package com.firerisk.service;

import com.firerisk.collectors.Climate;
import com.firerisk.collectors.Firms;
import com.firerisk.exception.InvalidRequestException;
import com.firerisk.exception.MunicipalityNotFoundException;
import com.firerisk.model.Analysis;
import com.firerisk.model.ClimateRecord;
import com.firerisk.model.FireBatch;
import com.firerisk.model.FireRecord;
import com.firerisk.model.GeoData;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.HistoryEntry;
import com.firerisk.model.Municipality;
import com.firerisk.model.RiskSummary;
import com.firerisk.model.State;
import com.firerisk.processors.RiskProcessor;
import com.firerisk.repository.AnalysisRepository;
import com.firerisk.util.Brasilia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Historico das analises no banco SQLite.
 */
@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    private static final int MAX_ENTRIES = 200;
    /** Filtros usados na carga historica: os padroes do painel. */
    public static final int BACKFILL_DAYS = 5;
    public static final int BACKFILL_RADIUS_KM = 100;
    /** O historico recente da FIRMS cobre cerca de dois meses. */
    public static final int MAX_BACKFILL_DAYS = 55;
    private static final int CLIMATE_WINDOW_DAYS = 30;

    private final AnalysisRepository repository;
    private final DataCache cache;
    private final Climate climate;
    private final Firms firms;

    public HistoryService(AnalysisRepository repository, DataCache cache, Climate climate, Firms firms) {
        this.repository = repository;
        this.cache = cache;
        this.climate = climate;
        this.firms = firms;
    }

    /** Uma falha no banco nao impede a analise de ser exibida. */
    public void record(Analysis analysis) {
        String source = analysis.dataSource().mode();
        LocalDate reference = analysis.dataSource().referenceDate() != null
                ? analysis.dataSource().referenceDate()
                : analysis.analyzedAt().toLocalDate();
        try {
            repository.save(analysis.municipality(), analysis.request().days(), analysis.request().radiusKm(),
                    analysis.summary(), analysis.satellite(), source, reference, analysis.analyzedAt());
        } catch (DataAccessException e) {
            log.warn("Analise nao gravada no historico: {}", e.getMostSpecificCause().getMessage());
        }
    }

    public List<HistoryEntry> history(String city, String uf) {
        return repository.findByMunicipality(find(city, uf).codarea(), MAX_ENTRIES);
    }

    /** Resultado da carga historica, para o terminal informar o que foi feito. */
    public record BackfillResult(String municipality, LocalDate first, LocalDate last, int saved, int skipped) {
    }

    /**
     * Recalcula o indice de cada um dos ultimos {@code daysBack} dias fechados, com os dados
     * que a NASA e o Open-Meteo tinham para aquele dia. Dias ja gravados sao pulados.
     */
    public BackfillResult backfill(String city, String uf, int daysBack) {
        if (daysBack < 1 || daysBack > MAX_BACKFILL_DAYS) {
            throw new InvalidRequestException("Informe de 1 a " + MAX_BACKFILL_DAYS + " dias para a carga histórica.");
        }
        GeoFeature feature = find(city, uf);
        State state = State.valueOf(feature.uf());
        Municipality municipality = cache.locate(feature);

        LocalDate last = Brasilia.today().minusDays(1);
        LocalDate first = last.minusDays(daysBack - 1L);
        List<ClimateRecord> series = climate.getDataBetween(municipality.lat(), municipality.lon(),
                first.minusDays(CLIMATE_WINDOW_DAYS), last);

        int saved = 0;
        int skipped = 0;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (repository.exists(feature.codarea(), day, BACKFILL_DAYS, BACKFILL_RADIUS_KM, "backfill")) {
                skipped++;
                continue;
            }
            LocalDate end = day;
            LocalDate start = day.minusDays(CLIMATE_WINDOW_DAYS);
            List<ClimateRecord> window = series.stream()
                    .filter(c -> !c.date().isBefore(start) && !c.date().isAfter(end))
                    .toList();
            FireBatch batch = firms.getFires(municipality.lat(), municipality.lon(),
                    BACKFILL_DAYS, BACKFILL_RADIUS_KM, day);
            List<FireRecord> fires = FireRiskService.markInside(batch.fires(), feature.geometry());
            RiskSummary summary = RiskProcessor.process(window, fires, state.getBiome()).summary();

            repository.save(municipality, BACKFILL_DAYS, BACKFILL_RADIUS_KM, summary, batch.satellite(),
                    "backfill", day, Brasilia.now());
            saved++;
        }
        return new BackfillResult(feature.nome() + " / " + feature.uf(), first, last, saved, skipped);
    }

    private GeoFeature find(String city, String uf) {
        State state = State.fromCode(uf)
                .orElseThrow(() -> new InvalidRequestException("Estado inválido. Use a sigla da UF, como MG."));
        GeoData geo = cache.loadGeo();
        return geo.findByName(city, state.name())
                .orElseThrow(() -> new MunicipalityNotFoundException(city, state.getLabel(),
                        geo.suggest(city, state.name(), 5)));
    }
}
