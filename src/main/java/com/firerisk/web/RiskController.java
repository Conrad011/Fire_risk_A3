package com.firerisk.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firerisk.model.Analysis;
import com.firerisk.model.AnalysisRequest;
import com.firerisk.model.HistoryEntry;
import com.firerisk.model.State;
import com.firerisk.service.FireRiskService;
import com.firerisk.service.HistoryService;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * API consumida pelo dashboard.
 */
@RestController
@RequestMapping("/api")
public class RiskController {

    private final FireRiskService service;
    private final HistoryService history;
    private final ObjectMapper mapper;

    public RiskController(FireRiskService service, HistoryService history, ObjectMapper mapper) {
        this.service = service;
        this.history = history;
        this.mapper = mapper;
    }

    @GetMapping("/states")
    public List<State> states() {
        return List.of(State.values());
    }

    @GetMapping("/municipalities")
    public List<String> municipalities(@RequestParam String uf) {
        return service.municipalities(uf);
    }

    /**
     * Analisa o risco de incendio de um municipio.
     *
     * @param days   dias de historico de focos (1 a 5)
     * @param radius raio de busca dos focos, em km (25 a 500)
     */
    @GetMapping("/risk")
    public AnalysisResponse risk(
            @RequestParam String city,
            @RequestParam String uf,
            @RequestParam(defaultValue = "5") int days,
            @RequestParam(defaultValue = "100") int radius) {

        Analysis analysis = service.analyze(new AnalysisRequest(city, uf, days, radius));
        return AnalysisResponse.of(analysis, toGeoJson(analysis.geometry()));
    }

    /** Analises gravadas no banco para o municipio, da mais recente para a mais antiga. */
    @GetMapping("/history")
    public List<HistoryEntry> history(@RequestParam String city, @RequestParam String uf) {
        return history.history(city, uf);
    }

    /** Descarta clima e focos em cache. */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        service.refresh();
        return ResponseEntity.noContent().build();
    }

    private JsonNode toGeoJson(Geometry geometry) {
        GeoJsonWriter writer = new GeoJsonWriter();
        writer.setEncodeCRS(false);
        try {
            return mapper.readTree(writer.write(geometry));
        } catch (IOException e) {
            return null;
        }
    }
}
