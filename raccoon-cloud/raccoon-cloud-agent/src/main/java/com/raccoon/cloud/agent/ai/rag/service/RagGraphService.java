package com.raccoon.cloud.agent.ai.rag.service;

import com.raccoon.cloud.agent.ai.rag.dto.RagDeviceAttachRequest;
import com.raccoon.cloud.agent.ai.rag.dto.RagDeviceContextVO;
import com.raccoon.cloud.agent.ai.rag.dto.RagDeviceVO;
import com.raccoon.cloud.agent.ai.rag.dto.RagDocumentVO;
import com.raccoon.cloud.agent.ai.rag.dto.RagReferenceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 图谱服务：封装与 Document / Device / Fault 三类节点的读写操作。
 * 采用纯 Cypher（Neo4jClient），无需引入 OGM 实体，避免影响其它模块的扫描配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagGraphService {

    private final Neo4jClient neo4jClient;

    /** 保存 Document 节点；若 deviceId 非空则 MERGE Device 并建立 HAS_DOCUMENT 关系。 */
    public void saveDocumentNode(String docId,
                                 String fileName,
                                 String docType,
                                 String minioObjectKey,
                                 int chunkCount,
                                 String deviceId,
                                 long uploadTime) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("docId", docId);
        params.put("fileName", fileName);
        params.put("docType", docType != null ? docType : "未分类");
        params.put("minioPath", minioObjectKey);
        params.put("chunkCount", chunkCount);
        params.put("uploadTime", uploadTime);

        neo4jClient.query("""
                MERGE (d:Document {docId: $docId})
                SET d.fileName = $fileName,
                    d.type = $docType,
                    d.minioPath = $minioPath,
                    d.chunkCount = $chunkCount,
                    d.uploadTime = $uploadTime
                RETURN d.docId AS docId
                """)
                .bindAll(params)
                .run();

        if (StringUtils.hasText(deviceId)) {
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("deviceId", deviceId);
            rel.put("docId", docId);
            neo4jClient.query("""
                    MERGE (dev:Device {deviceId: $deviceId})
                      ON CREATE SET dev.name = $deviceId, dev.status = '运行中'
                    WITH dev
                    MATCH (doc:Document {docId: $docId})
                    MERGE (dev)-[:HAS_DOCUMENT]->(doc)
                    """)
                    .bindAll(rel)
                    .run();
        }
    }

    /** 删除 Document 节点及其与设备的关系。 */
    public void deleteDocumentNode(String docId) {
        neo4jClient.query("""
                MATCH (d:Document {docId: $docId})
                DETACH DELETE d
                """)
                .bind(docId).to("docId")
                .run();
    }

    /**
     * 清理脏数据：删除 docId 为空 / fileName 缺失 / 上传时间缺失的 Document 节点。
     * 仅清理 Neo4j 节点，不动 Milvus / MinIO（因为这些孤儿节点本就无对应文件）。
     *
     * @return 被删除的节点数
     */
    public long cleanupOrphanDocuments() {
        Long deleted = neo4jClient.query("""
                MATCH (d:Document)
                WHERE d.docId IS NULL OR d.docId = ''
                   OR d.fileName IS NULL OR d.fileName = ''
                   OR d.uploadTime IS NULL
                WITH collect(d) AS docs, count(d) AS deleted
                FOREACH (x IN docs | DETACH DELETE x)
                RETURN deleted
                """)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("deleted").asLong(0L))
                .one()
                .orElse(0L);
        return deleted == null ? 0L : deleted;
    }

    /** 列出全部 Document，按上传时间倒序。每个 Document 聚合所有关联设备。 */
    public List<RagDocumentVO> listDocuments(String keyword, String deviceId) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (d:Document)
                WHERE d.docId IS NOT NULL AND d.docId <> ''
                """);
        if (StringUtils.hasText(keyword)) {
            cypher.append("  AND toLower(coalesce(d.fileName, '')) CONTAINS toLower($keyword)\n");
        }
        // 用 deviceId 过滤时，先确保文档与该设备相关联
        if (StringUtils.hasText(deviceId)) {
            cypher.append("""
                    WITH d
                    MATCH (filterDev:Device {deviceId: $deviceId})-[:HAS_DOCUMENT]->(d)
                    WITH d
                    """);
        }
        // 聚合该 Document 的全部关联设备
        cypher.append("""
                OPTIONAL MATCH (dev:Device)-[:HAS_DOCUMENT]->(d)
                WITH d,
                     collect(CASE WHEN dev IS NULL THEN null ELSE {
                         deviceId: dev.deviceId,
                         name: coalesce(dev.name, dev.deviceId),
                         type: dev.type,
                         station: dev.station
                     } END) AS devices
                RETURN d.docId AS docId,
                       d.fileName AS fileName,
                       d.type AS docType,
                       d.minioPath AS minioPath,
                       d.uploadTime AS uploadTime,
                       d.chunkCount AS chunkCount,
                       devices
                ORDER BY uploadTime DESC
                """);

        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.hasText(keyword)) {
            params.put("keyword", keyword.trim());
        }
        if (StringUtils.hasText(deviceId)) {
            params.put("deviceId", deviceId.trim());
        }

        var rows = neo4jClient.query(cypher.toString()).bindAll(params).fetch().all();
        List<RagDocumentVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(RagDocumentVO.builder()
                    .docId(asString(row.get("docId")))
                    .fileName(asString(row.get("fileName")))
                    .docType(asString(row.get("docType")))
                    .minioObjectKey(asString(row.get("minioPath")))
                    .uploadTime(asLong(row.get("uploadTime")))
                    .chunkCount(asInteger(row.get("chunkCount")))
                    .devices(toDeviceList(row.get("devices")))
                    .build());
        }
        return result;
    }

    /**
     * 给 docId 文档批量关联设备：
     * - 已存在的 Device 节点：补全空字段（保留已有非空值）
     * - 不存在的 Device 节点：按传入的主数据 MERGE 创建
     * - HAS_DOCUMENT 关系若已存在则不重复
     */
    public void attachDevices(String docId, List<RagDeviceAttachRequest.DeviceInput> devices) {
        if (!StringUtils.hasText(docId) || devices == null || devices.isEmpty()) {
            return;
        }
        // 去重 + 过滤空 deviceId
        Map<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        for (RagDeviceAttachRequest.DeviceInput dv : devices) {
            if (dv == null || !StringUtils.hasText(dv.getDeviceId())) continue;
            String id = dv.getDeviceId().trim();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", id);
            row.put("name", StringUtils.hasText(dv.getName()) ? dv.getName().trim() : null);
            row.put("type", StringUtils.hasText(dv.getType()) ? dv.getType().trim() : null);
            row.put("station", StringUtils.hasText(dv.getStation()) ? dv.getStation().trim() : null);
            dedup.put(id, row);
        }
        if (dedup.isEmpty()) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("docId", docId);
        params.put("devices", new ArrayList<>(dedup.values()));
        neo4jClient.query("""
                MATCH (d:Document {docId: $docId})
                UNWIND $devices AS dv
                MERGE (dev:Device {deviceId: dv.deviceId})
                  ON CREATE SET dev.name = coalesce(dv.name, dv.deviceId),
                                dev.type = dv.type,
                                dev.station = dv.station,
                                dev.status = '运行中'
                  ON MATCH SET  dev.name = coalesce(dv.name, dev.name),
                                dev.type = coalesce(dv.type, dev.type),
                                dev.station = coalesce(dv.station, dev.station)
                MERGE (dev)-[:HAS_DOCUMENT]->(d)
                """)
                .bindAll(params)
                .run();
    }

    /** 解除单个设备的文档关联。 */
    public void detachDevice(String docId, String deviceId) {
        if (!StringUtils.hasText(docId) || !StringUtils.hasText(deviceId)) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("docId", docId);
        params.put("deviceId", deviceId);
        neo4jClient.query("""
                MATCH (dev:Device {deviceId: $deviceId})-[r:HAS_DOCUMENT]->(d:Document {docId: $docId})
                DELETE r
                """)
                .bindAll(params)
                .run();
    }

    @SuppressWarnings("unchecked")
    private List<RagDeviceVO> toDeviceList(Object v) {
        List<RagDeviceVO> result = new ArrayList<>();
        if (!(v instanceof Iterable<?> it)) {
            return result;
        }
        for (Object o : it) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String deviceId = asString(m.get("deviceId"));
            if (!StringUtils.hasText(deviceId)) continue;
            result.add(RagDeviceVO.builder()
                    .deviceId(deviceId)
                    .name(asString(m.get("name")))
                    .type(asString(m.get("type")))
                    .station(asString(m.get("station")))
                    .build());
        }
        return result;
    }

    /** 设备列表：取自 Neo4j Device 节点，按 deviceId 升序。 */
    public List<RagDeviceVO> listDevices() {
        var rows = neo4jClient.query("""
                MATCH (d:Device)
                RETURN d.deviceId AS deviceId,
                       coalesce(d.name, d.deviceId) AS name,
                       d.type AS type,
                       d.station AS station
                ORDER BY deviceId
                """).fetch().all();
        List<RagDeviceVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String deviceId = asString(row.get("deviceId"));
            if (!StringUtils.hasText(deviceId)) {
                continue;
            }
            list.add(RagDeviceVO.builder()
                    .deviceId(deviceId)
                    .name(asString(row.get("name")))
                    .type(asString(row.get("type")))
                    .station(asString(row.get("station")))
                    .build());
        }
        return list;
    }

    /** 取单个 Document 节点的 MinIO 路径，用于删除时同步清理对象存储。 */
    public String findMinioObjectKey(String docId) {
        return neo4jClient.query("""
                MATCH (d:Document {docId: $docId})
                RETURN d.minioPath AS minioPath
                """)
                .bind(docId).to("docId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("minioPath").asString(null))
                .one()
                .orElse(null);
    }

    /** 查询某设备的故障、上下游设备拓扑（1-2 层）。 */
    public RagDeviceContextVO queryDeviceContext(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        var deviceRow = neo4jClient.query("""
                MATCH (d:Device {deviceId: $deviceId})
                RETURN d.deviceId AS deviceId,
                       coalesce(d.name, d.deviceId) AS name,
                       d.type AS type,
                       d.station AS station
                """)
                .bind(deviceId).to("deviceId")
                .fetch().one().orElse(null);
        if (deviceRow == null) {
            return null;
        }
        RagDeviceVO device = RagDeviceVO.builder()
                .deviceId(asString(deviceRow.get("deviceId")))
                .name(asString(deviceRow.get("name")))
                .type(asString(deviceRow.get("type")))
                .station(asString(deviceRow.get("station")))
                .build();

        var faultRows = neo4jClient.query("""
                MATCH (d:Device {deviceId: $deviceId})-[:HAS_FAULT]->(f:Fault)
                RETURN f.faultCode AS faultCode,
                       coalesce(f.name, f.faultCode) AS name,
                       f.description AS description,
                       f.level AS level
                ORDER BY f.level DESC, f.faultCode
                """)
                .bind(deviceId).to("deviceId")
                .fetch().all();
        List<RagDeviceContextVO.FaultInfo> faults = new ArrayList<>();
        for (Map<String, Object> r : faultRows) {
            faults.add(RagDeviceContextVO.FaultInfo.builder()
                    .faultCode(asString(r.get("faultCode")))
                    .name(asString(r.get("name")))
                    .description(asString(r.get("description")))
                    .level(asInteger(r.get("level")))
                    .build());
        }

        var topology = neo4jClient.query("""
                MATCH (d:Device {deviceId: $deviceId})
                OPTIONAL MATCH (d)-[:CONNECTS_TO*1..2]->(downstream:Device)
                OPTIONAL MATCH (upstream:Device)-[:CONNECTS_TO*1..2]->(d)
                RETURN collect(DISTINCT coalesce(downstream.name, downstream.deviceId)) AS down,
                       collect(DISTINCT coalesce(upstream.name, upstream.deviceId)) AS up
                """)
                .bind(deviceId).to("deviceId")
                .fetch().one().orElse(Map.of());

        return RagDeviceContextVO.builder()
                .device(device)
                .faults(faults)
                .downstreamDevices(toStringList(topology.get("down")))
                .upstreamDevices(toStringList(topology.get("up")))
                .build();
    }

    /** 把设备相关故障/拓扑信息转成检索引用列表，喂给 LLM。 */
    public List<RagReferenceVO> buildGraphReferences(RagDeviceContextVO ctx) {
        List<RagReferenceVO> refs = new ArrayList<>();
        if (ctx == null) {
            return refs;
        }
        String deviceName = ctx.getDevice() != null ? ctx.getDevice().getName() : null;
        if (ctx.getFaults() != null) {
            for (RagDeviceContextVO.FaultInfo f : ctx.getFaults()) {
                String text = String.format("设备[%s]可能发生故障[%s]：%s（等级 %s）",
                        deviceName, f.getName(), f.getDescription(), f.getLevel());
                refs.add(RagReferenceVO.builder()
                        .source("graph")
                        .content(text)
                        .deviceId(ctx.getDevice() != null ? ctx.getDevice().getDeviceId() : null)
                        .build());
            }
        }
        if (ctx.getDownstreamDevices() != null && !ctx.getDownstreamDevices().isEmpty()) {
            refs.add(RagReferenceVO.builder()
                    .source("graph")
                    .content("下游影响设备：" + String.join("、", ctx.getDownstreamDevices()))
                    .deviceId(ctx.getDevice() != null ? ctx.getDevice().getDeviceId() : null)
                    .build());
        }
        if (ctx.getUpstreamDevices() != null && !ctx.getUpstreamDevices().isEmpty()) {
            refs.add(RagReferenceVO.builder()
                    .source("graph")
                    .content("上游关联设备：" + String.join("、", ctx.getUpstreamDevices()))
                    .deviceId(ctx.getDevice() != null ? ctx.getDevice().getDeviceId() : null)
                    .build());
        }
        return refs;
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer asInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object v) {
        List<String> result = new ArrayList<>();
        if (v instanceof Iterable<?> it) {
            for (Object o : it) {
                if (o == null) continue;
                String s = o.toString();
                if (!s.isBlank()) result.add(s);
            }
        }
        return result;
    }
}
