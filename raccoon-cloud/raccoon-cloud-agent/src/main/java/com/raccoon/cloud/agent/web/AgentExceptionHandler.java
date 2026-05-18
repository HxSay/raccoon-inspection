package com.raccoon.cloud.agent.web;

import com.raccoon.common.result.HxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return HxResult.badRequest(msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleBind(BindException e) {
        return HxResult.badRequest("参数绑定失败");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return HxResult.badRequest("请求体 JSON 格式错误");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        return HxResult.badRequest(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleIllegalState(IllegalStateException e) {
        return HxResult.badRequest(e.getMessage());
    }

    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public HxResult<Void> handleResourceAccess(ResourceAccessException e) {
        log.warn("ollama connection error: {}", e.getMessage());
        return HxResult.fail("无法连接 Ollama 服务，请确认已执行 ollama serve 且地址正确");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public HxResult<Void> handleOther(Exception e) {
        log.error("agent api error", e);
        String msg = e.getMessage() != null ? e.getMessage() : "服务器内部错误";
        if (msg.contains("Connection refused") || msg.contains("connect")) {
            return HxResult.fail("无法连接 Ollama，请检查 spring.ai.ollama.base-url 与服务是否启动");
        }
        return HxResult.fail(msg);
    }
}
