// package com.anime.backend.service;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.http.HttpMethod;
// import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.security.web.SecurityFilterChain;
// import static org.springframework.security.config.Customizer.withDefaults;

// @Configuration
// @EnableWebSecurity
// public class SecurityConfig {

//     @Bean
//     SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//         http
//             .csrf(csrf -> csrf.disable())
//             .authorizeHttpRequests(auth -> auth
//                 // ADMIN only: modify/delete anime rooms
//                 .requestMatchers(HttpMethod.PUT, "/api/anime-rooms/**").hasRole("ADMIN")
//                 .requestMatchers(HttpMethod.DELETE, "/api/anime-rooms/**").hasRole("ADMIN")

//                 // ADMIN only: modify/delete discussion rooms
//                 .requestMatchers(HttpMethod.PUT, "/api/discussion-rooms/**").hasRole("ADMIN")
//                 .requestMatchers(HttpMethod.DELETE, "/api/discussion-rooms/**").hasRole("ADMIN")
//                 .requestMatchers("/api/users").permitAll()
//                 // everything else requires login
//                 .anyRequest().authenticated()

//             )
//             .httpBasic(withDefaults());

//         return http.build();
//     }

//     @Bean
//     PasswordEncoder passwordEncoder() {
//         return new BCryptPasswordEncoder();
//     }
// }
package com.anime.backend.service;

import com.anime.backend.config.CorsConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfig corsConfig;

    public SecurityConfig(CorsConfig corsConfig) {
        this.corsConfig = corsConfig;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS using our CorsConfigurationSource bean
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))

            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                // Public: homepage can list anime without login
                .requestMatchers(HttpMethod.GET, "/api/anime-rooms").permitAll()

                // Public: WebSocket handshake
                .requestMatchers("/ws/**").permitAll()

                // Public: register new user
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()

                // ADMIN only: modify/delete anime rooms
                .requestMatchers(HttpMethod.PUT, "/api/anime-rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/anime-rooms/**").hasRole("ADMIN")

                // ADMIN only: modify/delete discussion rooms
                .requestMatchers(HttpMethod.PUT, "/api/discussion-rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/discussion-rooms/**").hasRole("ADMIN")
                // Everything else requires login
                .anyRequest().authenticated()
            )
            .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}