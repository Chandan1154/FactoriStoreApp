package com.factori.platform.controller;

import com.factori.platform.service.ArtifactStorage.InvalidIconException;
import com.factori.platform.service.PlatformExceptions.BuildQueueFullException;
import com.factori.platform.service.PlatformExceptions.ConflictException;
import com.factori.platform.service.PlatformExceptions.ResourceNotFoundException;
import com.factori.platform.service.StoreConfigValidator.InvalidStoreConfigurationException;
import com.factori.platform.service.EligibilityService.FactoriIntegrationUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({
        InvalidStoreConfigurationException.class,
        InvalidIconException.class,
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        MissingServletRequestPartException.class
    })
    ResponseEntity<ProblemDetail> badRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request or store configuration is invalid.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Not found", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(BuildQueueFullException.class)
    ResponseEntity<ProblemDetail> queueFull(BuildQueueFullException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "30")
                .body(ProblemDetail.forStatusAndDetail(
                        HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "The authenticated tenant cannot access this resource.");
    }

    @ExceptionHandler(FactoriIntegrationUnavailableException.class)
    ResponseEntity<ProblemDetail> integrationUnavailable(
            FactoriIntegrationUnavailableException exception) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Integration unavailable",
                "Eligibility cannot be confirmed until Factori order data is connected.");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
