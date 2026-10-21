package com.beeleza.pixflow.adapter.in.web;

import com.beeleza.pixflow.application.service.exception.IdempotencyKeyReuseException;
import com.beeleza.pixflow.application.service.exception.PixTransferInProgressException;
import com.beeleza.pixflow.application.service.exception.TransactionNotFoundException;
import com.beeleza.pixflow.domain.exception.DomainException;
import com.beeleza.pixflow.domain.exception.IllegalTransactionStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();

        ProblemDetail problem = ex.getBody();
        problem.setTitle("Invalid request");
        problem.setDetail("One or more fields are invalid");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(IllegalTransactionStateException.class)
    public ProblemDetail handleIllegalTransactionState(IllegalTransactionStateException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid transaction state", ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", ex.getMessage());
    }

    @ExceptionHandler(PixTransferInProgressException.class)
    public ProblemDetail handleTransferInProgress(PixTransferInProgressException ex) {
        return problem(HttpStatus.CONFLICT, "Transfer in progress", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ProblemDetail handleIdempotencyKeyReuse(IdempotencyKeyReuseException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused", ex.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleNotFound(TransactionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Transaction not found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConcurrentModification(RuntimeException ex) {
        log.warn("Concurrent modification detected", ex);
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The transaction was modified concurrently, retry the request");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
