package com.mandi.web;

import com.mandi.service.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns errors into one consistent JSON shape: {status, error, message, fields, timestamp}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(int status, String error, String message, Map<String, String> fields, Instant timestamp) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException e) {
        return body(e.getStatus(), e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return body(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields", fields);
    }

    /** Two admins saved the same book at once (JPA @Version) or two checkouts raced on a row. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorBody> handleConcurrentEdit(ObjectOptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "Someone else changed this at the same time - reload and try again", Map.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorBody> handleUnreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "Request could not be read - check the values sent", Map.of());
    }

    private static ResponseEntity<ErrorBody> body(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status)
                .body(new ErrorBody(status.value(), status.getReasonPhrase(), message, fields, Instant.now()));
    }
}
