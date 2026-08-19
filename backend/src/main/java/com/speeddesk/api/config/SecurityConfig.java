package com.speeddesk.api.config;

import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.security.ProblemDetailAccessDeniedHandler;
import com.speeddesk.api.security.ProblemDetailAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users")
                        .hasAuthority(authority(UserRole.GERENTE))
                        .requestMatchers(HttpMethod.POST, "/api/users")
                        .hasAuthority(authority(UserRole.GERENTE))
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("SPEEDDESK_JWT_SECRET deve ser configurado.");
        }

        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "SPEEDDESK_JWT_SECRET deve possuir pelo menos 32 bytes."
            );
        }
        if (properties.expirationSeconds() <= 0) {
            throw new IllegalStateException(
                    "SPEEDDESK_JWT_EXPIRATION_SECONDS deve ser maior que zero."
            );
        }
        if (properties.issuer() == null || properties.issuer().isBlank()) {
            throw new IllegalStateException("O emissor JWT deve ser configurado.");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> standardValidators =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                standardValidators,
                this::validateRequiredClaims
        ));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix(ROLE_PREFIX);

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        authenticationConverter.setPrincipalClaimName("sub");
        return authenticationConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        List<String> allowedOrigins = properties.allowedOrigins() == null
                ? List.of()
                : properties.allowedOrigins().stream()
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList();

        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "SPEEDDESK_CORS_ALLOWED_ORIGINS deve ser configurado."
            );
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException("Origens CORS curinga não são permitidas.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private OAuth2TokenValidatorResult validateRequiredClaims(Jwt jwt) {
        try {
            UUID.fromString(jwt.getSubject());
            UserRole.valueOf(jwt.getClaimAsString("role"));
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                return invalidClaims();
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return invalidClaims();
        }
    }

    private OAuth2TokenValidatorResult invalidClaims() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "O token não contém as claims obrigatórias válidas.",
                null
        ));
    }

    private static String authority(UserRole role) {
        return ROLE_PREFIX + role.name();
    }
}
