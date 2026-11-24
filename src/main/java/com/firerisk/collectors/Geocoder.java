package com.firerisk.collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firerisk.exception.UpstreamException;
import com.firerisk.util.Http;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Encontra a sede urbana de um municipio pelo geocoder do Open-Meteo (sem chave).
 *
 * <p>So aceita pontos dentro do poligono do IBGE, o que descarta cidades homonimas de outros estados.</p>
 */
@Component
public class Geocoder {

    private static final String SERVICE = "Open-Meteo Geocoding";

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    /** Devolve vazio se o servico falhar ou nao houver resultado dentro do municipio. */
    public Optional<Point> findUrbanCenter(String name, Geometry polygon) {
        String url = "https://geocoding-api.open-meteo.com/v1/search?countryCode=BR&language=pt&count=20&name="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);
        try {
            for (JsonNode result : mapper.readTree(Http.getOrThrow(SERVICE, url)).path("results")) {
                Point point = geometryFactory.createPoint(
                        new Coordinate(result.path("longitude").asDouble(), result.path("latitude").asDouble()));
                if (polygon.contains(point)) {
                    return Optional.of(point);
                }
            }
        } catch (UpstreamException | JsonProcessingException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
