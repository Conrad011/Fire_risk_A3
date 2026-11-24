package com.firerisk.web;

import com.firerisk.exception.MunicipalityNotFoundException;
import com.firerisk.exception.UpstreamException;
import com.firerisk.service.FireRiskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskController.class)
class RiskControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FireRiskService service;

    @Test
    void listaOs27Estados() throws Exception {
        mvc.perform(get("/api/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(27))
                .andExpect(jsonPath("$[10].code").value("MG"))
                .andExpect(jsonPath("$[10].label").value("Minas Gerais"));
    }

    @Test
    void cidadeInexistenteTrazSugestoes() throws Exception {
        when(service.analyze(any())).thenThrow(
                new MunicipalityNotFoundException("Sete Lagos", "Minas Gerais", List.of("Sete Lagoas")));

        mvc.perform(get("/api/risk").param("city", "Sete Lagos").param("uf", "MG"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.suggestions[0]").value("Sete Lagoas"));
    }

    @Test
    void filtroForaDoLimiteRetorna400() throws Exception {
        mvc.perform(get("/api/risk").param("city", "Betim").param("uf", "MG").param("days", "99"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/risk").param("city", "Betim").param("uf", "MG").param("radius", "abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/risk").param("uf", "MG"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void falhaExternaNaoExpoeUrl() throws Exception {
        when(service.analyze(any())).thenThrow(new UpstreamException("NASA FIRMS"));

        mvc.perform(get("/api/risk").param("city", "Betim").param("uf", "MG"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("http"))));
    }

    @Test
    void erroInesperadoNaoExpoeDetalhes() throws Exception {
        when(service.analyze(any())).thenThrow(new IllegalStateException("segredo-interno"));

        mvc.perform(get("/api/risk").param("city", "Betim").param("uf", "MG"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("segredo-interno"))));
    }
}
