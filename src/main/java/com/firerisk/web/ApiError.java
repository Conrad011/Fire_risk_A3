package com.firerisk.web;

import java.util.List;

/**
 * Erro em linguagem simples para o dashboard.
 *
 * @param error       o que aconteceu
 * @param hint        como resolver
 * @param suggestions municipios com nome parecido, quando houver
 */
public record ApiError(String error, String hint, List<String> suggestions) {
}
