package com.raccoon.cloud.agent.web;

import com.raccoon.cloud.agent.dto.OllamaChatRequest;
import com.raccoon.cloud.agent.dto.OllamaChatResponse;
import com.raccoon.cloud.agent.dto.OllamaHealthVO;
import com.raccoon.cloud.agent.dto.OllamaModelVO;
import com.raccoon.cloud.agent.service.OllamaChatService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基于 Spring AI 的 Ollama 本地大模型测试接口。
 */
@RestController
@RequestMapping("/ollama")
@RequiredArgsConstructor
public class OllamaChatController {

    private final OllamaChatService ollamaChatService;

    @GetMapping("/health")
    public HxResult<OllamaHealthVO> health() {
        return HxResult.success(ollamaChatService.health());
    }

    @GetMapping("/models")
    public HxResult<List<OllamaModelVO>> models() {
        return HxResult.success(ollamaChatService.listModels());
    }

    @PostMapping("/chat")
    public HxResult<OllamaChatResponse> chat(@Valid @RequestBody OllamaChatRequest request) {
        return HxResult.success(ollamaChatService.chat(request));
    }
}
