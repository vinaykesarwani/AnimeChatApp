package com.anime.backend.service;

import com.anime.backend.config.CorsConfig;
import com.anime.backend.config.JwtAuthFilter;
import com.anime.backend.config.OAuthSuccessHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfig corsConfig;
    private final JwtAuthFilter jwtAuthFilter;
    private final OAuthSuccessHandler oAuthSuccessHandler;

    public SecurityConfig(CorsConfig corsConfig,
                          JwtAuthFilter jwtAuthFilter,
                          OAuthSuccessHandler oAuthSuccessHandler) {
        this.corsConfig = corsConfig;
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuthSuccessHandler = oAuthSuccessHandler;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())

            // Sessions are only needed for the OAuth2 redirect flow.
            // After the callback, the frontend uses JWT and sessions are irrelevant.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            .authorizeHttpRequests(auth -> auth
                // Public: homepage can list anime without login
                .requestMatchers(HttpMethod.GET, "/api/anime-rooms").permitAll()

                // Public: WebSocket handshake
                .requestMatchers("/ws/**").permitAll()

                // Public: register new user
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()

                // Public: exchange Basic credentials for a JWT
                .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()

                // Spring Security handles the OAuth2 redirect URLs internally
                .requestMatchers("/login/oauth2/**", "/oauth2/**").permitAll()

                // ADMIN only
                .requestMatchers(HttpMethod.PUT,    "/api/anime-rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/anime-rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/discussion-rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/discussion-rooms/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )

            // Keep HTTP Basic for the /api/auth/token endpoint
            .httpBasic(withDefaults())

            // Google OAuth2 login
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuthSuccessHandler)
            )

            // Validate JWT on every request, before the Basic auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // PasswordEncoder bean has been moved to PasswordEncoderConfig.java
    // to break the circular dependency:
    // SecurityConfig → OAuthSuccessHandler → UserService → PasswordEncoder → SecurityConfig
}
