package com.example.demo.exception;

/**
 * 登录凭证错误（用户名不存在或密码不匹配）。
 * 对应 HTTP 401。
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
