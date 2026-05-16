package com.raccoon.cloud.iotdata.web;

import com.raccoon.common.result.HxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class IotDataExceptionHandler {

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
        log.warn("json parse error: {}", e.getMessage());
        return HxResult.badRequest("请求体 JSON 格式错误或时间字段无法解析，请使用 yyyy-MM-dd HH:mm:ss");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        return HxResult.badRequest(e.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleDataAccess(DataAccessException e) {
        log.warn("data access: {}", e.getMessage());
        String msg = e.getMessage() != null && e.getMessage().contains("doesn't exist")
                ? "数据库表不存在，请执行 iot-data 模块下 db/*.sql 建表脚本"
                : "数据库写入失败，请检查 hxsay_agent_iot 连接与表结构";
        return HxResult.badRequest(msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public HxResult<Void> handleOther(Exception e) {
        log.error("iot-data api error", e);
        return HxResult.fail(e.getMessage() != null ? e.getMessage() : "服务器内部错误");
    }
}
