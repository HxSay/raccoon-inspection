package com.raccoon.cloud.agent.ai.rag.web;

import com.raccoon.cloud.agent.ai.rag.dto.IngestResultVO;
import com.raccoon.cloud.agent.ai.rag.dto.RagChatRequest;
import com.raccoon.cloud.agent.ai.rag.dto.RagChatResponse;
import com.raccoon.cloud.agent.ai.rag.dto.RagDeviceAttachRequest;
import com.raccoon.cloud.agent.ai.rag.dto.RagDeviceVO;
import com.raccoon.cloud.agent.ai.rag.dto.RagDocumentVO;
import com.raccoon.cloud.agent.ai.rag.service.DocumentIngestionService;
import com.raccoon.cloud.agent.ai.rag.service.HybridRagService;
import com.raccoon.cloud.agent.ai.rag.service.RagGraphService;
import com.raccoon.cloud.agent.service.MinioStorageService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * RAG 文档入库 & 混合检索 REST 接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Validated
public class RagController {

    private final DocumentIngestionService documentIngestionService;
    private final HybridRagService hybridRagService;
    private final RagGraphService ragGraphService;
    private final MinioStorageService minioStorageService;

    /** 文档上传入库：multipart 上传，含设备/类型元数据 */
    @PostMapping("/ingest")
    public HxResult<IngestResultVO> ingest(@RequestPart("file") MultipartFile file,
                                           @RequestParam(value = "deviceId", required = false) String deviceId,
                                           @RequestParam(value = "docType", required = false) String docType) {
        IngestResultVO vo = documentIngestionService.ingest(file, deviceId, docType);
        return HxResult.success("文档入库成功", vo);
    }

    /** 混合检索 RAG 问答 */
    @PostMapping("/chat")
    public HxResult<RagChatResponse> chat(@RequestBody @Valid RagChatRequest request) {
        return HxResult.success(hybridRagService.chat(request));
    }

    /** 文档列表：支持文件名关键字 / 设备 ID 筛选 */
    @GetMapping("/documents")
    public HxResult<List<RagDocumentVO>> listDocuments(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "deviceId", required = false) String deviceId) {
        return HxResult.success(ragGraphService.listDocuments(keyword, deviceId));
    }

    /** 按文档 ID 删除：Milvus 向量 + Neo4j 节点 + MinIO 对象一并清理 */
    @DeleteMapping("/documents/{docId}")
    public HxResult<Void> deleteDocument(@PathVariable("docId") @NotBlank String docId) {
        documentIngestionService.delete(docId);
        return HxResult.success();
    }

    /** 设备列表：来自 Neo4j Device 节点（含上传时自动创建的设备） */
    @GetMapping("/devices")
    public HxResult<List<RagDeviceVO>> listDevices() {
        return HxResult.success(ragGraphService.listDevices());
    }

    /** 给文档批量关联设备（追加，不影响已有关联） */
    @PostMapping("/documents/{docId}/devices")
    public HxResult<Void> attachDevices(@PathVariable("docId") @NotBlank String docId,
                                        @RequestBody @Valid RagDeviceAttachRequest request) {
        ragGraphService.attachDevices(docId, request.getDeviceIds());
        return HxResult.success();
    }

    /** 解除文档与单个设备的关联 */
    @DeleteMapping("/documents/{docId}/devices/{deviceId}")
    public HxResult<Void> detachDevice(@PathVariable("docId") @NotBlank String docId,
                                       @PathVariable("deviceId") @NotBlank String deviceId) {
        ragGraphService.detachDevice(docId, deviceId);
        return HxResult.success();
    }

    /** 清理脏数据：删除 docId/fileName/uploadTime 缺失的 Document 节点 */
    @PostMapping("/documents/cleanup")
    public HxResult<Map<String, Object>> cleanupOrphans() {
        long deleted = ragGraphService.cleanupOrphanDocuments();
        return HxResult.success("清理完成，共移除 " + deleted + " 条脏节点",
                Map.of("deleted", deleted));
    }

    /** PDF 预览：调用 MinioStorageService 重新生成预签名 URL */
    @GetMapping("/documents/{docId}/preview-url")
    public HxResult<Map<String, String>> previewUrl(@PathVariable("docId") @NotBlank String docId) {
        String objectKey = ragGraphService.findMinioObjectKey(docId);
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("未找到 docId=" + docId + " 对应的 MinIO 对象");
        }
        try {
            String url = minioStorageService.presign(objectKey);
            return HxResult.success(Map.of("docId", docId, "objectKey", objectKey, "url", url));
        } catch (Exception e) {
            throw new IllegalStateException("生成预签名 URL 失败: " + e.getMessage(), e);
        }
    }

}
