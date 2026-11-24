package com.firerisk.collectors;

import com.firerisk.exception.ApiKeyException;
import com.firerisk.exception.UpstreamException;
import com.firerisk.model.FireRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirmsTest {

    @Test
    void leFocosDoCsv() {
        String csv = "latitude,longitude,bright_ti4,acq_date,confidence,frp,daynight\n"
                + "-19.5,-44.2,330.1,2026-11-20,n,4.5,D\n";

        List<FireRecord> fires = Firms.parseCsv(csv);

        assertEquals(1, fires.size());
        assertEquals(LocalDate.of(2026, 11, 20), fires.get(0).acqDate());
        assertEquals(4.5, fires.get(0).frp());
    }

    @Test
    void converteHorarioUtcParaBrasilia() {
        String csv = "latitude,longitude,acq_date,acq_time,frp\n"
                + "-19.5,-44.2,2026-11-21,0130,1.0\n"
                + "-19.5,-44.2,2026-11-21,412,1.0\n";

        List<FireRecord> fires = Firms.parseCsv(csv);

        // 01:30 UTC de 21/11 ainda e 22:30 de 20/11 em Brasilia
        assertEquals(LocalDate.of(2026, 11, 20), fires.get(0).acqDate());
        assertEquals(LocalDateTime.of(2026, 11, 20, 22, 30), fires.get(0).detectedAt());
        // "412" e 04:12 UTC, 01:12 em Brasilia
        assertEquals(LocalDateTime.of(2026, 11, 21, 1, 12), fires.get(1).detectedAt());
    }

    @Test
    void semHorarioMantemADataInformada() {
        FireRecord fire = Firms.parseCsv("latitude,longitude,acq_date\n-19.5,-44.2,2026-11-21\n").get(0);

        assertEquals(LocalDate.of(2026, 11, 21), fire.acqDate());
        assertNull(fire.detectedAt());
    }

    @Test
    void respostaSoComCabecalhoNaoTemFocos() {
        assertTrue(Firms.parseCsv("latitude,longitude,acq_date\n").isEmpty());
        assertTrue(Firms.parseCsv("").isEmpty());
    }

    @Test
    void textoDeChaveInvalidaViraErroDeChave() {
        assertThrows(ApiKeyException.class, () -> Firms.parseCsv("Invalid MAP_KEY."));
    }

    @Test
    void textoInesperadoViraErroDeServico() {
        assertThrows(UpstreamException.class, () -> Firms.parseCsv("Service unavailable"));
    }

    @Test
    void leDisponibilidadeDosSatelites() {
        var available = Firms.parseAvailability(
                "data_id,min_date,max_date\nVIIRS_SNPP_NRT,2026-09-01,2026-11-02\n");

        assertEquals(LocalDate.of(2026, 11, 2), available.get("VIIRS_SNPP_NRT"));
        assertFalse(available.containsKey("VIIRS_NOAA20_NRT"));
        assertTrue(Firms.parseAvailability("erro").isEmpty());
    }

    @Test
    void erroDeServicoNaoExpoeAChave() {
        assertFalse(new UpstreamException("NASA FIRMS").getMessage().contains("http"));
    }
}
