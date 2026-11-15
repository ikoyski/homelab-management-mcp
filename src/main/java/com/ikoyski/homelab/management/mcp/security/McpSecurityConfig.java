package com.ikoyski.homelab.management.mcp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class McpSecurityConfig {

    @Bean
    public SecurityFilterChain mcpSecurityFilterChain(
            HttpSecurity http,
            AllowedUserAuthorizationService allowedUsers) throws Exception {

        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/mcp"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/mcp").access((authentication, context) ->
                                authorizeAllowlistedIdentity(authentication.get(), allowedUsers))
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public JwtDecoder homelabJwtDecoder(
            @Value("${homelab.security.issuer}") String issuer,
            @Value("${homelab.security.jwk-set-uri}") String jwkSetUri,
            @Value("${homelab.security.audience}") String audience) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> issuerAndTimeValidator =
                JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required MCP audience is missing", null));

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerAndTimeValidator, audienceValidator));
        return decoder;
    }

    private static AuthorizationDecision authorizeAllowlistedIdentity(
            Authentication authentication,
            AllowedUserAuthorizationService allowedUsers) {

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return new AuthorizationDecision(false);
        }

        // Webtools Auth places its stable central identity UUID in "uid", not "sub".
        String centralUserId = jwtAuthentication.getToken().getClaimAsString("uid");
        return new AuthorizationDecision(allowedUsers.isAllowed(centralUserId));
    }
}
