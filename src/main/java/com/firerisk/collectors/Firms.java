package com.firerisk.collectors;

import com.firerisk.exception.ApiKeyException;
import com.firerisk.exception.UpstreamException;
import com.firerisk.model.FireBatch;
import com.firerisk.model.FireRecord;
import com.firerisk.util.Brasilia;
import com.firerisk.util.GeoMath;
import com.firerisk.util.Http;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coletor de focos de calor ativos da NASA FIRMS (sensores VIIRS).
 *
 * <p>A FIRMS registra data e hora em UTC. Cada foco e convertido para o horario de
 * Brasilia antes de ser agrupado por dia, para casar com a serie climatica.</p>
 */
@Component
public class Firms {

    private static final String SERVICE = "NASA FIRMS";
    private static final String API = "https://firms.modaps.eosdis.nasa.gov/api/";
    private static final int MAX_DATA_AGE_DAYS = 2;
    private static final int MAX_DAYS_PER_REQUEST = 5;

    private static final Map<String, String> SOURCES = sources();

    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

    private final String apiKey;

    public Firms(@Value("${firerisk.firms.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    private static Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("VIIRS_NOAA20_NRT", "VIIRS NOAA-20");
        sources.put("VIIRS_NOAA21_NRT", "VIIRS NOAA-21");
        sources.put("VIIRS_SNPP_NRT", "VIIRS S-NPP");
        return sources;
    }

    /** Focos dos ultimos {@code days} dias ate hoje, em um raio ao redor do ponto. */
    public FireBatch getFires(double lat, double lon, int days, int radiusKm) {
        return getFires(lat, lon, days, radiusKm, Brasilia.today());
    }

    /**
     * Focos dos {@code days} dias que terminam em {@code end} (horario de Brasilia).
     *
     * <p>A API so aceita retangulos; o filtro por distancia transforma a busca em um circulo.</p>
     */
    public FireBatch getFires(double lat, double lon, int days, int radiusKm, LocalDate end) {
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiKeyException.missing();
        }
        String source = activeSource();
        String bbox = GeoMath.boundingBox(lat, lon, radiusKm);
        LocalDate start = end.minusDays(days - 1L);

        // O dia em Brasilia vai das 03:00 UTC ate as 03:00 UTC do dia seguinte,
        // entao a consulta precisa de um dia UTC a mais no fim.
        LocalDate lastUtcDay = end.plusDays(1);
        List<FireRecord> raw = new ArrayList<>(fetch(source, bbox, start, Math.min(days + 1, MAX_DAYS_PER_REQUEST)));
        boolean lastDayMissing = days + 1 > MAX_DAYS_PER_REQUEST;
        if (lastDayMissing && !LocalDate.now(ZoneOffset.UTC).isBefore(lastUtcDay)) {
            raw.addAll(fetch(source, bbox, lastUtcDay, 1));
        }

        List<FireRecord> fires = raw.stream()
                .filter(f -> !f.acqDate().isBefore(start) && !f.acqDate().isAfter(end))
                .map(f -> f.withDistance(GeoMath.distanceKm(lat, lon, f.latitude(), f.longitude())))
                .filter(f -> f.distanceKm() <= radiusKm)
                .toList();
        return new FireBatch(SOURCES.get(source), fires);
    }

    private List<FireRecord> fetch(String source, String bbox, LocalDate firstUtcDay, int days) {
        String url = API + "area/csv/" + apiKey + "/" + source + "/" + bbox + "/" + days + "/" + firstUtcDay;
        HttpResponse<String> response = Http.get(SERVICE, url);
        if (response.statusCode() != 200) {
            throw isKeyError(response.body()) ? ApiKeyException.rejected() : new UpstreamException(SERVICE);
        }
        return parseCsv(response.body());
    }

    /**
     * Escolhe o primeiro satelite com dados recentes.
     *
     * <p>Um satelite pode parar de publicar e a API seguir respondendo 200 com zero linhas,
     * o que pareceria "nenhum foco".</p>
     */
    private String activeSource() {
        Map<String, LocalDate> available;
        try {
            available = parseAvailability(Http.getOrThrow(SERVICE, API + "data_availability/csv/" + apiKey + "/all"));
        } catch (UpstreamException e) {
            return SOURCES.keySet().iterator().next();
        }
        if (available.isEmpty()) {
            return SOURCES.keySet().iterator().next();
        }
        LocalDate limit = LocalDate.now(ZoneOffset.UTC).minusDays(MAX_DATA_AGE_DAYS);
        return SOURCES.keySet().stream()
                .filter(s -> available.containsKey(s) && !available.get(s).isBefore(limit))
                .findFirst()
                .orElseThrow(() -> new UpstreamException(SERVICE));
    }

    static Map<String, LocalDate> parseAvailability(String csv) {
        Map<String, LocalDate> available = new HashMap<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSV)) {
            if (!parser.getHeaderNames().contains("data_id") || !parser.getHeaderNames().contains("max_date")) {
                return Map.of();
            }
            for (CSVRecord row : parser) {
                available.put(row.get("data_id"), LocalDate.parse(row.get("max_date")));
            }
        } catch (IOException | IllegalArgumentException | DateTimeParseException e) {
            return Map.of();
        }
        return available;
    }

    /** A FIRMS pode responder 200 com um texto de erro no lugar do CSV. */
    static List<FireRecord> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<FireRecord> fires = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSV)) {
            if (!parser.getHeaderNames().contains("latitude")) {
                throw isKeyError(csv) ? ApiKeyException.rejected() : new UpstreamException(SERVICE);
            }
            for (CSVRecord row : parser) {
                LocalDate utcDate = LocalDate.parse(get(row, "acq_date"));
                LocalDateTime detectedAt = toBrasilia(utcDate, get(row, "acq_time"));
                fires.add(new FireRecord(
                        detectedAt == null ? utcDate : detectedAt.toLocalDate(),
                        detectedAt,
                        Double.parseDouble(get(row, "latitude")),
                        Double.parseDouble(get(row, "longitude")),
                        optionalDouble(row, "frp", 0.0),
                        get(row, "confidence"),
                        get(row, "daynight"),
                        optionalDouble(row, "bright_ti4", null),
                        0.0,
                        false));
            }
        } catch (IOException | IllegalArgumentException | NullPointerException | DateTimeParseException e) {
            throw new UpstreamException(SERVICE);
        }
        return fires;
    }

    /** {@code acq_time} vem como HHMM em UTC, as vezes sem o zero a esquerda ("412"). */
    private static LocalDateTime toBrasilia(LocalDate utcDate, String acqTime) {
        if (acqTime == null || !acqTime.strip().matches("\\d{1,4}")) {
            return null;
        }
        String hhmm = String.format("%4s", acqTime.strip()).replace(' ', '0');
        try {
            LocalTime time = LocalTime.of(Integer.parseInt(hhmm.substring(0, 2)), Integer.parseInt(hhmm.substring(2)));
            return Brasilia.fromUtc(utcDate, time);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static boolean isKeyError(String body) {
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return text.contains("invalid") && text.contains("key");
    }

    private static String get(CSVRecord row, String column) {
        return row.isMapped(column) ? row.get(column) : null;
    }

    private static Double optionalDouble(CSVRecord row, String column, Double fallback) {
        String value = get(row, column);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
