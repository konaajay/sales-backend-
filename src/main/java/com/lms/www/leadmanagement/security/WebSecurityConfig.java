package com.lms.www.leadmanagement.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Autowired
    private SessionActivityFilter sessionFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // stronger
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(org.springframework.web.cors.CorsUtils::isPreFlightRequest).permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/error")
                        .permitAll()
                        .requestMatchers("/api/public/**", "/api/payments/**").permitAll()

                        // Restrict payment endpoints properly
                        .requestMatchers(HttpMethod.POST, "/api/payments/create").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/payments/status/**").authenticated()

                        // Everything else secured
                        .anyRequest().authenticated())

                .authenticationProvider(authenticationProvider())

                // Filters
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(sessionFilter, JwtAuthenticationFilter.class)

                // Exception handling
                .exceptionHandling(ex -> ex

                        .authenticationEntryPoint((req, res, e) -> {
                            System.err.println("[SECURITY] 401 Unauthorized for: " + req.getRequestURI() + " | Error: "
                                    + e.getMessage());
                            res.setContentType("application/json");
                            res.setStatus(401);
                            res.getWriter()
                                    .write("{\"error\":\"Unauthorized\", \"path\":\"" + req.getRequestURI() + "\"}");
                        })

                        .accessDeniedHandler((req, res, e) -> {
                            System.err.println("[SECURITY] 403 Forbidden for: " + req.getRequestURI() + " | Error: "
                                    + e.getMessage());
                            res.setContentType("application/json");
                            res.setStatus(403);
                            res.getWriter()
                                    .write("{\"error\":\"Forbidden\", \"path\":\"" + req.getRequestURI() + "\"}");
                        }));

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var config = new org.springframework.web.cors.CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                frontendUrl,
                "http://localhost:3000",
                "http://192.168.*:*",
                "http://100.*:*"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}