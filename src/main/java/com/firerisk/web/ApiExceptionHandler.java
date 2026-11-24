package com.firerisk.web;

import com.firerisk.exception.ApiKeyException;
import com.firerisk.exception.InvalidRequestException;
import com.firerisk.exception.MunicipalityNotFoundException;
import com.firerisk.exception.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Converte as falhas em respostas JSON que o dashboard sabe exibir.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MunicipalityNotFoundException.class)
    public ResponseEntity<ApiError> notFound(MunicipalityNotFoundException e) {
        String hint = e.suggestions().isEmpty()
                ? "Confira a grafia e o estado selecionado."
                : "Confira a grafia e o estado, ou escolha uma das opções abaixo.";
        return body(HttpStatus.NOT_FOUND, e.getMessage(), hint, e.suggestions());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> invalid(InvalidRequestException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), "Ajuste os filtros e tente novamente.", List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badParameter(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "Parâmetros inválidos.", "Informe cidade, UF, dias e raio.", List.of());
    }

    @ExceptionHandler(ApiKeyException.class)
    public ResponseEntity<ApiError> apiKey(ApiKeyException e) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e.hint(), List.of());
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ApiError> upstream(UpstreamException e) {
        String hint = e.isTimeout()
                ? "O serviço pode estar sobrecarregado. Aguarde um instante e tente novamente."
                : "Verifique sua conexão com a internet e tente novamente.";
        return body(HttpStatus.BAD_GATEWAY, e.getMessage(), hint, List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiError> routing(Exception e) {
        HttpStatusCode status = ((ErrorResponse) e).getStatusCode();
        return ResponseEntity.status(status).body(new ApiError("Recurso não disponível.", null, List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Erro inesperado: {}", e.getClass().getName());
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado.",
                "Tente novamente em alguns instantes.", List.of());
    }

    private ResponseEntity<ApiError> body(HttpStatus status, String error, String hint, List<String> suggestions) {
        return ResponseEntity.status(status).body(new ApiError(error, hint, suggestions));
    }
}
