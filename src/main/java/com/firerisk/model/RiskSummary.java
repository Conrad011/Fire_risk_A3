package com.firerisk.model;

/**
 * Resumo agregado da analise de risco.
 *
 * <p>Os campos de focos consideram so os que entram no indice (confianca normal ou alta).</p>
 *
 * @param totalFires         focos que entram no indice
 * @param lowConfidenceFires focos de baixa confianca, exibidos mas fora do indice
 * @param nearestFireKm      distancia do foco mais proximo; {@code null} se nao houver focos
 * @param dryDays            dias consecutivos, ate hoje, com menos de 1 mm de chuva
 */
public record RiskSummary(
        String biome,
        double biomeWeight,
        int totalFires,
        int lowConfidenceFires,
        int firesInMunicipality,
        Double nearestFireKm,
        double avgTemp,
        double avgRain,
        Double maxWind,
        Double minHumidity,
        int dryDays,
        double fireScore,
        double climateScore,
        double riskIndex,
        RiskLevel level
) {
}
