package com.firerisk.model;

import org.locationtech.jts.geom.Geometry;

/**
 * Um municipio da malha do IBGE, ja enriquecido com nome e UF.
 */
public record GeoFeature(String codarea, String nome, String uf, Geometry geometry) {
}
