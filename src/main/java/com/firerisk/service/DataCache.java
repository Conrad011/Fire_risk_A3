package com.firerisk.service;

import com.firerisk.collectors.Climate;
import com.firerisk.collectors.Firms;
import com.firerisk.collectors.Geocoder;
import com.firerisk.collectors.Vegetation;
import com.firerisk.config.CacheConfig;
import com.firerisk.exception.UpstreamException;
import com.firerisk.model.ClimateRecord;
import com.firerisk.model.FireBatch;
import com.firerisk.model.GeoData;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.Municipality;
import com.firerisk.model.MunicipalityName;
import com.firerisk.model.Snapshot;
import org.locationtech.jts.geom.Point;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Camada de cache em memoria sobre os coletores.
 */
@Component
public class DataCache {

    private final Vegetation vegetation;
    private final Climate climate;
    private final Firms firms;
    private final Geocoder geocoder;
    private final SnapshotStore snapshots;

    public DataCache(Vegetation vegetation, Climate climate, Firms firms, Geocoder geocoder, SnapshotStore snapshots) {
        this.vegetation = vegetation;
        this.climate = climate;
        this.firms = firms;
        this.geocoder = geocoder;
        this.snapshots = snapshots;
    }

    /** Sem a API de localidades do IBGE, usa os nomes salvos junto com os dados de contingencia. */
    @Cacheable(cacheNames = CacheConfig.GEO, sync = true)
    public GeoData loadGeo() {
        Map<String, MunicipalityName> names;
        try {
            names = vegetation.fetchMunicipalityNames();
        } catch (UpstreamException e) {
            names = snapshots.loadNames().orElseThrow(() -> e);
        }
        return vegetation.readGeoData(names);
    }

    /**
     * Usa a sede urbana. Sem o geocoder, prefere o ponto dos dados salvos e, por ultimo,
     * um ponto garantidamente dentro do municipio.
     */
    @Cacheable(cacheNames = CacheConfig.LOCATION, key = "#feature.codarea()")
    public Municipality locate(GeoFeature feature) {
        Optional<Point> center = geocoder.findUrbanCenter(feature.nome(), feature.geometry());
        if (center.isPresent()) {
            return toMunicipality(feature, center.get(), true);
        }
        return snapshots.load(feature)
                .map(Snapshot::municipality)
                .orElseGet(() -> toMunicipality(feature, feature.geometry().getInteriorPoint(), false));
    }

    @Cacheable(CacheConfig.CLIMATE)
    public List<ClimateRecord> loadClimate(double lat, double lon) {
        return climate.getData(lat, lon);
    }

    @Cacheable(CacheConfig.FIRMS)
    public FireBatch loadFires(double lat, double lon, int days, int radiusKm) {
        return firms.getFires(lat, lon, days, radiusKm);
    }

    /** Descarta clima e focos para a proxima analise buscar dados novos. */
    @CacheEvict(cacheNames = {CacheConfig.CLIMATE, CacheConfig.FIRMS}, allEntries = true)
    public void refresh() {
    }

    private Municipality toMunicipality(GeoFeature feature, Point point, boolean urbanCenter) {
        return new Municipality(feature.codarea(), feature.nome(), feature.uf(),
                Math.round(point.getY() * 10000) / 10000.0,
                Math.round(point.getX() * 10000) / 10000.0,
                urbanCenter);
    }
}
