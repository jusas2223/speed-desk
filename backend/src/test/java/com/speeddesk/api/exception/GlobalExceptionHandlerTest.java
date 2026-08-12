package com.speeddesk.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

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
}
