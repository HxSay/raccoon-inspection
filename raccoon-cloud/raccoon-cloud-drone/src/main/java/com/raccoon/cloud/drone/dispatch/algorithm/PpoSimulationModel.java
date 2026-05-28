package com.raccoon.cloud.drone.dispatch.algorithm;

import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PPO（Proximal Policy Optimization）预训练模型的轻量代理。
 * <p>项目当前为离线交付，未集成深度学习推理框架；此处实现一个等价的启发式奖励函数，
 * 接口与外部 PPO 推理服务（如 ONNX-Runtime / TorchServe）保持一致：
 * 输入「(任务 × 终端) 分配方案 + 终端实时状态」，输出 [0,1] 的奖励值。
 * 后续接入真实模型时仅需替换 {@link #infer} 内部实现。
 *
 * @author raccoon
 */
@Slf4j
@Component
public class PpoSimulationModel {

    /**
     * 推理奖励值。
     *
     * @param tasks     已分配任务
     * @param terminals 候选终端
     * @return 奖励值 [0,1]
     */
    public double infer(List<DispatchInspectionTask> tasks, List<TerminalState> terminals) {
        if (tasks == null || tasks.isEmpty() || terminals == null || terminals.isEmpty()) {
            return 0.0;
        }
        long assigned = tasks.stream().filter(t -> t.getAssignedTerminalId() != null).count();
        double coverage = (double) assigned / tasks.size();

        double avgBattery = terminals.stream()
                .map(TerminalState::getBatteryPct)
                .filter(v -> v != null)
                .mapToDouble(Float::doubleValue)
                .average().orElse(80.0);

        double avgCapability = terminals.stream()
                .filter(t -> t.getCapacity() != null)
                .mapToDouble(t -> t.getCapacity().getOverallScore())
                .average().orElse(0.5);

        long busyCount = terminals.stream().filter(t ->
                t.getAssignedTaskCount() != null && t.getAssignedTaskCount() > 0).count();
        double loadBalance = 1.0 - Math.abs(((double) busyCount / terminals.size()) - 0.5) * 2.0;
        loadBalance = Math.max(0.0, loadBalance);

        double reward = coverage * 0.5
                + Math.min(1.0, avgBattery / 100.0) * 0.2
                + avgCapability * 0.2
                + loadBalance * 0.1;
        reward = Math.max(0.0, Math.min(1.0, reward));
        log.debug("[ppo] reward={} coverage={} avgBattery={} avgCapability={} loadBalance={}",
                reward, coverage, avgBattery, avgCapability, loadBalance);
        return reward;
    }
}
