package com.firerisk.model;

import java.time.LocalDate;

/**
 * Serie diaria combinando clima e focos de calor.
 */
public record DailyRisk(
        LocalDate date,
        Double temperatureMax,
        Double precipitation,
        Double windspeed,
        Double humidityMin,
        long totalFires,
        int hadFire
) {
}
