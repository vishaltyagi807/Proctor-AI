package dev.varshit.proctor.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.security.jwt.JwtProperties;
import dev.varshit.proctor.security.jwt.JwtTokenService;
import dev.varshit.proctor.security.jwt.TokenService;
import dev.varshit.proctor.security.password.BcryptPasswordHasher;
import dev.varshit.proctor.security.password.PasswordHasher;
import dev.varshit.proctor.security.web.GlobalExceptionHandler;
import dev.varshit.proctor.security.web.JsonSecurityHandlers;
import dev.varshit.proctor.security.web.JwtAuthenticationWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.List;

@AutoConfiguration
@EnableWebFluxSecurity
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
@Import(GlobalExceptionHandler.class)
public class SecurityAutoConfiguration {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenService tokenService(JwtProperties properties, Clock clock) {
        return new JwtTokenService(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher passwordHasher(PasswordEncoder encoder) {
        return new BcryptPasswordHasher(encoder);
    }

    @Bean
    public JsonSecurityHandlers jsonSecurityHandlers(ObjectMapper mapper) {
        return new JsonSecurityHandlers(mapper);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            TokenService tokenService,
            JsonSecurityHandlers handlers,
            SecurityProperties properties
    ) {
        String[] publicPaths = properties.publicPaths().toArray(String[]::new);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(cors -> {
                })
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers(publicPaths.length == 0 ? new String[]{"/__none__"} : publicPaths).permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(new JwtAuthenticationWebFilter(tokenService), SecurityWebFiltersOrder.AUTHENTICATION)
                .headers(h -> h.contentTypeOptions(c -> {
                }))
                .build();
    }
}
