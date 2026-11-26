package com.firerisk.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Uma analise gravada no banco.
 *
 * @param analyzedAt    quando a analise foi feita, no horario de Brasilia
 * @param referenceDate ultimo dia coberto pelos dados
 * @param dataSource    {@code live}, {@code snapshot} (dados salvos) ou {@code backfill} (recalculada)
 */
public record HistoryEntry(
        OffsetDateTime analyzedAt,
        LocalDate referenceDate,
        int days,
        int radiusKm,
        double riskIndex,
        RiskLevel level,
        int firesCounted,
        int firesLowConfidence,
        int firesInMunicipality,
        Double avgTemp,
        Double avgRain,
        int dryDays,
        String satellite,
        String dataSource
) {
}
