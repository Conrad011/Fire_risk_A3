package com.firerisk.model;

import java.time.LocalDate;

/**
 * Uma linha diaria da serie climatica do Open-Meteo.
 */
public record ClimateRecord(
        LocalDate date,
        Double temperatureMax,
        Double precipitation,
        Double windspeed,
        Double humidityMin
) {
}
