package com.firerisk.model;

import com.firerisk.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisRequestTest {

    @Test
    void normalizaCidadeEUf() {
        AnalysisRequest request = new AnalysisRequest("  Sete Lagoas ", "mg", 5, 100);

        assertEquals("Sete Lagoas", request.city());
        assertEquals("MG", request.uf());
    }

    @Test
    void rejeitaValoresForaDoLimite() {
        assertThrows(InvalidRequestException.class, () -> new AnalysisRequest(" ", "MG", 5, 100));
        assertThrows(InvalidRequestException.class, () -> new AnalysisRequest("Betim", "XX", 5, 100));
        assertThrows(InvalidRequestException.class, () -> new AnalysisRequest("Betim", "MG", 6, 100));
        assertThrows(InvalidRequestException.class, () -> new AnalysisRequest("Betim", "MG", 5, 501));
    }
}
