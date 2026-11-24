package com.firerisk.model;

import com.firerisk.exception.InvalidRequestException;

/**
 * Parametros validados de uma analise.
 *
 * @param city     nome do municipio
 * @param uf       sigla da unidade federativa
 * @param days     dias de historico de focos
 * @param radiusKm raio de busca dos focos ao redor da cidade
 */
public record AnalysisRequest(String city, String uf, int days, int radiusKm) {

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 5;
    public static final int MIN_RADIUS_KM = 25;
    public static final int MAX_RADIUS_KM = 500;

    public AnalysisRequest {
        if (city == null || city.isBlank()) {
            throw new InvalidRequestException("Informe o nome da cidade.");
        }
        city = city.strip();
        uf = State.fromCode(uf)
                .orElseThrow(() -> new InvalidRequestException("Estado inválido. Use a sigla da UF, como MG."))
                .name();
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new InvalidRequestException("O período deve ser de " + MIN_DAYS + " a " + MAX_DAYS + " dias.");
        }
        if (radiusKm < MIN_RADIUS_KM || radiusKm > MAX_RADIUS_KM) {
            throw new InvalidRequestException(
                    "A distância deve ser de " + MIN_RADIUS_KM + " a " + MAX_RADIUS_KM + " km.");
        }
    }

    public AnalysisRequest withCity(String officialName) {
        return new AnalysisRequest(officialName, uf, days, radiusKm);
    }
}
