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
    void shouldReturnUnauthorizedForInvalidCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/login");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleInvalidCredentials(
                new InvalidCredentialsException(),
                request
        );

        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), problemDetail.getStatus());
        assertEquals("Falha na autenticação", problemDetail.getTitle());
        assertEquals("E-mail ou senha inválidos", problemDetail.getDetail());
        assertEquals(URI.create("/api/users/login"), problemDetail.getInstance());
    }

    @Test
    void shouldReturnProblemDetailForDuplicateEmail() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleDuplicateEmail(
                new DuplicateEmailException(),
                request
        );

        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertEquals(HttpStatus.CONFLICT.value(), problemDetail.getStatus());
        assertEquals("E-mail já cadastrado", problemDetail.getTitle());
        assertEquals("Já existe um usuário cadastrado com este e-mail.", problemDetail.getDetail());
        assertEquals(URI.create("/api/users"), problemDetail.getInstance());
    }

    @Test
    void shouldReturnProblemDetailWhenTicketDoesNotExist() {
        UUID ticketId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH",
                "/api/tickets/" + ticketId + "/assumir/" + UUID.randomUUID()
        );

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleNotFound(
                new TicketNotFoundException(ticketId),
                request
        );

        ProblemDetail problemDetail = response.getBody();
        assertNotNull(problemDetail);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertEquals(HttpStatus.NOT_FOUND.value(), problemDetail.getStatus());
        assertEquals("Recurso não encontrado", problemDetail.getTitle());
        assertEquals("Chamado não encontrado: " + ticketId, problemDetail.getDetail());
        assertEquals(URI.create(request.getRequestURI()), problemDetail.getInstance());
    }
}
