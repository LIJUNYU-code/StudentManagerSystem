package com.example.demo.ExceptionHandler;

import com.example.demo.Day1.Result;
import com.example.demo.Exception.NameOrPasswordError;
import com.example.demo.Exception.StudentHasExisit;
import com.example.demo.Exception.StudentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<Result<Void>> handleStudentNotFound(StudentNotFoundException e) {
        Result<Void> result = Result.error(404, e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    @ExceptionHandler
    public ResponseEntity<Result<Void>> handleStudentHasExisit(StudentHasExisit e) {
        Result<Void> result = Result.error(409, e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage()).collect(Collectors.joining(";"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, msg));
    }
    @ExceptionHandler(NameOrPasswordError.class)
    public ResponseEntity<Result<Void>> handleNameOrPasswordError(NameOrPasswordError e) {
        Result<Void> result = Result.error(401, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(result);
    }
}
