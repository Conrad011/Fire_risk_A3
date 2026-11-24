package com.firerisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Locale;

/** Faixas do indice de risco: fonte unica para API, terminal e dashboard. */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum RiskLevel {
    LOW("Baixo", "Condições menos favoráveis ao fogo no momento."),
    MODERATE("Moderado", "Atenção: evite queimadas e acompanhe a evolução dos focos."),
    HIGH("Alto", "Condições críticas: não use fogo ao ar livre e fique atento às autoridades locais.");

    public static final double MODERATE_FROM = 3.0;
    public static final double HIGH_FROM = 6.0;

    private final String label;
    private final String meaning;

    RiskLevel(String label, String meaning) {
        this.label = label;
        this.meaning = meaning;
    }

    public static RiskLevel of(double index) {
        if (index >= HIGH_FROM) {
            return HIGH;
        }
        return index >= MODERATE_FROM ? MODERATE : LOW;
    }

    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getLabel() {
        return label;
    }

    public String getMeaning() {
        return meaning;
    }
}
