package com.firerisk.processors;

import com.firerisk.model.ClimateRecord;
import com.firerisk.model.DailyRisk;
import com.firerisk.model.FireRecord;
import com.firerisk.model.RiskLevel;
import com.firerisk.model.RiskResult;
import com.firerisk.model.RiskSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Cruza a serie climatica com os focos de calor e calcula o indice de risco 0-10.
 *
 * <pre>
 * fire_score    = clamp(qtd_focos / 100, 0, 1)
 * climate_score = clamp(temp_media / 40, 0, 1) * clamp(1 - chuva_media / 50, 0, 1)
 * risk_index    = (fire_score * 0.6 + climate_score * 0.4) * peso_bioma * 10
 * </pre>
 *
 * <p>{@code qtd_focos} ignora as deteccoes de baixa confianca ({@link FireRecord#lowConfidence()}).</p>
 */
public final class RiskProcessor {

    /** Multiplicador de risco por bioma; biomas nao listados valem 1.0. */
    public static final Map<String, Double> BIOME_WEIGHT = Map.of(
            "Amazônia", 1.5,
            "Cerrado", 1.2,
            "Caatinga", 1.3);

    /** Abaixo disso o dia conta como sem chuva. */
    private static final double DRY_DAY_MM = 1.0;

    private static final double MAX_FIRES_SATURATION = 100.0;
    private static final double MAX_TEMP_C = 40.0;
    private static final double MAX_RAIN_MM = 50.0;
    private static final double FIRE_WEIGHT = 0.6;
    private static final double CLIMATE_WEIGHT = 0.4;
    private static final double MAX_RISK_INDEX = 10.0;

    private RiskProcessor() {
    }

    /** Devolve a serie diaria combinada e o resumo agregado da analise. */
    public static RiskResult process(List<ClimateRecord> climate, List<FireRecord> allFires, String biome) {
        List<FireRecord> fires = allFires.stream().filter(f -> !f.lowConfidence()).toList();

        Map<LocalDate, Long> firesPerDay = fires.stream()
                .collect(Collectors.groupingBy(FireRecord::acqDate, Collectors.counting()));

        List<DailyRisk> daily = new ArrayList<>(climate.size());
        for (ClimateRecord record : climate) {
            long totalFires = firesPerDay.getOrDefault(record.date(), 0L);
            daily.add(new DailyRisk(
                    record.date(),
                    record.temperatureMax(),
                    record.precipitation(),
                    record.windspeed(),
                    record.humidityMin(),
                    totalFires,
                    totalFires > 0 ? 1 : 0));
        }

        double avgTemp = values(daily, DailyRisk::temperatureMax).average().orElse(Double.NaN);
        double avgRain = values(daily, DailyRisk::precipitation).average().orElse(Double.NaN);
        double weight = BIOME_WEIGHT.getOrDefault(biome, 1.0);

        double fireScore = Math.min(fires.size() / MAX_FIRES_SATURATION, 1.0);
        double climateScore = Math.min(avgTemp / MAX_TEMP_C, 1.0) * Math.max(1 - avgRain / MAX_RAIN_MM, 0.0);
        double riskIndex = Math.min(
                round((fireScore * FIRE_WEIGHT + climateScore * CLIMATE_WEIGHT) * weight * MAX_RISK_INDEX, 2),
                MAX_RISK_INDEX);

        RiskSummary summary = new RiskSummary(
                biome,
                weight,
                fires.size(),
                allFires.size() - fires.size(),
                (int) fires.stream().filter(FireRecord::insideMunicipality).count(),
                boxed(fires.stream().mapToDouble(FireRecord::distanceKm).min()),
                round(avgTemp, 1),
                round(avgRain, 1),
                boxed(values(daily, DailyRisk::windspeed).max()),
                boxed(values(daily, DailyRisk::humidityMin).min()),
                dryStreak(daily),
                round(fireScore, 3),
                round(climateScore, 3),
                riskIndex,
                RiskLevel.of(riskIndex));

        return new RiskResult(daily, summary);
    }

    /** Dias consecutivos sem chuva, contando de hoje para tras. */
    private static int dryStreak(List<DailyRisk> daily) {
        int streak = 0;
        for (int i = daily.size() - 1; i >= 0; i--) {
            Double rain = daily.get(i).precipitation();
            if (rain != null && rain >= DRY_DAY_MM) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private static DoubleStream values(List<DailyRisk> daily, Function<DailyRisk, Double> field) {
        return daily.stream().map(field).filter(Objects::nonNull).mapToDouble(Double::doubleValue);
    }

    private static Double boxed(OptionalDouble value) {
        return value.isPresent() ? round(value.getAsDouble(), 1) : null;
    }

    private static double round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
