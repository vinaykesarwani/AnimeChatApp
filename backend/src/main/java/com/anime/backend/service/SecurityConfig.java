package com.anime.backend.service;

import com.anime.backend.config.CorsConfig;
import com.anime.backend.security.JwtAuthFilter;
import com.anime.backend.security.OAuth2SuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfig corsConfig;

    public SecurityConfig(CorsConfig corsConfig) {
        this.corsConfig = corsConfig;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                   OAuth2SuccessHandler oAuth2SuccessHandler, @Value("${frontend.url}") String frontendUrl) throws Exception {
        http
            // Enable CORS using our CorsConfigurationSource bean
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))

            .csrf(csrf -> csrf.disable())

            // Keep OAuth state in the session; API requests still authenticate with JWT.
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error", "/oauth2/**", "/login/oauth2/**").permitAll()
                // Public: homepage can list anime without login
                .requestMatchers(HttpMethod.GET, "/api/anime-rooms").permitAll()

                // Public: WebSocket handshake
                .requestMatchers("/ws/**").permitAll()

                // Public: register new user
                .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/login").permitAll()

                // Public: similarity check (reads only public room titles)
                .requestMatchers(HttpMethod.POST, "/api/similarity/check").permitAll()

                // ADMIN only: modify/delete anime rooms
                .requestMatchers(HttpMethod.PUT, "/api/anime-rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/anime-rooms/**").hasRole("ADMIN")

                // ADMIN only: modify/delete discussion rooms
                .requestMatchers(HttpMethod.PUT, "/api/discussion-rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/discussion-rooms/**").hasRole("ADMIN")
                // Everything else requires login
                .anyRequest().authenticated()
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    response.sendError(401, "Authentication required"))
                .accessDeniedHandler((request, response, exception) ->
                    response.sendError(403, "Not allowed")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // ── OAuth2 login ──────────────────────────────────────────────────────────
            .oauth2Login(oauth -> oauth
                .successHandler(oAuth2SuccessHandler)
                .failureHandler((request, response, exception) -> response.sendRedirect(
                    frontendUrl + "/oauth2/callback?error=oauth_login_failed"))
            );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}