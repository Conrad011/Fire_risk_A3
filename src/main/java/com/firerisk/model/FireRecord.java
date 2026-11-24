package com.firerisk.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um foco de calor detectado por satelite (NASA FIRMS).
 *
 * @param acqDate            dia da deteccao no horario de Brasilia
 * @param detectedAt         data e hora da deteccao no horario de Brasilia; {@code null} se a FIRMS nao informar a hora
 * @param distanceKm         distancia em linha reta ate o centro da cidade
 * @param insideMunicipality se o foco esta dentro do limite do municipio
 */
public record FireRecord(
        LocalDate acqDate,
        LocalDateTime detectedAt,
        double latitude,
        double longitude,
        double frp,
        String confidence,
        String daynight,
        Double brightTi4,
        double distanceKm,
        boolean insideMunicipality
) {

    /**
     * Deteccao de baixa confianca do VIIRS ({@code "l"}), mais sujeita a falso alarme:
     * reflexo do sol, areas industriais, ruido do sensor. Aparece no mapa, mas nao entra no indice.
     */
    public boolean lowConfidence() {
        return "l".equalsIgnoreCase(confidence);
    }

    public FireRecord withDistance(double km) {
        return new FireRecord(acqDate, detectedAt, latitude, longitude, frp, confidence, daynight, brightTi4,
                km, insideMunicipality);
    }

    public FireRecord withInside(boolean inside) {
        return new FireRecord(acqDate, detectedAt, latitude, longitude, frp, confidence, daynight, brightTi4,
                distanceKm, inside);
    }
}
