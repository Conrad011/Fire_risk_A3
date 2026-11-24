package com.firerisk.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caches em memoria. Cada tipo de dado expira no ritmo em que muda na fonte.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String GEO = "geo";
    public static final String LOCATION = "location";
    public static final String CLIMATE = "climate";
    public static final String FIRMS = "firms";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(GEO, Caffeine.newBuilder().build());
        manager.registerCustomCache(LOCATION, expiring(Duration.ofDays(1)));
        manager.registerCustomCache(CLIMATE, expiring(Duration.ofHours(1)));
        manager.registerCustomCache(FIRMS, expiring(Duration.ofMinutes(15)));
        return manager;
    }

    private Cache<Object, Object> expiring(Duration ttl) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(500).build();
    }
}
