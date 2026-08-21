package com.apextick.booking.web;

import com.apextick.booking.seat.SeatUnavailableException;
import com.apextick.booking.security.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into RFC-7807 problem+json with a stable machine-readable
 * {@code code}, a timestamp and the request correlation id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), ErrorCodes.NOT_FOUND, Map.of());
    }

    @ExceptionHandler(SeatUnavailableException.class)
    ProblemDetail handleSeatUnavailable(SeatUnavailableException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ErrorCodes.SEAT_UNAVAILABLE,
                Map.of("seatIds", ex.getSeatIds()));
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ex.getCode(), ex.getProperties());
    }

    @ExceptionHandler(UnprocessableException.class)
    ProblemDetail handleUnprocessable(UnprocessableException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex.getCode(), ex.getProperties());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access is denied", ErrorCodes.FORBIDDEN, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                ErrorCodes.INTERNAL_ERROR, Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                ErrorCodes.VALIDATION_FAILED, Map.of("fieldErrors", fieldErrors));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, String code, Map<String, Object> extra) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            pd.setProperty("correlationId", correlationId);
        }
        extra.forEach(pd::setProperty);
        return pd;
    }
}
