package com.firerisk.collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firerisk.exception.UpstreamException;
import com.firerisk.model.ClimateRecord;
import com.firerisk.util.Http;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Coletor da serie climatica diaria do Open-Meteo, no horario de Brasilia.
 */
@Component
public class Climate {

    private static final String SERVICE = "Open-Meteo";
    private static final int PAST_DAYS = 30;

    private final ObjectMapper mapper;

    public Climate(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Ultimos 30 dias ate hoje; {@code forecast_days=1} evita que dias de
     * previsao entrem nas medias do indice.
     */
    public List<ClimateRecord> getData(double lat, double lon) {
        return fetch(lat, lon, "&past_days=" + PAST_DAYS + "&forecast_days=1");
    }

    /** Os 30 dias anteriores a {@code end}, mais o proprio {@code end}. */
    public List<ClimateRecord> getDataUntil(double lat, double lon, LocalDate end) {
        return getDataBetween(lat, lon, end.minusDays(PAST_DAYS), end);
    }

    /** Serie diaria de {@code start} a {@code end}, inclusive. */
    public List<ClimateRecord> getDataBetween(double lat, double lon, LocalDate start, LocalDate end) {
        return fetch(lat, lon, "&start_date=" + start + "&end_date=" + end);
    }

    private List<ClimateRecord> fetch(double lat, double lon, String period) {
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=%s&longitude=%s"
                        + "&daily=temperature_2m_max,precipitation_sum,windspeed_10m_max,relative_humidity_2m_min"
                        + "&timezone=America/Sao_Paulo%s",
                lat, lon, period);

        String body = Http.getOrThrow(SERVICE, url);
        try {
            JsonNode daily = mapper.readTree(body).get("daily");
            if (daily == null || daily.get("time") == null) {
                throw new UpstreamException(SERVICE);
            }
            List<ClimateRecord> records = toRecords(daily);
            boolean usable = records.stream().anyMatch(r -> r.temperatureMax() != null)
                    && records.stream().anyMatch(r -> r.precipitation() != null);
            if (!usable) {
                throw new UpstreamException(SERVICE);
            }
            return records;
        } catch (JsonProcessingException e) {
            throw new UpstreamException(SERVICE);
        }
    }

    /** Converte os arrays paralelos do Open-Meteo em uma lista de registros. */
    private List<ClimateRecord> toRecords(JsonNode daily) {
        JsonNode times = daily.get("time");
        List<ClimateRecord> records = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            records.add(new ClimateRecord(
                    LocalDate.parse(times.get(i).asText()),
                    nullableDouble(daily.get("temperature_2m_max"), i),
                    nullableDouble(daily.get("precipitation_sum"), i),
                    nullableDouble(daily.get("windspeed_10m_max"), i),
                    nullableDouble(daily.get("relative_humidity_2m_min"), i)));
        }
        return records;
    }

    private Double nullableDouble(JsonNode array, int index) {
        if (array == null || index >= array.size()) {
            return null;
        }
        JsonNode value = array.get(index);
        return value == null || value.isNull() ? null : value.asDouble();
    }
}
