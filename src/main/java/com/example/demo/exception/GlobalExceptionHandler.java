package com.example.demo.exception;

import com.example.demo.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把各层抛出的异常统一翻译成
 * 「合理的 HTTP 状态码 + 统一 Result 结构」。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 资源不存在（学生/班级等查无此 id） */
    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(StudentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, e.getMessage()));
    }

    /** 唯一键冲突（学号/手机号/用户名等重复注册） */
    @ExceptionHandler(StudentAlreadyExistsException.class)
    public ResponseEntity<Result<Void>> handleAlreadyExists(StudentAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, e.getMessage()));
    }

    /** 登录凭证错误 */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Result<Void>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, e.getMessage()));
    }

    /** 参数校验失败（@Valid 注解触发） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, msg));
    }

    /** GET 查询参数 / 表单绑定的校验失败（@ModelAttribute + @Valid 抛 BindException） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, msg));
    }

    /** 业务规则异常：Service 层用 (code, message) 表达可预期的失败 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        int code = e.getCode() >= 400 && e.getCode() < 600 ? e.getCode() : 400;
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status)
                .body(Result.error(code, e.getMessage()));
    }

    /** 路径参数类型错误（如 /student/abc） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "参数类型不正确: " + e.getName()));
    }

    /** 请求路径不存在（如打错 URL），避免返回一大页 Whitelabel */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "接口不存在"));
    }

    /**
     * 数据库唯一键冲突兜底（并发注册同名/同学号时，
     * 服务层查重可能漏过，唯一索引是最后防线，捕获后给 409 而不是 500）。
     */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(org.springframework.dao.DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "数据已存在（唯一字段重复）"));
    }

    /** 兜底：未预期的异常，返回 500 但不向客户端泄露堆栈细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        // TODO 阶段6：接入日志框架后，这里要记录完整堆栈到日志，而不是只返回提示
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器内部错误"));
    }
}
