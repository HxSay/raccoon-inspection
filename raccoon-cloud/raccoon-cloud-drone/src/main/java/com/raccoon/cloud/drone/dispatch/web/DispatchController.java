package com.raccoon.cloud.drone.dispatch.web;

import com.raccoon.cloud.drone.dispatch.dto.DispatchSnapshotVO;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskRequest;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskResponse;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dispatch.service.TaskGenerateService;
import com.raccoon.cloud.drone.dispatch.service.TerminalStateService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 调度中枢对外 REST 接口。
 *
 * @author raccoon
 */
@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final TaskGenerateService taskGenerateService;
    private final TerminalStateService terminalStateService;

    /**
     * 提交任务并执行端到端调度。
     */
    @PostMapping("/task/generate")
    public HxResult<DispatchTaskResponse> generate(@Valid @RequestBody DispatchTaskRequest request) {
        return HxResult.success(taskGenerateService.generate(request));
    }

    /**
     * 拉取场景调度快照，供中央 Agent 决策。
     */
    @GetMapping("/snapshot/{mapId}")
    public HxResult<DispatchSnapshotVO> snapshot(@PathVariable Long mapId) {
        return HxResult.success(terminalStateService.buildSnapshot(mapId));
    }

    /**
     * 单终端实时状态。
     */
    @GetMapping("/terminal/{terminalId}")
    public HxResult<TerminalState> terminal(@PathVariable Long terminalId) {
        Optional<TerminalState> state = terminalStateService.getTerminalState(terminalId);
        return state.map(HxResult::success).orElseGet(() -> HxResult.notFound());
    }

    /**
     * 列出场景下全部终端状态。
     */
    @GetMapping("/terminal/by-map/{mapId}")
    public HxResult<List<TerminalState>> listByMap(@PathVariable Long mapId) {
        return HxResult.success(terminalStateService.listByMap(mapId));
    }

    /**
     * 失效缓存（终端配置变更后调用）。
     */
    @PostMapping("/cache/evict/{terminalId}")
    public HxResult<Void> evict(@PathVariable Long terminalId) {
        terminalStateService.evict(terminalId);
        return HxResult.success();
    }
}
