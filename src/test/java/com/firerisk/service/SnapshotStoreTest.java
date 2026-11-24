package com.firerisk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firerisk.model.DataSource;
import com.firerisk.model.FireRecord;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.Municipality;
import com.firerisk.model.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotStoreTest {

    @TempDir
    Path dir;

    /** Mesma configuracao de datas que o Spring aplica ao ObjectMapper. */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void gravaELeMantendoOHorarioDeBrasilia() {
        SnapshotStore store = new SnapshotStore(dir.toString(), mapper);
        OffsetDateTime savedAt = OffsetDateTime.of(2026, 11, 24, 9, 56, 0, 0, ZoneOffset.ofHours(-3));
        LocalDateTime detectedAt = LocalDateTime.of(2026, 11, 22, 22, 30);
        Snapshot snapshot = new Snapshot(LocalDate.of(2026, 11, 23), savedAt,
                new Municipality("3167202", "Sete Lagoas", "MG", -19.4658, -44.2467, true), "VIIRS NOAA-20", 5, 500,
                List.of(), List.of(new FireRecord(detectedAt.toLocalDate(), detectedAt, -19, -44, 1, "n", "N", null, 10, false)));

        Path file = store.save(snapshot);
        Snapshot loaded = store.load(new GeoFeature("3167202", "Sete Lagoas", "MG", null)).orElseThrow();
        DataSource source = DataSource.snapshot(loaded);

        assertTrue(file.getFileName().toString().equals("sete-lagoas-mg.json"));
        assertEquals("2026-11-24T09:56-03:00", source.savedAt().toString());
        assertEquals(detectedAt, loaded.fires().get(0).detectedAt());
    }

    @Test
    void horarioGravadoEmUtcSaiEmBrasilia() {
        Snapshot utc = new Snapshot(LocalDate.of(2026, 11, 23), OffsetDateTime.parse("2026-11-24T12:56:00Z"),
                null, null, 5, 500, List.of(), List.of());

        assertEquals("2026-11-24T09:56-03:00", DataSource.snapshot(utc).savedAt().toString());
    }
}
