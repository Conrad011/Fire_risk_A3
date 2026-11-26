package com.firerisk.model;

import java.util.List;

/**
 * Par (serie diaria, resumo) devolvido pelo processamento de risco.
 */
public record RiskResult(List<DailyRisk> daily, RiskSummary summary) {
}
