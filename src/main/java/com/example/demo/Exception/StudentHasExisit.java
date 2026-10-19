package com.example.demo.Exception;

public class StudentHasExisit extends RuntimeException {
    public StudentHasExisit(String message) {
        super(message);
    }
}
