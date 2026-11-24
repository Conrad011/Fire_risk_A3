package com.firerisk.exception;

/** Parametros de analise fora dos limites aceitos. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
