package com.firerisk.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 11, 23);

    private final Snapshot snapshot = new Snapshot(REFERENCE, OffsetDateTime.now(),
            new Municipality("3167202", "Sete Lagoas", "MG", -19.4658, -44.2467, true),
            "VIIRS NOAA-20", 5, 500, List.of(),
            List.of(fire(REFERENCE, 20.0), fire(REFERENCE.minusDays(2), 150.0), fire(REFERENCE.minusDays(4), 30.0)));

    @Test
    void recortaPeriodoContadoAPartirDoDiaDeReferencia() {
        assertEquals(1, snapshot.firesWithin(1, 500).size());
        assertEquals(2, snapshot.firesWithin(3, 500).size());
        assertEquals(3, snapshot.firesWithin(5, 500).size());
    }

    @Test
    void recortaRaio() {
        assertEquals(2, snapshot.firesWithin(5, 100).size());
    }

    private static FireRecord fire(LocalDate date, double distanceKm) {
        return new FireRecord(date, date.atTime(14, 0), -19.0, -44.0, 1.0, "n", "D", null, distanceKm, false);
    }
}
