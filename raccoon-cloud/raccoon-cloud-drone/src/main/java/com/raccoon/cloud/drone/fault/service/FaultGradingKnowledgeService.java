package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.dispatch.knowledge.AgentKnowledgeClient;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DeviceFaultDocRelation;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DocumentChunk;
import com.raccoon.cloud.drone.dispatch.model.knowledge.TopologyNode;
import com.raccoon.cloud.drone.fault.dto.FaultGradingContext;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合 RAG 定级上下文：Milvus 故障手册 + Neo4j 拓扑（复用 {@link AgentKnowledgeClient}）。
 */
@Service
@RequiredArgsConstructor
public class FaultGradingKnowledgeService {

    private final AgentKnowledgeClient agentKnowledgeClient;

    public FaultGradingContext buildContext(FaultEvent event) {
        FaultGradingContext ctx = new FaultGradingContext();
        if (event == null) {
            return ctx;
        }
        String query = (event.getFaultType() != null ? event.getFaultType() : "")
                + " 故障处置 "
                + (event.getDescription() != null ? event.getDescription() : "");
        List<DocumentChunk> chunks = agentKnowledgeClient.searchRegulations(query, 5);
        ctx.setRegulationChunks(chunks);

        List<Long> deviceIds = event.getDeviceId() != null ? List.of(event.getDeviceId()) : List.of();
        List<DeviceFaultDocRelation> relations = agentKnowledgeClient.queryFaultRelations(deviceIds);
        ctx.setFaultRelations(relations);
        List<TopologyNode> topology = agentKnowledgeClient.queryTopology(deviceIds);
        ctx.setTopologyNodes(topology);

        Map<String, Object> summary = new HashMap<>();
        summary.put("deviceId", event.getDeviceId());
        summary.put("downstreamCount", topology.size());
        summary.put("openFaultCount", relations.size());
        ctx.setGraphSummary(summary);

        ctx.setCriticalLevelScore(scoreCritical(topology, relations));
        ctx.setDownstreamImpactScore(Math.min(1.0, topology.size() / 10.0));
        ctx.setOpenFaultScore(Math.min(1.0, relations.size() / 5.0));
        ctx.setRegulationUrgencyScore(scoreRegulationUrgency(chunks));
        return ctx;
    }

    private double scoreCritical(List<TopologyNode> topology, List<DeviceFaultDocRelation> relations) {
        double max = 0.3;
        for (TopologyNode n : topology) {
            if (n.getCriticalLevel() != null) {
                max = Math.max(max, Math.min(1.0, n.getCriticalLevel() / 5.0));
            }
        }
        for (DeviceFaultDocRelation r : relations) {
            if (r.getSeverity() != null) {
                max = Math.max(max, Math.min(1.0, r.getSeverity() / 5.0));
            }
        }
        return max;
    }

    private double scoreRegulationUrgency(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0.2;
        }
        double score = 0.2;
        for (DocumentChunk c : chunks) {
            String text = c.getExcerpt();
            if (text == null) {
                continue;
            }
            if (text.contains("立即") || text.contains("紧急") || text.contains("停机")) {
                score = Math.max(score, 0.9);
            } else if (text.contains("严重") || text.contains("尽快")) {
                score = Math.max(score, 0.65);
            }
        }
        return score;
    }
}
