package com.firerisk.exception;

/** A chave da NASA FIRMS esta ausente ou foi recusada. */
public class ApiKeyException extends RuntimeException {

    private final String hint;

    private ApiKeyException(String message, String hint) {
        super(message);
        this.hint = hint;
    }

    public static ApiKeyException missing() {
        return new ApiKeyException("A chave de acesso da NASA FIRMS não foi configurada.",
                "Crie o arquivo .env na raiz do projeto com a linha NASA_FIRMS_KEY=sua_chave e reinicie o sistema.");
    }

    public static ApiKeyException rejected() {
        return new ApiKeyException("A NASA FIRMS recusou a chave de acesso.",
                "Confira NASA_FIRMS_KEY no arquivo .env ou gere uma chave em firms.modaps.eosdis.nasa.gov/api/area.");
    }

    public String hint() {
        return hint;
    }
}
