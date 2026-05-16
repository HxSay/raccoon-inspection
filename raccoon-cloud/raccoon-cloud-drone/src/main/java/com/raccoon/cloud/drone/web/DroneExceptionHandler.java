package com.raccoon.cloud.drone.web;

import com.raccoon.common.result.HxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class DroneExceptionHandler {

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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        return HxResult.badRequest(e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("data integrity: {}", e.getMessage());
        String msg = e.getMessage() != null && e.getMessage().contains("task_id")
                ? "该巡检任务已存在路径规划，请勿重复提交或清空任务关联"
                : "数据约束冲突，请检查填写内容";
        return HxResult.badRequest(msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public HxResult<Void> handleOther(Exception e) {
        log.error("drone api error", e);
        return HxResult.fail(e.getMessage() != null ? e.getMessage() : "服务器内部错误");
    }
}
