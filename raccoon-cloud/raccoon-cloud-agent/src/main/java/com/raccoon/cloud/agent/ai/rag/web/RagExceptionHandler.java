package com.raccoon.cloud.agent.ai.rag.web;

import com.raccoon.common.result.HxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * RAG 模块专用异常处理：解析根因后回写更可读的错误信息。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.raccoon.cloud.agent.ai.rag")
public class RagExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public HxResult<Void> handleSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("rag upload size exceeded: {}", e.getMessage());
        return HxResult.badRequest("上传文件过大，请检查 spring.servlet.multipart.max-file-size 限制");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("rag illegal argument: {}", e.getMessage());
        return HxResult.badRequest(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleIllegalState(IllegalStateException e) {
        log.warn("rag illegal state: {}", resolve(e));
        return HxResult.badRequest(resolve(e));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public HxResult<Void> handleOther(Exception e) {
        log.error("rag error", e);
        return HxResult.fail("RAG 服务异常: " + resolve(e));
    }

    private static String resolve(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root.getMessage() != null && !root.getMessage().isBlank()) {
            return root.getMessage().trim();
        }
        return e.getClass().getSimpleName();
    }
}
