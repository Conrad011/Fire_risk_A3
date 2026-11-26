package com.firerisk.service;

import com.firerisk.exception.ApiKeyException;
import com.firerisk.exception.InvalidRequestException;
import com.firerisk.exception.MunicipalityNotFoundException;
import com.firerisk.exception.UpstreamException;
import com.firerisk.model.Analysis;
import com.firerisk.model.AnalysisRequest;
import com.firerisk.model.ClimateRecord;
import com.firerisk.model.DataSource;
import com.firerisk.model.FireBatch;
import com.firerisk.model.FireRecord;
import com.firerisk.model.GeoData;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.Municipality;
import com.firerisk.model.RiskResult;
import com.firerisk.model.Snapshot;
import com.firerisk.model.State;
import com.firerisk.processors.RiskProcessor;
import com.firerisk.util.Brasilia;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orquestra a analise completa: geografia, clima, focos e calculo de risco.
 */
@Service
public class FireRiskService {

    private static final Logger log = LoggerFactory.getLogger(FireRiskService.class);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int MAX_SUGGESTIONS = 5;

    private final DataCache cache;
    private final SnapshotStore snapshots;
    private final HistoryService history;

    public FireRiskService(DataCache cache, SnapshotStore snapshots, HistoryService history) {
        this.cache = cache;
        this.snapshots = snapshots;
        this.history = history;
    }

    /**
     * Executa a analise para o municipio e filtros informados.
     *
     * <p>Se clima ou focos nao puderem ser obtidos e houver dados salvos do municipio,
     * a analise usa esses dados e informa isso em {@link DataSource}. Toda analise e
     * gravada no historico.</p>
     *
     * @throws MunicipalityNotFoundException se o nome nao existir na UF
     */
    public Analysis analyze(AnalysisRequest request) {
        GeoData geoData = cache.loadGeo();
        State state = State.valueOf(request.uf());

        GeoFeature feature = geoData.findByName(request.city(), request.uf())
                .orElseThrow(() -> new MunicipalityNotFoundException(request.city(), state.getLabel(),
                        geoData.suggest(request.city(), request.uf(), MAX_SUGGESTIONS)));

        Municipality municipality = cache.locate(feature);
        List<ClimateRecord> climate;
        FireBatch batch;
        DataSource source;
        try {
            climate = cache.loadClimate(municipality.lat(), municipality.lon());
            batch = cache.loadFires(municipality.lat(), municipality.lon(), request.days(), request.radiusKm());
            source = DataSource.live();
        } catch (UpstreamException | ApiKeyException e) {
            Snapshot saved = snapshots.load(feature).orElseThrow(() -> e);
            log.warn("APIs indisponiveis ({}); usando dados salvos de {}", e.getMessage(), saved.referenceDate());
            municipality = saved.municipality();
            climate = saved.climate();
            batch = new FireBatch(saved.satellite(), saved.firesWithin(request.days(), request.radiusKm()));
            source = DataSource.snapshot(saved);
        }

        List<FireRecord> fires = markInside(batch.fires(), feature.geometry());
        RiskResult result = RiskProcessor.process(climate, fires, state.getBiome());

        Analysis analysis = new Analysis(request.withCity(feature.nome()), municipality, feature.geometry(),
                batch.satellite(), source, climate, fires, result, Brasilia.now());
        history.record(analysis);
        return analysis;
    }

    /** Nomes oficiais dos municipios da UF, para o autocompletar. */
    public List<String> municipalities(String uf) {
        State state = State.fromCode(uf)
                .orElseThrow(() -> new InvalidRequestException("Estado inválido. Use a sigla da UF, como MG."));
        return cache.loadGeo().namesIn(state.name());
    }

    public void refresh() {
        cache.refresh();
    }

    static List<FireRecord> markInside(List<FireRecord> fires, Geometry municipality) {
        PreparedGeometry polygon = PreparedGeometryFactory.prepare(municipality);
        return fires.stream()
                .map(f -> f.withInside(polygon.contains(
                        GEOMETRY_FACTORY.createPoint(new Coordinate(f.longitude(), f.latitude())))))
                .toList();
    }
}
