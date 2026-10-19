package com.example.demo.exception;

public class NameOrPasswordError extends RuntimeException {
    public NameOrPasswordError(String message) {
        super(message);
    }
}
