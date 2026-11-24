package com.firerisk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Carrega a malha ao subir o servidor, para a primeira analise nao esperar por ela. */
@Component
@ConditionalOnWebApplication
public class GeoPreloader {

    private static final Logger log = LoggerFactory.getLogger(GeoPreloader.class);

    private final DataCache cache;

    public GeoPreloader(DataCache cache) {
        this.cache = cache;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        CompletableFuture.runAsync(() -> {
            try {
                cache.loadGeo();
                log.info("Malha municipal carregada.");
            } catch (RuntimeException e) {
                log.warn("Malha nao carregada; nova tentativa na primeira analise: {}", e.getMessage());
            }
        });
    }
}
