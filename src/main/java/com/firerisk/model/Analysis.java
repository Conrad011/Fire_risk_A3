package com.firerisk.model;

import org.locationtech.jts.geom.Geometry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resultado completo de uma analise, com tudo que o CLI e o dashboard precisam.
 */
public record Analysis(
        AnalysisRequest request,
        Municipality municipality,
        Geometry geometry,
        String satellite,
        DataSource dataSource,
        List<ClimateRecord> climate,
        List<FireRecord> fires,
        RiskResult risk,
        OffsetDateTime analyzedAt
) {

    public RiskSummary summary() {
        return risk.summary();
    }

    public List<DailyRisk> daily() {
        return risk.daily();
    }
}
