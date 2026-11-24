package com.firerisk.model;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoDataTest {

    private final GeoData geo = new GeoData(List.of(
            feature("1", "Sete Lagoas", "MG"),
            feature("2", "São João del Rei", "MG"),
            feature("3", "Belo Horizonte", "MG"),
            feature("4", "Sete Barras", "SP")));

    @Test
    void encontraIgnorandoAcentoMaiusculaEHifen() {
        assertEquals("2", geo.findByName("SAO JOAO DEL-REI", "MG").orElseThrow().codarea());
    }

    @Test
    void naoEncontraEmOutroEstado() {
        assertFalse(geo.findByName("Sete Lagoas", "SP").isPresent());
    }

    @Test
    void sugereNomeParecido() {
        assertEquals("Sete Lagoas", geo.suggest("Sete Lagos", "MG", 5).get(0));
        assertTrue(geo.suggest("xyzw", "MG", 5).isEmpty());
    }

    @Test
    void listaEmOrdemAlfabetica() {
        assertEquals(List.of("Belo Horizonte", "São João del Rei", "Sete Lagoas"), geo.namesIn("MG"));
    }

    private static GeoFeature feature(String code, String name, String uf) {
        return new GeoFeature(code, name, uf, new GeometryFactory().createPoint(new Coordinate(0, 0)));
    }
}
