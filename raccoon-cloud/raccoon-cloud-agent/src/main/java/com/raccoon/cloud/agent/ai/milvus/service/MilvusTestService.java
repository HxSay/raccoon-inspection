package com.raccoon.cloud.agent.ai.milvus.service;

import com.raccoon.cloud.agent.ai.milvus.config.MilvusTestEmbeddingConfiguration;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestDocumentVO;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestGetByIdRequest;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestInsertRequest;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestInsertResultVO;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestSearchRequest;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.response.DescCollResponseWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus + Spring AI VectorStore 调试服务：入库、相似检索、按 ID 查询。
 *
 * <p>不从 Spring 容器获取 VectorStore，而是用自有 EmbeddingModel + MilvusServiceClient
 * 自建独立的 {@link MilvusVectorStore}，完全隔离 Nacos / 业务默认 VectorStore（如 vector_store 1536 维）。</p>
 */
@Slf4j
@Service
public class MilvusTestService {

    private static final String META_DEVICE_TYPE = "deviceType";
    private static final String META_FAULT_TYPE = "faultType";
    private static final String META_SOURCE = "source";
    private static final String META_DOC_ID = "docId";

    private final MilvusServiceClient milvusServiceClient;
    private final EmbeddingModel embeddingModel;
    private final String databaseName;
    private final String collectionName;

    private volatile MilvusVectorStore vectorStore;

