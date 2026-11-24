package com.firerisk.model;

import java.util.List;

/**
 * Par (serie diaria, resumo) devolvido pelo processamento de risco.
 *
 * <p>Equivalente a tupla {@code (df, summary)} do codigo Python.</p>
 */
public record RiskResult(List<DailyRisk> daily, RiskSummary summary) {
}
