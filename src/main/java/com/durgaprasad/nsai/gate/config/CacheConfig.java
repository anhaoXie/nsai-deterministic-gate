package com.durgaprasad.nsai.gate.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.durgaprasad.nsai.graph.GraphRuleLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Caches the rules loaded from Neo4j per intent. Entries expire after
     * {@code nsai.gateway.graph.cache-ttl-seconds} (default 600), so rule changes in the
     * graph are picked up without a restart.
     */
    @Bean
    public CacheManager cacheManager(@Value("${nsai.gateway.graph.cache-ttl-seconds:600}") long ttlSeconds) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(GraphRuleLoader.CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .recordStats()); // hit/miss counters
        return cacheManager;
    }
}