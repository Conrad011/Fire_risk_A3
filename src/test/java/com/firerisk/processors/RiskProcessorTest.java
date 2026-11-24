package com.firerisk.processors;

import com.firerisk.model.ClimateRecord;
import com.firerisk.model.DailyRisk;
import com.firerisk.model.FireRecord;
import com.firerisk.model.RiskLevel;
import com.firerisk.model.RiskResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskProcessorTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 1);

    @Test
    void combinaFocosComSerieClimatica() {
        List<ClimateRecord> climate = List.of(day(DAY_1, 30.0, 0.0), day(DAY_1.plusDays(1), 32.0, 10.0));
        List<FireRecord> fires = List.of(fire(DAY_1), fire(DAY_1), fire(DAY_1.plusDays(1)));

        List<DailyRisk> daily = RiskProcessor.process(climate, fires, "Amazônia").daily();

        assertEquals(2, daily.size());
        assertEquals(2, daily.get(0).totalFires());
        assertEquals(1, daily.get(0).hadFire());
        assertEquals(1, daily.get(1).totalFires());
    }

    @Test
    void diaSemFocoRecebeZero() {
        RiskResult result = RiskProcessor.process(List.of(day(DAY_1, 30.0, 0.0)), List.of(), "Cerrado");

        assertEquals(0, result.daily().get(0).totalFires());
        assertEquals(0, result.daily().get(0).hadFire());
        assertEquals(0, result.summary().totalFires());
        assertNull(result.summary().nearestFireKm());
    }

    @Test
    void calculaIndiceComPesoDoBioma() {
        // (1 * 0.6 + 1 * 0.4) * 1.5 * 10 = 15 -> limitado a 10
        RiskResult result = RiskProcessor.process(List.of(day(DAY_1, 40.0, 0.0)), manyFires(100), "Amazônia");

        assertEquals(10.0, result.summary().riskIndex());
        assertEquals(40.0, result.summary().avgTemp());
        assertEquals(0.0, result.summary().avgRain());
        assertEquals(RiskLevel.HIGH, result.summary().level());
    }

    @Test
    void biomaSemPesoUsaMultiplicadorNeutro() {
        // (0.5 * 0.6 + 0.25 * 0.4) * 1.0 * 10 = 4.0
        RiskResult result = RiskProcessor.process(List.of(day(DAY_1, 20.0, 25.0)), manyFires(50), "Pantanal");

        assertEquals(4.0, result.summary().riskIndex());
        assertEquals(RiskLevel.MODERATE, result.summary().level());
    }

    @Test
    void chuvaAcimaDoLimiteZeraOComponenteClimatico() {
        // fire_score = 0.1 ; climate_score = 0 -> 0.1 * 0.6 * 1.5 * 10 = 0.9
        RiskResult result = RiskProcessor.process(List.of(day(DAY_1, 35.0, 120.0)), manyFires(10), "Amazônia");

        assertEquals(0.9, result.summary().riskIndex());
        assertEquals(RiskLevel.LOW, result.summary().level());
    }

    @Test
    void indiceNuncaPassaDeDez() {
        RiskResult result = RiskProcessor.process(List.of(day(DAY_1, 45.0, 0.0)), manyFires(500), "Amazônia");

        assertTrue(result.summary().riskIndex() <= 10.0);
    }

    @Test
    void contaDiasSecosAPartirDeHoje() {
        List<ClimateRecord> climate = List.of(
                day(DAY_1, 30.0, 5.0), day(DAY_1.plusDays(1), 30.0, 0.9), day(DAY_1.plusDays(2), 30.0, 0.0));

        assertEquals(2, RiskProcessor.process(climate, List.of(), "Cerrado").summary().dryDays());
    }

    @Test
    void resumeFocosNoMunicipioEMaisProximo() {
        List<FireRecord> fires = List.of(
                fire(DAY_1).withDistance(40.5), fire(DAY_1).withDistance(7.2).withInside(true));

        RiskResult result = RiskProcessor.process(List.of(day(DAY_1, 30.0, 0.0)), fires, "Cerrado");

        assertEquals(1, result.summary().firesInMunicipality());
        assertEquals(7.2, result.summary().nearestFireKm());
    }

    @Test
    void focoDeBaixaConfiancaNaoEntraNoIndice() {
        List<FireRecord> fires = new ArrayList<>(manyFires(50));
        FireRecord low = new FireRecord(DAY_1, null, -3.0, -60.0, 1.0, "l", "D", null, 2.0, true);
        for (int i = 0; i < 50; i++) {
            fires.add(low);
        }

        RiskResult result = RiskProcessor.process(List.of(day(DAY_1, 20.0, 25.0)), fires, "Pantanal");

        // Mesmo indice do caso com 50 focos: os 50 de baixa confianca sao ignorados
        assertEquals(4.0, result.summary().riskIndex());
        assertEquals(50, result.summary().totalFires());
        assertEquals(50, result.summary().lowConfidenceFires());
        assertEquals(0, result.summary().firesInMunicipality());
        assertEquals(0.0, result.summary().nearestFireKm());
        assertEquals(50, result.daily().get(0).totalFires());
    }

    private static ClimateRecord day(LocalDate date, double temperature, double rain) {
        return new ClimateRecord(date, temperature, rain, 5.0, 40.0);
    }

    private static FireRecord fire(LocalDate date) {
        return new FireRecord(date, null, -3.0, -60.0, 12.5, "n", "D", 320.0, 0.0, false);
    }

    private static List<FireRecord> manyFires(int count) {
        List<FireRecord> fires = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            fires.add(fire(DAY_1));
        }
        return fires;
    }
}
