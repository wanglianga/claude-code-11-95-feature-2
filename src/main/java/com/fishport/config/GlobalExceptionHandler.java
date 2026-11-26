package com.fishport.config;

import com.fishport.service.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(LocalDateTime timestamp, int status, String code, String message,
                            List<String> details) {
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorBody> handleBiz(BizException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorBody(LocalDateTime.now(), e.getStatus().value(), e.getCode(), e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField).toList();
        return ResponseEntity.badRequest().body(new ErrorBody(LocalDateTime.now(), 400,
                "VALIDATION", "请求参数不合法", details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody(LocalDateTime.now(), 500, "INTERNAL", e.getMessage(), null));
    }
}
