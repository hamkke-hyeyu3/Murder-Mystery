package com.murdermystery.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void handleIllegalArgument_returns400WithDetail() {
        ProblemDetail result = handler.handleIllegalArgument(new IllegalArgumentException("invalid input"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).isEqualTo("invalid input");
    }

    @Test
    void handleGeneral_returns500WithDefaultMessage() {
        ProblemDetail result = handler.handleGeneral(new RuntimeException());

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getDetail()).isEqualTo("Internal server error");
    }

    @Test
    void handleGeneral_withMessage_includesMessage() {
        ProblemDetail result = handler.handleGeneral(new RuntimeException("unexpected failure"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getDetail()).isEqualTo("unexpected failure");
    }
}
