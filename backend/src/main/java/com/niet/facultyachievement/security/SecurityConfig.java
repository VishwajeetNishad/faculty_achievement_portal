package com.niet.facultyachievement.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://127.0.0.1:8080,http://localhost:5500,http://127.0.0.1:5500,http://localhost:3000}")
    private String rawAllowedOrigins;

    // Dev convenience: when true, ALSO allow any localhost origin/port (e.g. a Live
    // Server preview). Off by default so production only accepts the exact origins in
    // app.cors.allowed-origins (FRONTEND_ALLOWED_ORIGINS).
    @Value("${app.cors.allow-localhost:false}")
    private boolean allowLocalhostOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        // Account-status checks ("is this account still enabled?") normally run
        // BEFORE the password is verified. That would let anyone discover which
        // accounts are deactivated just by trying random passwords, because a
        // deactivated account answers differently from an active one.
        //
        // Moving the status check to AFTER the password check closes that leak:
        // a wrong password always produces the same generic "invalid
        // credentials" error, and only a caller who has proved they own the
        // account is told that it has been deactivated.
        authProvider.setPreAuthenticationChecks(userDetails -> {
            // Intentionally empty — status is checked post-authentication below.
        });
        authProvider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(type -> {})
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // "/api/health" is deliberately NOT listed: nothing is mapped there,
                // so whitelisting it only produced a confusing error for anyone who
                // tried it. The health check is Actuator's, below.
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // The public site: the faculty directory, public profiles, the
                // approved-and-public research gallery, and share links. No token,
                // no session, no identity.
                //
                // This line MUST stay above anyRequest().authenticated() — rules
                // are evaluated in order, and the first match wins, so a permitAll
                // placed after the catch-all would never be reached.
                //
                // Everything behind it is safe to expose without a login because
                // of decisions made elsewhere, not because of this line:
                //   * PublicController returns only dto.publicview classes, which
                //     have no field for a reviewer comment, an email, an employee
                //     id or a proof URL — so those cannot be serialised even by
                //     accident;
                //   * every public query hard-codes status = APPROVED AND
                //     visibility = PUBLIC as a literal, so no query parameter can
                //     widen what a visitor sees;
                //   * a share token is 32 random bytes, and its expiry and
                //     revocation are re-checked by the server on every request.
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/achievements/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()
                // Administrators keep the access they had; an account explicitly
                // granted VIEW_AUDIT_LOGS is allowed as well. This URL rule runs
                // BEFORE the check inside AuditLogController, so without the
                // permission listed here that check would never be reached and
                // the permission would silently have no effect.
                .requestMatchers("/api/audit-logs/**")
                    .hasAnyAuthority("ROLE_ADMIN", "ADMIN", Permissions.VIEW_AUDIT_LOGS)
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse production CORS allowed origins from environment variable/property
        List<String> origins = Arrays.stream(rawAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        configuration.setAllowedOrigins(origins);
        // Development only: allow any local IDE preview port (e.g. http://localhost:5500).
        // Disabled in production so only FRONTEND_ALLOWED_ORIGINS is accepted.
        if (allowLocalhostOrigins) {
            configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:*", "http://127.0.0.1:*"));
        }
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
