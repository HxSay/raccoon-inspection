package com.raccoon.cloud.system.web;

import com.raccoon.common.result.HxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将未处理异常转为统一 {@link HxResult}，便于前端展示具体原因（避免仅返回 Spring 默认 500 JSON）。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public HxResult<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgument: {}", e.getMessage());
        return HxResult.fail(400, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public HxResult<?> handleIllegalState(IllegalStateException e) {
        log.warn("IllegalState: {}", e.getMessage());
        return HxResult.fail(e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public HxResult<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadable: {}", e.getMessage());
        String msg = e.getMostSpecificCause().getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "请求体 JSON 格式错误或字段类型不匹配";
        }
        return HxResult.badRequest(msg);
    }

    @ExceptionHandler(DataAccessException.class)
    public HxResult<?> handleDataAccess(DataAccessException e) {
        log.error("DataAccessException", e);
        return HxResult.fail("数据库错误: " + deepestMessage(e));
    }

    @ExceptionHandler(Exception.class)
    public HxResult<?> handleAny(Exception e) {
        log.error("Unhandled exception", e);
        return HxResult.fail(deepestMessage(e));
    }

    private static String deepestMessage(Throwable e) {
        Throwable cur = e;
        String best = cur.getMessage();
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                best = cur.getMessage();
            }
        }
        return best != null && !best.isBlank() ? best : e.getClass().getSimpleName();
    }
}
