package com.raccoon.cloud.agent.ai.rag.web;

import com.raccoon.cloud.agent.ai.rag.dto.MilvusChunkVO;
import com.raccoon.cloud.agent.ai.rag.dto.MilvusCollectionStatsVO;
import com.raccoon.cloud.agent.ai.rag.dto.Neo4jNodeVO;
import com.raccoon.cloud.agent.ai.rag.dto.Neo4jOverviewVO;
import com.raccoon.cloud.agent.ai.rag.dto.Neo4jRelationshipVO;
import com.raccoon.cloud.agent.ai.rag.service.RagStorageService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAG 存储查看接口：Neo4j 节点 / 关系，Milvus 集合 / chunk。
 */
@RestController
@RequestMapping("/api/rag/storage")
@RequiredArgsConstructor
@Validated
public class RagStorageController {

    private final RagStorageService ragStorageService;

    /** Neo4j 总览：节点 / 关系总数、按 label / type 分组统计 */
    @GetMapping("/neo4j/overview")
    public HxResult<Neo4jOverviewVO> neo4jOverview() {
        return HxResult.success(ragStorageService.neo4jOverview());
    }

    /** 浏览指定 label 的节点 */
    @GetMapping("/neo4j/nodes")
    public HxResult<List<Neo4jNodeVO>> neo4jNodes(@RequestParam("label") @NotBlank String label,
                                                  @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return HxResult.success(ragStorageService.listNodes(label, limit));
    }

    /** 浏览指定 type 的关系 */
    @GetMapping("/neo4j/relationships")
    public HxResult<List<Neo4jRelationshipVO>> neo4jRelationships(@RequestParam("type") @NotBlank String type,
                                                                  @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return HxResult.success(ragStorageService.listRelationships(type, limit));
    }

    /** Neo4j 白名单：可供前端拉取下拉项 */
    @GetMapping("/neo4j/schema")
    public HxResult<Map<String, Object>> neo4jSchema() {
        return HxResult.success(Map.of(
                "labels", ragStorageService.allowedLabels(),
                "relationshipTypes", ragStorageService.allowedRelationshipTypes()
        ));
    }

    /** Milvus 集合统计：行数 / 字段 / 索引 */
    @GetMapping("/milvus/overview")
    public HxResult<MilvusCollectionStatsVO> milvusOverview() {
        return HxResult.success(ragStorageService.milvusStats());
    }

    /** Milvus chunk 浏览：可按 docId 过滤 */
    @GetMapping("/milvus/chunks")
    public HxResult<List<MilvusChunkVO>> milvusChunks(@RequestParam(value = "docId", required = false) String docId,
                                                      @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return HxResult.success(ragStorageService.listChunks(docId, limit));
    }

    /** 删除 Neo4j 节点（RAG Document 会联动 Milvus + MinIO） */
    @DeleteMapping("/neo4j/nodes")
    public HxResult<Void> deleteNeo4jNode(@RequestParam("internalId") long internalId,
                                          @RequestParam("label") @NotBlank String label) {
        ragStorageService.deleteNeo4jNode(internalId, label);
        return HxResult.success();
    }

    /** 删除 Neo4j 关系（仅删边，不删节点） */
    @DeleteMapping("/neo4j/relationships")
    public HxResult<Void> deleteNeo4jRelationship(@RequestParam("relInternalId") long relInternalId,
                                                   @RequestParam("type") @NotBlank String type) {
        ragStorageService.deleteNeo4jRelationship(relInternalId, type);
        return HxResult.success();
    }

    /** 删除 Milvus 单条 chunk（按主键 doc_id） */
    @DeleteMapping("/milvus/chunks")
    public HxResult<Void> deleteMilvusChunk(@RequestParam("docPk") @NotBlank String docPk) {
        ragStorageService.deleteMilvusChunk(docPk);
        return HxResult.success();
    }
}
