package com.example.demo.Exception;

public class NameOrPasswordError extends RuntimeException {
    public NameOrPasswordError(String message) {
        super(message);
    }
}
