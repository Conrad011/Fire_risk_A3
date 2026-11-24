package com.firerisk.model;

import java.util.List;

/** Focos de uma consulta e o satelite que os detectou. */
public record FireBatch(String satellite, List<FireRecord> fires) {
}
