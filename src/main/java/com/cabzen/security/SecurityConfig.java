package com.cabzen.security;

import com.cabzen.entity.DriverProfile;
import com.cabzen.enums.ApprovalStatus;
import com.cabzen.repository.DriverProfileRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.io.IOException;

/**
 * Session-cookie login (no JWT): the frontend is served from the same origin, so an HttpOnly session cookie
 * plus a CSRF token header is the simplest secure option. Role rules cover both pages and APIs.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Tracks who is logged in where, so blocking a user can end their open sessions (see AdminService). */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registry when sessions time out or log out; Spring Boot registers it as a servlet listener. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DriverProfileRepository drivers,
                                            SessionRegistry sessionRegistry) throws Exception {
        AccessDeniedHandler forbidden = new AccessDeniedHandlerImpl();
        http
                // XSRF-TOKEN cookie readable by JS; js/api.js echoes it in the X-XSRF-TOKEN header
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/login.html", "/register.html",
                                "/css/**", "/js/**", "/img/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register/**").permitAll()
                        .requestMatchers("/passenger/**", "/api/passenger/**").hasRole("PASSENGER")
                        .requestMatchers("/driver/**", "/api/driver/**").hasRole("DRIVER")
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            AppUser user = (AppUser) authentication.getPrincipal();
                            writeJson(response, HttpStatus.OK, "{\"role\":\"%s\",\"redirectUrl\":\"%s\"}"
                                    .formatted(user.role(), homePage(user, drivers)));
                        })
                        .failureHandler((request, response, exception) -> writeJson(response, HttpStatus.UNAUTHORIZED,
                                "{\"status\":401,\"detail\":\"%s\"}".formatted(exception instanceof DisabledException
                                        ? "Your account has been blocked. Contact support."
                                        : "Invalid email or password.")))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                // Sessions the admin expired (user blocked) stop working on the very next request
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> {
                            if (event.getRequest().getRequestURI().startsWith("/api/")) {
                                writeJson(event.getResponse(), HttpStatus.UNAUTHORIZED,
                                        "{\"status\":401,\"detail\":\"Your account has been blocked. Contact support.\"}");
                            } else {
                                event.getResponse().sendRedirect("/login.html?blocked");
                            }
                        }))
                // APIs answer 401 JSON; pages fall back to the form-login redirect to /login.html
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        // A page for another role sends you to your own home instead of an error page
                        .accessDeniedHandler((request, response, denied) -> {
                            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                            if (!request.getRequestURI().startsWith("/api/") && auth != null
                                    && auth.getPrincipal() instanceof AppUser user) {
                                response.sendRedirect(homePage(user, drivers));
                            } else {
                                forbidden.handle(request, response, denied);
                            }
                        }));
        return http.build();
    }

    private static String homePage(AppUser user, DriverProfileRepository drivers) {
        return switch (user.role()) {
            case PASSENGER -> "/passenger/home.html";
            case ADMIN -> "/admin/dashboard.html";
            case DRIVER -> drivers.findById(user.id())
                    .map(DriverProfile::getApprovalStatus)
                    .filter(status -> status == ApprovalStatus.APPROVED)
                    .map(status -> "/driver/home.html")
                    .orElse("/driver/onboarding.html");
        };
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, String json) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json);
    }
}
