package com.firerisk.model;

import com.firerisk.util.Brasilia;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Origem dos dados de uma analise.
 *
 * @param mode          {@code "live"} para as APIs ou {@code "snapshot"} para os dados salvos
 * @param referenceDate ultimo dia coberto pelos dados salvos
 * @param savedAt       quando os dados salvos foram gravados
 */
public record DataSource(String mode, LocalDate referenceDate, OffsetDateTime savedAt) {

    public static DataSource live() {
        return new DataSource("live", null, null);
    }

    /** O horario de gravacao sai sempre em Brasilia, qualquer que seja o fuso em que foi lido. */
    public static DataSource snapshot(Snapshot snapshot) {
        return new DataSource("snapshot", snapshot.referenceDate(),
                snapshot.savedAt().atZoneSameInstant(Brasilia.ZONE).toOffsetDateTime());
    }
}
