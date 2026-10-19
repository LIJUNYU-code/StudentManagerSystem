package com.example.demo.exception;

/**
 * 业务规则异常基类：表达“可预期的业务失败”，携带业务错误码。
 * <p>
 * 与“资源不存在/凭证错误”等具体异常的关系：
 * 具体异常语义更清晰、处理更精确，优先使用；
 * 那些不值得单独建类的规则校验（如“至少改一个字段”）直接抛本类。
 * 全局处理器按 code 映射 HTTP 状态码（400~599 之外一律按 400 处理）。
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码，与 HTTP 状态码对齐（400/404/409...） */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
