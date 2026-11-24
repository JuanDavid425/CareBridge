package org.carebridge.api.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.TreeMap;
import org.carebridge.api.assignment.CaseAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps request errors to the {@link ApiError} envelope. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Same body as the filter-chain 403. The reason (outside unit, not assigned, ...) is logged
     * with the request id for support and never returned to the client.
     */
    @ExceptionHandler(CaseAccessDeniedException.class)
    ResponseEntity<ApiError> caseAccessDenied(CaseAccessDeniedException ex, HttpServletRequest request) {
        log.info("Case access denied: reason={} permission={} target={}", ex.reason(), ex.permission(),
                ex.target());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden(RequestIdFilter.of(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.validation(fields, RequestIdFilter.of(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("malformed_request", "The request body could not be read.",
                        RequestIdFilter.of(request)));
    }
}
