package com.speeddesk.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void returnsUnauthorizedProblemDetailForInvalidCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/users/login"
        );

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleUnauthorized(
                new InvalidCredentialsException(),
                request
        );

        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertEquals(401, problemDetail.getStatus());
        assertEquals("Falha na autenticação", problemDetail.getTitle());
        assertEquals(URI.create("/api/users/login"), problemDetail.getInstance());
    }

    @Test
    void returnsConflictProblemDetailForDuplicateEmail() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleDuplicateEmail(
                new DuplicateEmailException(),
                request
        );

        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("E-mail já cadastrado", problemDetail.getTitle());
    }

    @Test
    void returnsNotFoundProblemDetailForMissingTicket() {
        UUID ticketId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH",
                "/api/tickets/" + ticketId + "/resolver"
        );

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleNotFound(
                new TicketNotFoundException(ticketId),
                request
        );

        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Recurso não encontrado", problemDetail.getTitle());
        assertEquals(URI.create(request.getRequestURI()), problemDetail.getInstance());
    }

    @Test
    void returnsForbiddenProblemDetailForObjectAuthorizationFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tickets");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleForbidden(
                new ForbiddenOperationException("Operação não permitida."),
                request
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Acesso negado", response.getBody().getTitle());
    }
}
