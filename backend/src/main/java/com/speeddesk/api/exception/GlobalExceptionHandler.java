package com.speeddesk.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            InvalidCredentialsException.class,
            AuthenticationCredentialsNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleUnauthorized(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.UNAUTHORIZED,
                "Falha na autenticação",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    public ResponseEntity<ProblemDetail> handleForbidden(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.FORBIDDEN,
                "Acesso negado",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(
            DuplicateEmailException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "E-mail já cadastrado",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DuplicateOrganizationException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateOrganization(
            DuplicateOrganizationException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "Organização já cadastrada",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DuplicateTicketCategoryException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateTicketCategory(
            DuplicateTicketCategoryException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "Categoria já cadastrada",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({
            TicketNotFoundException.class,
            TechnicianNotFoundException.class,
            ClientNotFoundException.class,
            AssetNotFoundException.class,
            UserNotFoundException.class,
            OrganizationNotFoundException.class,
            TicketCategoryNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "Recurso não encontrado",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({LastActiveManagerException.class, UserRoleChangeConflictException.class})
    public ResponseEntity<ProblemDetail> handleBusinessConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "Regra de negócio conflitante",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({
            InvalidTicketStatusTransitionException.class,
            InvalidSlaOperationException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidTicketStatusTransition(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "Transição de status inválida",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "Conflito de concorrencia",
                "O chamado foi alterado por outra operacao. Atualize os dados e tente novamente.",
                request
        );
    }

    @ExceptionHandler({InvalidUserRoleException.class, InvalidRequestException.class})
    public ResponseEntity<ProblemDetail> handleInvalidRequest(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Solicitação inválida",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problemDetail = problemDetail(
                HttpStatus.BAD_REQUEST,
                "Falha de validação",
                "Um ou mais campos são inválidos.",
                request
        );
        problemDetail.setProperty("errors", errors);
        return entity(HttpStatus.BAD_REQUEST, problemDetail);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> handleUnreadableRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Solicitação inválida",
                "O corpo ou os parâmetros da requisição são inválidos.",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "Conflito de dados",
                "A operação conflita com dados já cadastrados.",
                request
        );
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        return entity(status, problemDetail(status, title, detail, request));
    }

    private ProblemDetail problemDetail(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }

    private ResponseEntity<ProblemDetail> entity(
            HttpStatus status,
            ProblemDetail problemDetail
    ) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
