package com.firerisk.model;

/**
 * Municipio analisado.
 *
 * @param codarea     codigo IBGE do municipio
 * @param nome        nome oficial
 * @param uf          sigla da unidade federativa
 * @param lat         latitude do ponto analisado
 * @param lon         longitude do ponto analisado
 * @param urbanCenter {@code true} se o ponto e a sede urbana; {@code false} se e um ponto interno aproximado
 */
public record Municipality(String codarea, String nome, String uf, double lat, double lon, boolean urbanCenter) {
}
