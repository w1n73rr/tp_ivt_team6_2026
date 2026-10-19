package tech.genna.TY3.controller;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tech.genna.TY3.api.model.ErrorResponse;
import tech.genna.TY3.api.model.FieldError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Проверьте поля запроса")
                .fieldErrors(errors));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Некорректный запрос"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException exception) {
        String code = exception instanceof BadCredentialsException
                ? "INVALID_CREDENTIALS" : "AUTHENTICATION_REQUIRED";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(code, "Неверные учётные данные или требуется вход"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException exception) {
        String reason = exception.getReason();
        String code = reason != null && reason.matches("[A-Z][A-Z0-9_]+") ? reason
                : statusCode(exception.getStatusCode().value());
        String message = switch (exception.getStatusCode().value()) {
            case 400 -> "Некорректный запрос";
            case 401 -> "Требуется авторизация";
            case 403 -> "Недостаточно прав";
            case 404 -> "Ресурс не найден или недоступен";
            case 409 -> "Конфликт данных или текущего состояния";
            default -> "Не удалось выполнить запрос";
        };
        return ResponseEntity.status(exception.getStatusCode()).body(new ErrorResponse(code, message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadLimit(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("FILE_TOO_LARGE", "Превышен допустимый размер файла"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode()).body(new ErrorResponse(
                    statusCode(error.getStatusCode().value()), "Не удалось выполнить запрос"));
        }
        logger.error("Необработанная ошибка API", exception);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Внутренняя ошибка сервера"));
    }

    private String statusCode(int status) {
        return switch (status) {
            case 400 -> "VALIDATION_ERROR";
            case 401 -> "AUTHENTICATION_REQUIRED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 413 -> "FILE_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            default -> "INTERNAL_ERROR";
        };
    }
}
