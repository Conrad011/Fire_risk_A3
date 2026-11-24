package com.firerisk.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dados salvos de um municipio, usados quando as APIs externas nao respondem.
 *
 * <p>Guarda a maior janela possivel (5 dias, 500 km) para que qualquer filtro menor
 * possa ser recortado dela.</p>
 *
 * @param referenceDate ultimo dia coberto pelos dados, no horario de Brasilia
 * @param savedAt       quando o arquivo foi gravado
 */
public record Snapshot(
        LocalDate referenceDate,
        OffsetDateTime savedAt,
        Municipality municipality,
        String satellite,
        int days,
        int radiusKm,
        List<ClimateRecord> climate,
        List<FireRecord> fires
) {

    /** Focos dentro do periodo e do raio pedidos, contados a partir do dia de referencia. */
    public List<FireRecord> firesWithin(int requestDays, int requestRadiusKm) {
        LocalDate start = referenceDate.minusDays(requestDays - 1L);
        return fires.stream()
                .filter(f -> f.distanceKm() <= requestRadiusKm && !f.acqDate().isBefore(start))
                .toList();
    }
}
