package com.raccoon.cloud.agent.ai.rag.service;

import com.raccoon.cloud.agent.ai.rag.config.RagChatClientConfiguration;
import com.raccoon.cloud.agent.ai.rag.config.RagVectorStoreConfiguration;
import com.raccoon.cloud.agent.ai.rag.dto.RagChatRequest;
import com.raccoon.cloud.agent.ai.rag.dto.RagChatResponse;
import com.raccoon.cloud.agent.ai.rag.dto.RagDeviceContextVO;
import com.raccoon.cloud.agent.ai.rag.dto.RagReferenceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 混合检索 RAG 服务：
 * <ol>
 *   <li>并行 Milvus 语义检索（TopK=5） + Neo4j 图查询（设备-故障-文档、上下游拓扑）</li>
 *   <li>融合上下文 → 专家 prompt → LLM 生成回答</li>
 * </ol>
 */
@Slf4j
@Service
public class HybridRagService {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "rag-hybrid-" + r.hashCode());
        t.setDaemon(true);
        return t;
    });

    private final RagVectorStoreConfiguration ragVectorStore;
    private final RagGraphService ragGraphService;
    private final ChatClient ragChatClient;
    private final String defaultModel;

    public HybridRagService(
            RagVectorStoreConfiguration ragVectorStore,
            RagGraphService ragGraphService,
            @Qualifier(RagChatClientConfiguration.BEAN_NAME) ChatClient ragChatClient,
            @Value("${spring.ai.ollama.chat.options.model:qwen2:7b}") String defaultModel) {
        this.ragVectorStore = ragVectorStore;
        this.ragGraphService = ragGraphService;
        this.ragChatClient = ragChatClient;
        this.defaultModel = defaultModel;
    }

    public RagChatResponse chat(RagChatRequest request) {
        String question = request.getQuestion().trim();
        String deviceId = StringUtils.hasText(request.getDeviceId()) ? request.getDeviceId().trim() : null;
        int topK = request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : 5;

        long start = System.currentTimeMillis();

        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(
                () -> safeVectorSearch(question, topK, deviceId), EXECUTOR);
        CompletableFuture<RagDeviceContextVO> graphFuture = CompletableFuture.supplyAsync(
                () -> safeGraphQuery(deviceId), EXECUTOR);

        List<Document> vectorResults;
        RagDeviceContextVO deviceContext;
        try {
            CompletableFuture.allOf(vectorFuture, graphFuture).join();
            vectorResults = vectorFuture.get();
            deviceContext = graphFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("混合检索失败: " + e.getMessage(), e);
        }

        List<RagReferenceVO> references = new ArrayList<>();
        references.addAll(buildVectorReferences(vectorResults));
        references.addAll(ragGraphService.buildGraphReferences(deviceContext));

        String prompt = buildPrompt(question, deviceId, references);
        String answer = callLlm(prompt);

        long elapsed = System.currentTimeMillis() - start;
        log.info("RAG chat 完成: deviceId={}, vectorHits={}, graphHits={}, elapsedMs={}",
                deviceId, vectorResults.size(),
                deviceContext == null ? 0
                        : (deviceContext.getFaults() == null ? 0 : deviceContext.getFaults().size()),
                elapsed);

        return RagChatResponse.builder()
                .answer(answer)
                .references(references)
                .deviceContext(deviceContext)
                .elapsedMs(elapsed)
                .model(defaultModel)
                .build();
    }

    private List<Document> safeVectorSearch(String question, int topK, String deviceId) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .similarityThreshold(0.0);
            if (StringUtils.hasText(deviceId)) {
                builder.filterExpression("device_id == '" + deviceId.replace("'", "\\'") + "'");
            }
            List<Document> docs = ragVectorStore.getVectorStore().similaritySearch(builder.build());
            if ((docs == null || docs.isEmpty()) && StringUtils.hasText(deviceId)) {
                // 设备过滤无结果时，回退到全局检索，避免完全无上下文
                docs = ragVectorStore.getVectorStore().similaritySearch(SearchRequest.builder()
                        .query(question).topK(topK).similarityThreshold(0.0).build());
            }
            return docs == null ? List.of() : docs;
        } catch (Exception e) {
            log.warn("Milvus 检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private RagDeviceContextVO safeGraphQuery(String deviceId) {
        try {
            return ragGraphService.queryDeviceContext(deviceId);
        } catch (Exception e) {
            log.warn("Neo4j 图查询失败: {}", e.getMessage());
            return null;
        }
    }

    private List<RagReferenceVO> buildVectorReferences(List<Document> docs) {
        List<RagReferenceVO> refs = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
            refs.add(RagReferenceVO.builder()
                    .source("vector")
                    .docId(strMeta(meta, "doc_id"))
                    .fileName(strMeta(meta, "file_name"))
                    .chunkIndex(intMeta(meta, "chunk_index"))
                    .score(doc.getScore())
                    .content(doc.getText())
                    .minioUrl(strMeta(meta, "minio_url"))
                    .deviceId(strMeta(meta, "device_id"))
                    .build());
        }
        return refs;
    }

    private String buildPrompt(String question, String deviceId, List<RagReferenceVO> references) {
        StringBuilder context = new StringBuilder();
        boolean hasVector = references.stream().anyMatch(r -> "vector".equals(r.getSource()));
        boolean hasGraph = references.stream().anyMatch(r -> "graph".equals(r.getSource()));

        if (hasVector) {
            context.append("### 相关文档片段：\n");
            int idx = 1;
            for (RagReferenceVO r : references) {
                if (!"vector".equals(r.getSource())) continue;
                context.append("[片段").append(idx++).append("] (来源: ")
                        .append(r.getFileName() != null ? r.getFileName() : "未知文档").append(")\n");
                context.append(r.getContent() == null ? "" : r.getContent()).append("\n\n");
            }
        }
        if (hasGraph) {
            context.append("### 设备关联信息：\n");
            for (RagReferenceVO r : references) {
                if (!"graph".equals(r.getSource())) continue;
                context.append("- ").append(r.getContent()).append("\n");
            }
            context.append("\n");
        }
        if (context.length() == 0) {
            context.append("（暂未检索到相关上下文）\n");
        }

        String deviceLine = StringUtils.hasText(deviceId)
                ? "当前关注设备：" + deviceId + "\n"
                : "";

        return """
                你是一名资深的电力巡检智能助手，擅长结合规程手册与设备故障图谱给出可执行的处置建议。
                请严格依据下方上下文回答用户的问题；若上下文不足以回答，请明确说明无法从上下文中得到答案，不要编造。

                %s上下文信息：
                %s
                用户问题：%s

                输出要求：
                1. 直接给出结构化的回答，包含「分析」「建议步骤」「相关参考」三段；
                2. 「相关参考」需要引用上文出现过的文档名或设备名；
                3. 若问题与上下文无关，请提示用户补充信息或更换关键词。
                """.formatted(deviceLine, context, question);
    }

    private String callLlm(String prompt) {
        try {
            String answer = ragChatClient.prompt().user(prompt).call().content();
            return answer != null ? answer : "";
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            throw new IllegalStateException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    private static String strMeta(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        return v == null ? null : v.toString();
    }

    private static Integer intMeta(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
