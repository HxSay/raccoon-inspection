package com.raccoon.cloud.drone.dispatch.agent;

import com.raccoon.cloud.drone.dispatch.knowledge.AgentKnowledgeClient;
import com.raccoon.cloud.drone.dispatch.model.TerminalCapacity;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DeviceFaultDocRelation;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DocumentChunk;
import com.raccoon.cloud.drone.dispatch.model.knowledge.TopologyNode;
import com.raccoon.cloud.drone.dispatch.service.TerminalStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 中央任务规划 Agent 的 Spring AI 工具集（步骤 3）。
 * <p>把「实时终端上下文查询、巡检知识语义检索、设备故障/拓扑图查询」封装为本地 LLM(Ollama)
 * 可在任务分配编排中自动调用的工具。返回紧凑文本，便于 LLM 消费与引用。
 *
 * @author raccoon
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPlanningAgentTools {

    private final TerminalStateService terminalStateService;
    private final AgentKnowledgeClient agentKnowledgeClient;

    @Tool(description = "查询指定场景地图下所有终端(无人机/机器狗等)的实时在线状态、电量、负载、传感器能力。")
    public String queryTerminalContext(
            @ToolParam(description = "场景地图ID") Long mapId) {
        if (mapId == null) {
            return "缺少 mapId，无法查询终端。";
        }
        List<TerminalState> pool;
        try {
            pool = terminalStateService.listByMap(mapId);
        } catch (Exception e) {
            return "查询终端失败：" + e.getMessage();
        }
        if (pool == null || pool.isEmpty()) {
            return "场景 " + mapId + " 下无可用终端。";
        }
        StringBuilder sb = new StringBuilder("场景 ").append(mapId).append(" 终端共 ")
                .append(pool.size()).append(" 台：\n");
        for (TerminalState t : pool) {
            sb.append("- ID=").append(t.getTerminalId())
                    .append(" 名称=").append(t.getTerminalName())
                    .append(" 类型=").append(t.getTerminalType())
                    .append(" 在线=").append(t.isOnline())
                    .append(" 电量=").append(t.getBatteryPct() == null ? "?" : Math.round(t.getBatteryPct()) + "%")
                    .append(" 负载CPU=").append(t.getCpuPct() == null ? "?" : Math.round(t.getCpuPct()) + "%")
                    .append(" 故障=").append(t.getFaultStatus())
                    .append(" 传感器=").append(sensors(t))
                    .append('\n');
        }
        return sb.toString();
    }

    @Tool(description = "从向量知识库语义检索与任务相关的巡检规程、作业标准与故障手册片段。")
    public String searchInspectionKnowledge(
            @ToolParam(description = "检索问题，例如：主变压器 热成像 复巡 规程") String query) {
        List<DocumentChunk> chunks = agentKnowledgeClient.searchRegulations(query, 5);
        if (chunks.isEmpty()) {
            return "未检索到相关规程片段。";
        }
        StringBuilder sb = new StringBuilder("检索到 ").append(chunks.size()).append(" 条规程片段：\n");
        int i = 1;
        for (DocumentChunk c : chunks) {
            sb.append("[").append(i++).append("] ").append(c.getFileName() == null ? "规程" : c.getFileName())
                    .append("(chunk=").append(c.getChunkId()).append(")：")
                    .append(c.getExcerpt() == null ? "" : c.getExcerpt()).append('\n');
        }
        return sb.toString();
    }

    @Tool(description = "从图数据库查询给定设备的故障关联与上下游拓扑影响范围。")
    public String queryDeviceKnowledgeGraph(
            @ToolParam(description = "设备ID列表，逗号分隔，例如：101,102") String deviceIds) {
        List<Long> ids = parseIds(deviceIds);
        if (ids.isEmpty()) {
            return "未提供有效设备ID。";
        }
        List<DeviceFaultDocRelation> faults = agentKnowledgeClient.queryFaultRelations(ids);
        List<TopologyNode> topo = agentKnowledgeClient.queryTopology(ids);
        if (faults.isEmpty() && topo.isEmpty()) {
            return "未查询到设备故障关联或拓扑关系。";
        }
        StringBuilder sb = new StringBuilder();
        if (!faults.isEmpty()) {
            sb.append("故障关联：\n");
            for (DeviceFaultDocRelation f : faults) {
                sb.append("- 设备").append(f.getDeviceId()).append("(").append(f.getDeviceName()).append(") ")
                        .append("故障=").append(f.getFaultName())
                        .append(" 严重度=").append(f.getSeverity())
                        .append(f.getDocName() == null ? "" : " 关联文档=" + f.getDocName())
                        .append('\n');
            }
        }
        if (!topo.isEmpty()) {
            sb.append("拓扑影响（下游设备）：\n");
            for (TopologyNode n : topo) {
                sb.append("- ").append(n.getSourceId()).append(" -> ").append(n.getTargetName())
                        .append(" 关键度=").append(n.getCriticalLevel()).append('\n');
            }
        }
        return sb.toString();
    }

    private String sensors(TerminalState t) {
        if (t.getCapacity() == null || t.getCapacity().getPerception() == null) {
            return "?";
        }
        TerminalCapacity.Perception p = t.getCapacity().getPerception();
        List<String> list = new ArrayList<>(p.getSensorTypes() == null ? List.of() : p.getSensorTypes());
        return String.join("/", list);
    }

    private List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null) {
            return ids;
        }
        for (String s : csv.split("[,，\\s]+")) {
            if (!s.isBlank()) {
                try {
                    ids.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }
        }
        return ids;
    }
}
