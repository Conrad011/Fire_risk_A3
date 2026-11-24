package com.firerisk.model;

/** Nome e UF de um municipio, vindos da API de localidades do IBGE. */
public record MunicipalityName(String nome, String uf) {
}
