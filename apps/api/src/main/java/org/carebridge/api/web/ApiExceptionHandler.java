package org.carebridge.api.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.TreeMap;
import org.carebridge.api.assignment.CaseAccessDeniedException;
import org.carebridge.api.cases.CaseConflictException;
import org.carebridge.api.cases.CaseETags;
import org.carebridge.api.cases.CaseNotFoundException;
import org.carebridge.api.cases.CaseValidationException;
import org.carebridge.api.cases.PreconditionRequiredException;
import org.carebridge.api.cases.StaleCaseVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(CaseNotFoundException.class)
    ResponseEntity<ApiError> caseNotFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("not_found", "That case does not exist.", RequestIdFilter.of(request)));
    }

    @ExceptionHandler(CaseConflictException.class)
    ResponseEntity<ApiError> caseConflict(CaseConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ex.code(), ex.getMessage(), RequestIdFilter.of(request)));
    }

    /** The current ETag is returned so a client can reload and retry deliberately. */
    @ExceptionHandler(StaleCaseVersionException.class)
    ResponseEntity<ApiError> staleVersion(StaleCaseVersionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .eTag(CaseETags.of(ex.currentVersion()))
                .body(ApiError.of("stale_version", "This case changed since you opened it. Reload and try again.",
                        RequestIdFilter.of(request)));
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    ResponseEntity<ApiError> preconditionRequired(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(ApiError.of("precondition_required",
                        "Send the case version you are editing in the If-Match header.", RequestIdFilter.of(request)));
    }

    @ExceptionHandler(CaseValidationException.class)
    ResponseEntity<ApiError> caseInvalid(CaseValidationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.validation(new TreeMap<>(ex.fields()), RequestIdFilter.of(request)));
    }

    @ExceptionHandler(InvalidParameterException.class)
    ResponseEntity<ApiError> invalidParameter(InvalidParameterException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(new ApiError.Body("invalid_parameter",
                "Some request parameters are invalid.", RequestIdFilter.of(request), new TreeMap<>(ex.fields()))));
    }

    /** A malformed id in the path, such as {@code /cases/not-a-uuid}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(new ApiError.Body("invalid_parameter",
                "Some request parameters are invalid.", RequestIdFilter.of(request),
                Map.of(ex.getName(), "Invalid value."))));
    }
}
