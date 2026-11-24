package com.firerisk.service;

import com.firerisk.exception.UpstreamException;
import com.firerisk.model.Analysis;
import com.firerisk.model.AnalysisRequest;
import com.firerisk.model.ClimateRecord;
import com.firerisk.model.FireBatch;
import com.firerisk.model.GeoData;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.Municipality;
import com.firerisk.model.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FireRiskServiceTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 11, 23);
    private static final AnalysisRequest REQUEST = new AnalysisRequest("Sete Lagoas", "MG", 5, 100);

    private final DataCache cache = mock(DataCache.class);
    private final SnapshotStore snapshots = mock(SnapshotStore.class);
    private final FireRiskService service = new FireRiskService(cache, snapshots);

    private final GeoFeature feature = new GeoFeature("3167202", "Sete Lagoas", "MG",
            new GeometryFactory().toGeometry(new org.locationtech.jts.geom.Envelope(-45, -44, -20, -19)));
    private final Municipality city = new Municipality("3167202", "Sete Lagoas", "MG", -19.4658, -44.2467, true);
    private final List<ClimateRecord> climate = List.of(new ClimateRecord(REFERENCE, 30.0, 0.0, 10.0, 40.0));

    @BeforeEach
    void setUp() {
        when(cache.loadGeo()).thenReturn(new GeoData(List.of(feature)));
        when(cache.locate(any())).thenReturn(city);
    }

    @Test
    void usaAsApisQuandoRespondem() {
        when(cache.loadClimate(anyDouble(), anyDouble())).thenReturn(climate);
        when(cache.loadFires(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new FireBatch("VIIRS NOAA-20", List.of()));

        Analysis analysis = service.analyze(REQUEST);

        assertEquals("live", analysis.dataSource().mode());
        assertNull(analysis.dataSource().referenceDate());
    }

    @Test
    void usaDadosSalvosQuandoAApiFalha() {
        when(cache.loadClimate(anyDouble(), anyDouble())).thenThrow(new UpstreamException("Open-Meteo"));
        when(snapshots.load(feature)).thenReturn(Optional.of(new Snapshot(REFERENCE, OffsetDateTime.now(), city,
                "VIIRS NOAA-20", 5, 500, climate, List.of())));

        Analysis analysis = service.analyze(REQUEST);

        assertEquals("snapshot", analysis.dataSource().mode());
        assertEquals(REFERENCE, analysis.dataSource().referenceDate());
        assertEquals(1, analysis.climate().size());
    }

    @Test
    void semDadosSalvosOErroOriginalContinua() {
        when(cache.loadClimate(anyDouble(), anyDouble())).thenThrow(new UpstreamException("Open-Meteo"));
        when(snapshots.load(feature)).thenReturn(Optional.empty());

        assertThrows(UpstreamException.class, () -> service.analyze(REQUEST));
    }

    @Test
    void marcaFocosDentroDoMunicipio() {
        var inside = new com.firerisk.model.FireRecord(REFERENCE, null, -19.5, -44.5, 1, "n", "D", null, 5, false);
        var outside = new com.firerisk.model.FireRecord(REFERENCE, null, -18.5, -44.5, 1, "n", "D", null, 60, false);

        var marked = FireRiskService.markInside(List.of(inside, outside),
                new GeometryFactory().toGeometry(new org.locationtech.jts.geom.Envelope(-45, -44, -20, -19)));

        assertEquals(List.of(true, false), marked.stream().map(f -> f.insideMunicipality()).toList());
        assertEquals(new Coordinate(-44.5, -19.5), new Coordinate(marked.get(0).longitude(), marked.get(0).latitude()));
    }
}
