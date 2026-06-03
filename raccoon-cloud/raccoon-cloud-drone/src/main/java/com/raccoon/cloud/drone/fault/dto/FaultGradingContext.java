package com.raccoon.cloud.drone.fault.dto;

import com.raccoon.cloud.drone.dispatch.model.knowledge.DeviceFaultDocRelation;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DocumentChunk;
import com.raccoon.cloud.drone.dispatch.model.knowledge.TopologyNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 混合 RAG 定级上下文。
 */
@Data
public class FaultGradingContext {

    private List<DocumentChunk> regulationChunks = new ArrayList<>();
    private List<DeviceFaultDocRelation> faultRelations = new ArrayList<>();
    private List<TopologyNode> topologyNodes = new ArrayList<>();
    private Map<String, Object> graphSummary;

    private double criticalLevelScore;
    private double downstreamImpactScore;
    private double openFaultScore;
    private double regulationUrgencyScore;
}
