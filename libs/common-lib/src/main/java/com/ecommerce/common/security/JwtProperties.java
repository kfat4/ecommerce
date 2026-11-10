package com.ecommerce.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * @param secret      HMAC-SHA256 key shared by the auth-service (issuer) and every resource server. Min 32 bytes.
 *                    Production: prefer RS256/ES256 with a JWKS endpoint so only the issuer holds the private key (ADR-0006).
 * @param publicPaths request patterns that do not need a token
 */
@ConfigurationProperties(prefix = "ecommerce.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, List<String> publicPaths) {

    public JwtProperties {
        issuer = issuer == null ? "ecommerce-auth" : issuer;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(30) : accessTokenTtl;
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
