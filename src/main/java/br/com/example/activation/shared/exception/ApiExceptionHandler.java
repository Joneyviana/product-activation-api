package br.com.example.activation.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Solicitação não encontrada", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflito", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
                .toList();
        var result = problem(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Um ou mais campos não passaram na validação.", request);
        result.setProperty("errors", errors);
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Dados inválidos", ex.getMessage(), request);
    }

    @ExceptionHandler({IllegalStateException.class, OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail conflictState(RuntimeException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflito de estado",
                "A solicitação não pode ser atualizada no estado atual.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        // Não expor mensagem interna nem stack trace ao consumidor.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Informe o traceId ao suporte.", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setType(URI.create("https://api.example.com/problems/" + status.value()));
        result.setTitle(title);
        result.setInstance(URI.create(request.getRequestURI()));
        result.setProperty("traceId", MDC.get("traceId") == null ? UUID.randomUUID().toString() : MDC.get("traceId"));
        return result;
    }
}
