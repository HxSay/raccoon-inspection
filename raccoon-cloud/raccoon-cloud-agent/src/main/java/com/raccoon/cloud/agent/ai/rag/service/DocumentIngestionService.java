package com.raccoon.cloud.agent.ai.rag.service;

import com.raccoon.cloud.agent.ai.rag.config.RagVectorStoreConfiguration;
import com.raccoon.cloud.agent.ai.rag.dto.IngestResultVO;
import com.raccoon.cloud.agent.dto.MinioUploadVO;
import com.raccoon.cloud.agent.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档 Ingestion 服务：
 * <pre>
 *   PDF 上传 → MinIO 存储并取预签名 URL
 *            → PDF 文本解析（预留 OCR）
 *            → 800 字符切块
 *            → 生成向量写入 Milvus（带 metadata: 文件名/MinIO URL/chunk 序号/设备 ID）
 *            → 写入 Neo4j Document 节点并 MERGE Device 关联
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final String META_DOC_ID = "doc_id";
    private static final String META_FILE_NAME = "file_name";
    private static final String META_MINIO_URL = "minio_url";
    private static final String META_MINIO_KEY = "minio_key";
    private static final String META_CHUNK_INDEX = "chunk_index";
    private static final String META_DEVICE_ID = "device_id";
    private static final String META_DOC_TYPE = "doc_type";

    private final MinioStorageService minioStorageService;
    private final RagVectorStoreConfiguration ragVectorStore;
    private final PdfTextExtractor pdfTextExtractor;
    private final TextChunker textChunker;
    private final RagGraphService ragGraphService;

    @Value("${raccoon.rag.minio-prefix:rag/}")
    private String minioPrefix;

    @Value("${raccoon.rag.chunk-size:800}")
    private int chunkSize;

    @Value("${raccoon.rag.chunk-overlap:80}")
    private int chunkOverlap;

    /**
     * 上传 PDF → 解析 → 切块 → 写入 Milvus + Neo4j。
     *
     * @param file     PDF 文件
     * @param deviceId 关联设备 ID（可空，空时不建立 Device-HAS_DOCUMENT-Document 关系）
     * @param docType  文档类型（规程 / 手册 / 图纸 等）
     */
    public IngestResultVO ingest(MultipartFile file, String deviceId, String docType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "unnamed.pdf";
        if (!isPdf(fileName, file.getContentType())) {
            throw new IllegalArgumentException("仅支持 PDF 文件，文件名: " + fileName);
        }

        long uploadTime = System.currentTimeMillis();
        String docId = UUID.randomUUID().toString().replace("-", "");
        log.info("RAG ingest start: file={}, deviceId={}, docType={}, docId={}",
                fileName, deviceId, docType, docId);

        MinioUploadVO uploadVO;
        try {
            uploadVO = minioStorageService.upload(file, minioPrefix);
        } catch (Exception e) {
            log.error("MinIO 上传失败: {}", fileName, e);
            throw new IllegalStateException("MinIO 上传失败: " + e.getMessage(), e);
        }

        String pdfText = pdfTextExtractor.extract(file);
        if (!StringUtils.hasText(pdfText)) {
            log.warn("PDF 解析为空文本，疑似扫描件: {}", fileName);
            throw new IllegalStateException(
                    "PDF 解析未提取到文本（疑似扫描件），OCR 通道暂未启用，请提供文本型 PDF");
        }

        List<String> chunks = textChunker.split(pdfText, chunkSize, chunkOverlap);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文本切块为空，无法入库");
        }
        log.info("RAG ingest 切块完成: {} 个 chunk", chunks.size());

        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(META_DOC_ID, docId);
            metadata.put(META_FILE_NAME, fileName);
            metadata.put(META_MINIO_URL, uploadVO.getPresignedUrl());
            metadata.put(META_MINIO_KEY, uploadVO.getObjectKey());
            metadata.put(META_CHUNK_INDEX, i);
            metadata.put(META_DOC_TYPE, docType != null ? docType : "未分类");
            if (StringUtils.hasText(deviceId)) {
                metadata.put(META_DEVICE_ID, deviceId);
            }
            // Spring AI MilvusVectorStore 默认主键 VARCHAR(36)，
            // 单独用一个 32 字符 UUID 作为 chunk 主键，再将 docId / chunkIndex 写入 metadata。
            String chunkId = UUID.randomUUID().toString().replace("-", "");
            Document doc = Document.builder()
                    .id(chunkId)
                    .text(chunks.get(i))
                    .metadata(metadata)
                    .build();
            documents.add(doc);
        }

        MilvusVectorStore vectorStore = ragVectorStore.getVectorStore();
        try {
            vectorStore.add(documents);
        } catch (Exception e) {
            log.error("Milvus 写入失败 docId={}", docId, e);
            safeRemoveMinio(uploadVO.getObjectKey());
            throw new IllegalStateException("Milvus 写入失败: " + rootCause(e), e);
        }

        try {
            ragGraphService.saveDocumentNode(docId, fileName,
                    docType, uploadVO.getObjectKey(), chunks.size(), deviceId, uploadTime);
        } catch (Exception e) {
            log.error("Neo4j 写入失败 docId={}，开始回滚 Milvus 与 MinIO", docId, e);
            safeRemoveVectorsByDocId(docId);
            safeRemoveMinio(uploadVO.getObjectKey());
            throw new IllegalStateException("Neo4j 写入失败: " + rootCause(e), e);
        }

        log.info("RAG ingest done: docId={}, file={}, chunks={}", docId, fileName, chunks.size());

        return IngestResultVO.builder()
                .docId(docId)
                .fileName(fileName)
                .uploadTime(uploadTime)
                .deviceId(deviceId)
                .docType(docType)
                .chunkCount(chunks.size())
                .minioObjectKey(uploadVO.getObjectKey())
                .minioUrl(uploadVO.getPresignedUrl())
                .build();
    }

    /** 同步删除 Milvus 向量 + Neo4j 节点 + MinIO 对象。 */
    public void delete(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw new IllegalArgumentException("docId 不能为空");
        }
        String minioKey = ragGraphService.findMinioObjectKey(docId);

        // 按 metadata.doc_id 过滤删除 Milvus 中所有 chunk
        try {
            Filter.Expression filter = new FilterExpressionBuilder().eq(META_DOC_ID, docId).build();
            ragVectorStore.getVectorStore().delete(filter);
        } catch (Exception e) {
            log.warn("Milvus 向量删除失败 docId={}: {}", docId, e.getMessage());
        }

        ragGraphService.deleteDocumentNode(docId);
        if (StringUtils.hasText(minioKey)) {
            safeRemoveMinio(minioKey);
        }
        log.info("RAG 删除完成 docId={}, minioKey={}", docId, minioKey);
    }

    private void safeRemoveMinio(String objectKey) {
        if (!StringUtils.hasText(objectKey)) return;
        try {
            minioStorageService.delete(objectKey);
        } catch (Exception ex) {
            log.warn("MinIO 删除失败 key={}: {}", objectKey, ex.getMessage());
        }
    }

    private void safeRemoveVectorsByDocId(String docId) {
        try {
            Filter.Expression filter = new FilterExpressionBuilder().eq(META_DOC_ID, docId).build();
            ragVectorStore.getVectorStore().delete(filter);
        } catch (Exception ex) {
            log.warn("Milvus 回滚删除失败 docId={}: {}", docId, ex.getMessage());
        }
    }

    private static boolean isPdf(String fileName, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("pdf")) {
            return true;
        }
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    private static String rootCause(Throwable e) {
        Throwable r = e;
        while (r.getCause() != null) {
            r = r.getCause();
        }
        return r.getMessage() != null ? r.getMessage() : r.getClass().getSimpleName();
    }
}
