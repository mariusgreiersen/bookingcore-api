package com.bookingcore.common.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.bookingcore.common.logging.CorrelationIdFilter;

/**
 * Translates every exception into an RFC 9457 Problem Details response ({@code application/problem+json}).
 *
 * <p>Each response carries two extension properties: {@code code}, a stable machine-readable {@link ErrorCode}
 * (or the HTTP status name for framework errors), and {@code requestId}, matching the {@code X-Request-Id}
 * header and the log lines of the request. Internal details such as SQL, stack traces or exception messages
 * from infrastructure are never included in responses.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        return respond(problem(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.info("Optimistic locking conflict: {}", ex.getClass().getSimpleName());
        return respond(problem(ErrorCode.CONCURRENT_MODIFICATION,
                "The record was modified by another request. Reload it and retry."));
    }

    /**
     * Lock timeouts and deadlocks (SQLSTATE 40P01) are transient: the transaction was rolled back and nothing was
     * written, so the client can safely retry. Booking writes are ordered by a row lock and should not deadlock;
     * this is defence in depth that keeps such failures from surfacing as opaque 500s.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleLockFailure(PessimisticLockingFailureException ex) {
        log.warn("Transient locking failure, transaction rolled back: {}", ex.getClass().getSimpleName());
        ProblemDetail problem = problem(ErrorCode.TRANSIENT_FAILURE,
                "The request could not be completed due to concurrent activity. Nothing was changed; retry shortly.");
        enrich(problem, HttpStatus.SERVICE_UNAVAILABLE);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1").body(problem);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ProblemDetail> handleInvalidSort(PropertyReferenceException ex) {
        return respond(problem(ErrorCode.VALIDATION_FAILED,
                "Unknown sort property '" + ex.getPropertyName() + "'."));
    }

    /** Last-resort handler: logs the full error server-side, returns a generic body to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(problem(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the requestId when reporting this problem."));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> Map.of(
                        "field", error instanceof FieldError fe ? fe.getField() : error.getObjectName(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid.");
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", String.valueOf(result.getMethodParameter().getParameterName()),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.MALFORMED_REQUEST,
                "The request body is missing or malformed. Timestamps must be ISO 8601 with an explicit "
                        + "offset, e.g. 2027-03-15T10:00:00+01:00.");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        ProblemDetail body = problem(ErrorCode.MALFORMED_REQUEST,
                "Parameter '" + name + "' has an invalid format.");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Final step for all framework exceptions handled by the base class: adds {@code code} and {@code requestId}
     * to the Problem Details body Spring has built.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            enrich(problem, statusCode);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        enrich(problem, HttpStatusCode.valueOf(problem.getStatus()));
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static void enrich(ProblemDetail problem, HttpStatusCode status) {
        if (problem.getType() == null) {
            problem.setType(ABOUT_BLANK);
        }
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey("code")) {
            String fallback = status instanceof HttpStatus httpStatus
                    ? httpStatus.name()
                    : String.valueOf(status.value());
            problem.setProperty("code", fallback);
        }
        String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
    }
}
