package com.myexampleproject.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // ✅ Cache token đã decode (token-level)
    private final Map<String, Mono<Jwt>> tokenCache = new ConcurrentHashMap<>();

    // ✅ Cache decoder-level (key từ Keycloak)
    private final Map<String, CachedDecoder> jwkDecoderCache = new ConcurrentHashMap<>();

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return token -> {
            // 👉 Key cho cache (Keycloak URI)
            String jwkUri = "http://keycloak:8085/realms/spring-boot-microservices-realm/protocol/openid-connect/certs";

            ReactiveJwtDecoder decoder = getCachedDecoder(jwkUri);

            // 👉 Cache từng token decode 10 phút
            return tokenCache.computeIfAbsent(token,
                    t -> Mono.defer(() -> decoder.decode(t))
                            .cache(Duration.ofMinutes(10))
            );
        };
    }

    // ✅ Cấu hình Security chain
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/eureka/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    // ✅ Cache JWK decoders (manual version of `.cache(Duration)`)
    private ReactiveJwtDecoder getCachedDecoder(String jwkUri) {
        CachedDecoder cached = jwkDecoderCache.get(jwkUri);
        if (cached != null && cached.isValid()) {
            return cached.decoder;
        }

        ReactiveJwtDecoder newDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkUri).build();
        jwkDecoderCache.put(jwkUri, new CachedDecoder(newDecoder, Instant.now()));
        return newDecoder;
    }

    // ✅ Class phụ để giữ decoder và timestamp
    private static class CachedDecoder {
        private final ReactiveJwtDecoder decoder;
        private final Instant createdAt;

        CachedDecoder(ReactiveJwtDecoder decoder, Instant createdAt) {
            this.decoder = decoder;
            this.createdAt = createdAt;
        }

        boolean isValid() {
            // Cache JWK 1 tiếng
            return Duration.between(createdAt, Instant.now()).compareTo(Duration.ofHours(1)) < 0;
        }
    }
}
