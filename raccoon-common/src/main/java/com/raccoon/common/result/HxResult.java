package com.raccoon.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用响应结果封装类（微服务全项目通用）
 * 支持：成功、失败、参数错误、未登录、无权限等
 */
@Data
@Schema(description = "统一返回结果")
@JsonInclude(JsonInclude.Include.NON_NULL) // 空字段不序列化
public class HxResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // 响应码
    @Schema(description = "响应状态码 200=成功")
    private int code;

    // 响应消息
    @Schema(description = "响应消息")
    private String msg;

    // 响应数据
    @Schema(description = "响应数据")
    private T data;

    // ======================== 构造方法 ========================
    public HxResult() {
    }

    public HxResult(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public HxResult(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ======================== 成功（最常用） ========================
    public static <T> HxResult<T> success() {
        return new HxResult<>(200, "操作成功");
    }

    public static <T> HxResult<T> success(T data) {
        return new HxResult<>(200, "操作成功", data);
    }

    public static <T> HxResult<T> success(String msg, T data) {
        return new HxResult<>(200, msg, data);
    }

    // ======================== 失败 ========================
    public static <T> HxResult<T> fail() {
        return new HxResult<>(500, "操作失败");
    }

    public static <T> HxResult<T> fail(String msg) {
        return new HxResult<>(500, msg);
    }

    public static <T> HxResult<T> fail(int code, String msg) {
        return new HxResult<>(code, msg);
    }

    // ======================== 常用业务状态码（微服务必备） ========================
    /**
     * 参数错误
     */
    public static <T> HxResult<T> badRequest(String msg) {
        return new HxResult<>(400, msg);
    }

    /**
     * 未登录
     */
    public static <T> HxResult<T> unauthorized() {
        return new HxResult<>(401, "未登录或登录已过期");
    }

    /**
     * 无权限
     */
    public static <T> HxResult<T> forbidden() {
        return new HxResult<>(403, "无权限访问");
    }

    /**
     * 资源不存在
     */
    public static <T> HxResult<T> notFound() {
        return new HxResult<>(404, "资源不存在");
    }

}