    public MilvusTestService(
            MilvusServiceClient milvusServiceClient,
            @Qualifier(MilvusTestEmbeddingConfiguration.BEAN_NAME) EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName,
            @Value("${raccoon.milvus-test.collection-name:inspection_rag_store}") String collectionName) {
        this.milvusServiceClient = milvusServiceClient;
        this.embeddingModel = embeddingModel;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    @PostConstruct
    public void init() {
        int dimension = embeddingModel.dimensions();
        if (dimension <= 0) {
            dimension = 768;
        }
        log.info("Milvus 测试初始化: database={}, collection={}, embeddingDimension={}",
                databaseName, collectionName, dimension);

        ensureCollectionCompatible(dimension);

        this.vectorStore = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .embeddingDimension(dimension)
                .initializeSchema(true)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .build();
        try {
            this.vectorStore.afterPropertiesSet();
        } catch (Exception e) {
            log.error("Milvus 测试 VectorStore 初始化失败", e);
            throw new IllegalStateException("Milvus 测试 VectorStore 初始化失败: " + e.getMessage(), e);
        }
    }

    /** 检查集合维度是否与当前 EmbeddingModel 匹配，不匹配则删除以便重建 */
    private void ensureCollectionCompatible(int expectedDimension) {
        try {
            boolean exists = Boolean.TRUE.equals(milvusServiceClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withDatabaseName(databaseName)
                            .withCollectionName(collectionName)
                            .build()).getData());
            if (!exists) {
                return;
            }
            var resp = milvusServiceClient.describeCollection(DescribeCollectionParam.newBuilder()
                    .withDatabaseName(databaseName)
                    .withCollectionName(collectionName)
                    .build());
            if (resp.getData() == null) {
                return;
            }
            DescCollResponseWrapper wrapper = new DescCollResponseWrapper(resp.getData());
            boolean compatible = wrapper.getFields().stream().anyMatch(f -> {
                if (f.getDataType() != DataType.FloatVector) {
                    return false;
                }
                int actual = f.getDimension();
                return actual == expectedDimension;
            });
            if (!compatible) {
                log.warn("Milvus 测试：集合 {} 与维度 {} 不兼容，删除后重建", collectionName, expectedDimension);
                milvusServiceClient.dropCollection(DropCollectionParam.newBuilder()
                        .withDatabaseName(databaseName)
                        .withCollectionName(collectionName)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Milvus 测试：集合兼容性检查失败（忽略，由 VectorStore.initializeSchema 接管）: {}", e.getMessage());
        }
    }

    private VectorStore requireVectorStore() {
        if (vectorStore == null) {
            throw new IllegalStateException("Milvus 测试 VectorStore 未初始化，请检查 agent 启动日志");
        }
        return vectorStore;
    }

    /** 构建 Spring AI Document 并写入 Milvus。 */
    public MilvusTestInsertResultVO insert(MilvusTestInsertRequest request) {
        VectorStore store = requireVectorStore();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(META_DOC_ID, request.getDocId());
        metadata.put(META_DEVICE_TYPE, request.getDeviceType());
        metadata.put(META_FAULT_TYPE, request.getFaultType());
        metadata.put(META_SOURCE, StringUtils.hasText(request.getSource()) ? request.getSource() : "测试录入");

        Document document = Document.builder()
                .id(request.getDocId())
                .text(request.getContent())
                .metadata(metadata)
                .build();

        try {
            store.add(List.of(document));
        } catch (Exception e) {
            log.error("Milvus test insert failed, docId={}", request.getDocId(), e);
            String root = rootCauseMessage(e);
            if (root.contains("schema does not contain vector field")) {
                throw new IllegalStateException(
                        "Milvus 集合缺少向量字段，请重启 agent 服务以自动重建集合 " + collectionName, e);
            }
            if (root.contains("dimension")) {
                throw new IllegalStateException("向量维度不匹配: " + root, e);
            }
            throw new IllegalStateException("入库失败: " + root, e);
        }
        log.info("Milvus test insert ok, docId={}", request.getDocId());

        return MilvusTestInsertResultVO.builder()
                .docId(request.getDocId())
                .message("入库成功")
                .metadata(metadata)
                .build();
    }

    /** 向量相似度检索。 */
    public List<MilvusTestDocumentVO> search(MilvusTestSearchRequest request) {
        VectorStore store = requireVectorStore();
        int topK = request.getTopK() != null ? request.getTopK() : 3;

        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getQuery())
                .topK(topK)
                .similarityThreshold(0.0)
                .build();

        List<Document> documents = store.similaritySearch(searchRequest);
        List<MilvusTestDocumentVO> result = new ArrayList<>();
        for (Document doc : documents) {
            result.add(toVo(doc, resolveScore(doc)));
        }
        return result;
    }

    /** 按文档 ID 查询，用于验证是否入库成功 */
    public MilvusTestDocumentVO getById(MilvusTestGetByIdRequest request) {
        VectorStore store = requireVectorStore();
        String docId = escapeFilterValue(request.getDocId());

        SearchRequest byMeta = SearchRequest.builder()
                .query("故障知识检索")
                .topK(1)
                .similarityThreshold(0.0)
                .filterExpression(META_DOC_ID + " == '" + docId + "'")
                .build();

        List<Document> hits = store.similaritySearch(byMeta);
        if (hits.isEmpty()) {
            SearchRequest byId = SearchRequest.builder()
                    .query("故障知识检索")
                    .topK(1)
                    .similarityThreshold(0.0)
                    .filterExpression("id == '" + docId + "'")
                    .build();
            hits = store.similaritySearch(byId);
        }

        if (hits.isEmpty()) {
            throw new IllegalArgumentException("未找到文档 ID: " + request.getDocId());
        }

        return toVo(hits.get(0), null);
    }

    private MilvusTestDocumentVO toVo(Document doc, Double score) {
        Map<String, Object> metadata = doc.getMetadata() != null
                ? new LinkedHashMap<>(doc.getMetadata())
                : new LinkedHashMap<>();

        String docId = doc.getId();
        if (!StringUtils.hasText(docId)) {
            Object metaId = metadata.get(META_DOC_ID);
            docId = metaId != null ? metaId.toString() : null;
        }

        return MilvusTestDocumentVO.builder()
                .docId(docId)
                .content(doc.getText())
                .metadata(metadata)
                .score(score)
                .build();
    }

    /** 解析相似度得分：优先 Document.getScore()，其次由 distance 推算 */
    private Double resolveScore(Document doc) {
        Double score = doc.getScore();
        if (score != null) {
            return score;
        }
        Object distance = doc.getMetadata() != null ? doc.getMetadata().get("distance") : null;
        if (distance instanceof Number num) {
            double d = num.doubleValue();
            return d <= 1.0 ? 1.0 - d : 1.0 / (1.0 + d);
        }
        return null;
    }

    private static String escapeFilterValue(String raw) {
        return raw.replace("'", "\\'");
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root.getMessage() != null && !root.getMessage().isBlank()) {
            return root.getMessage().trim();
        }
        return root.getClass().getSimpleName();
    }
}
