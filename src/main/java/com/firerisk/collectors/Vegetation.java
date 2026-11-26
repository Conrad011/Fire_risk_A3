package com.firerisk.collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firerisk.exception.UpstreamException;
import com.firerisk.model.GeoData;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.MunicipalityName;
import com.firerisk.util.Http;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coletor da malha municipal do IBGE.
 *
 * <p>A malha e baixada uma unica vez e mantida em disco; nome e UF
 * vem da API de localidades do IBGE.</p>
 */
@Component
public class Vegetation {

    private static final String SERVICE = "IBGE";

    private static final String MALHAS_URL =
            "https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR"
                    + "?formato=application/vnd.geo+json&qualidade=minima&intrarregiao=municipio";

    private static final String MUNICIPIOS_URL =
            "https://servicodados.ibge.gov.br/api/v1/localidades/municipios";

    private final ObjectMapper mapper;
    private final Path geojsonPath;

    public Vegetation(
            @Value("${firerisk.geojson-path:data/raw/municipios.geojson}") String geojsonPath,
            ObjectMapper mapper) {
        this.geojsonPath = Path.of(geojsonPath);
        this.mapper = mapper;
    }

    /** Baixa a malha municipal do IBGE e grava em {@code firerisk.geojson-path}. */
    public void download() {
        String body = Http.getOrThrow(SERVICE, MALHAS_URL);
        try {
            Path parent = geojsonPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(geojsonPath, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar a malha em " + geojsonPath, e);
        }
    }

    /** Le a malha, baixando-a se preciso, e a enriquece com nome e UF. */
    public GeoData readGeoData(Map<String, MunicipalityName> namesByCodarea) {
        if (!Files.exists(geojsonPath)) {
            download();
        }

        String geojson;
        try {
            geojson = Files.readString(geojsonPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler a malha em " + geojsonPath, e);
        }

        return new GeoData(parseFeatures(geojson, namesByCodarea));
    }

    /** Nome e UF de cada municipio, indexados pelo codigo do IBGE. */
    public Map<String, MunicipalityName> fetchMunicipalityNames() {
        String body = Http.getOrThrow(SERVICE, MUNICIPIOS_URL);
        Map<String, MunicipalityName> names = new HashMap<>();
        try {
            for (JsonNode municipality : mapper.readTree(body)) {
                String uf = municipality
                        .path("regiao-imediata")
                        .path("regiao-intermediaria")
                        .path("UF")
                        .path("sigla")
                        .asText(null);
                names.put(municipality.get("id").asText(), new MunicipalityName(municipality.get("nome").asText(), uf));
            }
        } catch (JsonProcessingException e) {
            throw new UpstreamException(SERVICE);
        }
        return names;
    }

    private List<GeoFeature> parseFeatures(String geojson, Map<String, MunicipalityName> namesByCodarea) {
        GeoJsonReader reader = new GeoJsonReader(new GeometryFactory());
        List<GeoFeature> features = new ArrayList<>();
        try {
            for (JsonNode feature : mapper.readTree(geojson).path("features")) {
                String codarea = feature.path("properties").path("codarea").asText(null);
                if (codarea == null) {
                    continue;
                }
                Geometry geometry = reader.read(feature.get("geometry").toString());
                MunicipalityName name = namesByCodarea.getOrDefault(codarea, new MunicipalityName(null, null));
                features.add(new GeoFeature(codarea, name.nome(), name.uf(), geometry));
            }
        } catch (JsonProcessingException | ParseException e) {
            throw new IllegalStateException("Malha do IBGE em formato invalido: " + geojsonPath, e);
        }
        return features;
    }
}
