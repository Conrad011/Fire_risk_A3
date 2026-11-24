package com.firerisk.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Horario de Brasilia, usado em toda data exibida ao usuario.
 *
 * <p>A NASA FIRMS informa data e hora em UTC; o clima ja vem no fuso de Sao Paulo.</p>
 */
public final class Brasilia {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private Brasilia() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZONE);
    }

    /** Converte data e hora UTC para o horario de Brasilia. */
    public static LocalDateTime fromUtc(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE).toLocalDateTime();
    }
}
