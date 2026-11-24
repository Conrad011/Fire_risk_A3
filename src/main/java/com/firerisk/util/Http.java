package com.firerisk.util;

import com.firerisk.exception.UpstreamException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Cliente HTTP compartilhado pelos coletores.
 *
 * <p>As falhas sao identificadas pelo nome do servico, nunca pela URL, que pode conter chave.</p>
 */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private Http() {
    }

    /** Executa um GET e devolve a resposta, qualquer que seja o status. */
    public static HttpResponse<String> get(String service, String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("Accept", "*/*")
                .GET()
                .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new UpstreamException(service, true);
        } catch (IOException e) {
            throw new UpstreamException(service);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException(service);
        }
    }

    /** Executa um GET e falha se o status nao for 2xx. */
    public static String getOrThrow(String service, String url) {
        HttpResponse<String> response = get(service, url);
        if (response.statusCode() / 100 != 2) {
            throw new UpstreamException(service);
        }
        return response.body();
    }
}
