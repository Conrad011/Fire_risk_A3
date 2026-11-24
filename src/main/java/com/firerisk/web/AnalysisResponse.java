package com.firerisk.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.firerisk.model.Analysis;
import com.firerisk.model.AnalysisRequest;
import com.firerisk.model.ClimateRecord;
import com.firerisk.model.DataSource;
import com.firerisk.model.DailyRisk;
import com.firerisk.model.FireRecord;
import com.firerisk.model.Municipality;
import com.firerisk.model.RiskSummary;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Payload consumido pelo dashboard.
 *
 * @param params              filtros efetivamente usados, com o nome oficial da cidade
 * @param dataSource          se os dados vieram das APIs ou do arquivo salvo
 * @param analyzedAt          momento da analise, no horario de Brasilia
 * @param municipalityGeoJson geometria do municipio, para o overlay do mapa
 */
public record AnalysisResponse(
        AnalysisRequest params,
        Municipality municipality,
        RiskSummary summary,
        String satellite,
        DataSource dataSource,
        OffsetDateTime analyzedAt,
        List<ClimateRecord> climate,
        List<DailyRisk> daily,
        List<FireRecord> fires,
        JsonNode municipalityGeoJson
) {

    public static AnalysisResponse of(Analysis analysis, JsonNode geoJson) {
        return new AnalysisResponse(
                analysis.request(),
                analysis.municipality(),
                analysis.summary(),
                analysis.satellite(),
                analysis.dataSource(),
                analysis.analyzedAt(),
                analysis.climate(),
                analysis.daily(),
                analysis.fires(),
                geoJson);
    }
}
