package com.firerisk.service;

import com.firerisk.collectors.Climate;
import com.firerisk.collectors.Firms;
import com.firerisk.collectors.Vegetation;
import com.firerisk.exception.InvalidRequestException;
import com.firerisk.exception.MunicipalityNotFoundException;
import com.firerisk.model.AnalysisRequest;
import com.firerisk.model.ClimateRecord;
import com.firerisk.model.FireBatch;
import com.firerisk.model.GeoData;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.Municipality;
import com.firerisk.model.Snapshot;
import com.firerisk.model.State;
import com.firerisk.util.Brasilia;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Grava os dados de um dia fechado para uso sem internet.
 *
 * <p>Consulta as APIs diretamente, sem cache, na maior janela aceita (5 dias, 500 km).</p>
 */
@Service
public class SnapshotService {

    /** Limite do historico recente da FIRMS e do Open-Meteo. */
    private static final int MAX_AGE_DAYS = 60;

    private final DataCache cache;
    private final Vegetation vegetation;
    private final Climate climate;
    private final Firms firms;
    private final SnapshotStore store;

    public SnapshotService(DataCache cache, Vegetation vegetation, Climate climate, Firms firms, SnapshotStore store) {
        this.cache = cache;
        this.vegetation = vegetation;
        this.climate = climate;
        this.firms = firms;
        this.store = store;
    }

    /** Resultado da gravacao, para o terminal informar o que foi salvo. */
    public record Saved(Path file, Snapshot snapshot) {
    }

    public Saved save(String city, String uf, LocalDate reference) {
        LocalDate today = Brasilia.today();
        if (!reference.isBefore(today) || reference.isBefore(today.minusDays(MAX_AGE_DAYS))) {
            throw new InvalidRequestException("A data deve ser um dia já encerrado dos últimos " + MAX_AGE_DAYS + " dias.");
        }
        State state = State.fromCode(uf)
                .orElseThrow(() -> new InvalidRequestException("Estado inválido. Use a sigla da UF, como MG."));

        store.saveNames(vegetation.fetchMunicipalityNames());

        GeoData geo = cache.loadGeo();
        GeoFeature feature = geo.findByName(city, state.name())
                .orElseThrow(() -> new MunicipalityNotFoundException(city, state.getLabel(),
                        geo.suggest(city, state.name(), 5)));
        Municipality municipality = cache.locate(feature);

        List<ClimateRecord> series = climate.getDataUntil(municipality.lat(), municipality.lon(), reference);
        FireBatch batch = firms.getFires(municipality.lat(), municipality.lon(),
                AnalysisRequest.MAX_DAYS, AnalysisRequest.MAX_RADIUS_KM, reference);

        Snapshot snapshot = new Snapshot(reference, Brasilia.now(), municipality, batch.satellite(),
                AnalysisRequest.MAX_DAYS, AnalysisRequest.MAX_RADIUS_KM, series,
                FireRiskService.markInside(batch.fires(), feature.geometry()));
        return new Saved(store.save(snapshot), snapshot);
    }
}
