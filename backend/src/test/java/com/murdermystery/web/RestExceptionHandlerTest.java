package com.murdermystery.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

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

    @Test
    void handleMethodArgumentNotValid_returns400() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "hostNickname", "must not be blank"));
        Method method = Object.class.getDeclaredMethod("toString");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
            new org.springframework.core.MethodParameter(method, -1), bindingResult);

        ProblemDetail result = handler.handleMethodArgumentNotValid(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).contains("hostNickname");
    }

    @Test
    void handleIllegalState_returns500WithGenericMessage() {
        ProblemDetail result = handler.handleIllegalState(new IllegalStateException("invite code exhausted"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getDetail()).isEqualTo("잠시 후 다시 시도해주세요");
    }
}
