package com.raccoon.cloud.agent.ai.milvus.web;

import com.raccoon.common.result.HxResult;
import io.milvus.exception.MilvusException;
import io.milvus.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Milvus 测试接口专用异常处理（仅作用于本包 Controller）。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = MilvusTestController.class)
public class MilvusTestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return HxResult.badRequest(msg);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleBusiness(RuntimeException e) {
        log.warn("milvus test: {}", e.getMessage());
        return HxResult.fail(e.getMessage());
    }

    @ExceptionHandler(ParamException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HxResult<Void> handleParam(ParamException e) {
        log.warn("milvus test param error: {}", e.getMessage());
        String msg = e.getMessage() != null ? e.getMessage() : "Milvus 参数错误";
        if (msg.contains("dimension")) {
            return HxResult.fail("向量维度不匹配: " + msg
                    + "。请重启 agent，或确认集合 inspection_rag_store 与 embedding 模型维度一致。");
        }
        return HxResult.fail(msg);
    }

    @ExceptionHandler(MilvusException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public HxResult<Void> handleMilvus(MilvusException e) {
        log.warn("milvus test milvus error: {}", e.getMessage());
        String msg = e.getMessage() != null ? e.getMessage() : "Milvus 操作失败";
        if (msg.contains("schema does not contain vector field")) {
            return HxResult.fail(
                    "Milvus 集合 schema 不正确（缺少向量字段）。请重启 raccoon-cloud-agent，"
                            + "或删除旧集合后使用集合 inspection_rag_store（见 agent-milvus-test.yml）");
        }
        return HxResult.fail("Milvus 错误: " + resolveMessage(e));
    }

    @ExceptionHandler(NonTransientAiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public HxResult<Void> handleAi(NonTransientAiException e) {
        log.warn("milvus test ai error: {}", e.getMessage());
        String msg = e.getMessage() != null ? e.getMessage() : "Embedding 调用失败";
        if (msg.contains("Connection") || msg.contains("refused")) {
            return HxResult.fail("无法连接 Ollama，请先执行: ollama serve");
        }
        if (msg.contains("not found") && msg.contains("model")) {
            return HxResult.fail(
                    "Ollama 未安装所需 Embedding 模型。请执行: ollama pull nomic-embed-text "
                            + "（或在 agent-milvus-test.yml 中设置 raccoon.milvus-test.embedding-model）");
        }
        return HxResult.fail(resolveMessage(e));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public HxResult<Void> handleRuntime(RuntimeException e) {
        log.error("milvus test runtime error", e);
        return HxResult.fail(resolveMessage(e));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public HxResult<Void> handleOther(Exception e) {
        log.error("milvus test error", e);
        String msg = resolveMessage(e);
        if (msg.contains("UNAVAILABLE") || msg.contains("Connection refused")) {
            return HxResult.fail("无法连接 Milvus，请确认 19530 端口服务已启动");
        }
        if (msg.contains("Failed to insert")) {
            return HxResult.fail("Milvus 写入失败: " + msg);
        }
        return HxResult.fail(msg);
    }

    /** 拼接异常链消息，避免仅返回 “Failed to insert:” 而无根因 */
    static String resolveMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root.getMessage() != null && !root.getMessage().isBlank()) {
            return root.getMessage().trim();
        }
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 8) {
            String part = cur.getMessage();
            if (part != null && !part.isBlank() && !part.trim().equals("Failed to insert:")) {
                String trimmed = part.trim();
                if (!sb.toString().contains(trimmed)) {
                    if (sb.length() > 0) {
                        sb.append(" | ");
                    }
                    sb.append(trimmed);
                }
            }
            cur = cur.getCause();
            depth++;
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        return e.getClass().getSimpleName();
    }
}
