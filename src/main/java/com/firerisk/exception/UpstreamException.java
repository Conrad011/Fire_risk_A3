package com.firerisk.exception;

/**
 * Falha ao consultar uma API externa.
 *
 * <p>A mensagem nunca inclui a URL: a da NASA FIRMS carrega a chave de acesso.</p>
 */
public class UpstreamException extends RuntimeException {

    private final boolean timeout;

    public UpstreamException(String service, boolean timeout) {
        super(timeout ? service + " demorou demais para responder."
                : "Não foi possível obter os dados de " + service + ".");
        this.timeout = timeout;
    }

    public UpstreamException(String service) {
        this(service, false);
    }

    public boolean isTimeout() {
        return timeout;
    }
}
