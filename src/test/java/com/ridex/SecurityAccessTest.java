package com.ridex;

import com.ridex.dto.AuthDtos.RegisterRequest;
import com.ridex.enums.AccountStatus;
import com.ridex.enums.Role;
import com.ridex.security.AppUser;
import com.ridex.service.AdminService;
import com.ridex.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Role isolation, login and CSRF, against the real security chain and Neon. Data changes are rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAccessTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService auth;
    @Autowired
    AdminService adminService;

    static RequestPostProcessor as(Role role) {
        return user(new AppUser(-1L, role.name().toLowerCase() + "@test.example.com", "x", role, true));
    }

    @Test
    void anonymousApiCallsGet401AndPagesRedirectToLogin() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/passenger/bookings/active")).andExpect(status().isUnauthorized());
        mvc.perform(get("/passenger/home.html").accept(MediaType.TEXT_HTML)) // browsers ask for HTML
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
        mvc.perform(get("/login.html")).andExpect(status().isOk());
    }

    @Test
    void eachRoleIsLockedOutOfTheOthers() throws Exception {
        mvc.perform(get("/api/admin/stats").with(as(Role.PASSENGER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/driver/profile").with(as(Role.PASSENGER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/passenger/bookings/active").with(as(Role.DRIVER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").with(as(Role.DRIVER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/passenger/bookings/active").with(as(Role.ADMIN))).andExpect(status().isForbidden());
        // a page for another role sends you home instead of showing an error page
        mvc.perform(get("/admin/dashboard.html").with(as(Role.PASSENGER))).andExpect(redirectedUrl("/passenger/home.html"));
    }

    @Test
    void blockingAUserEndsTheirOpenSession() throws Exception {
        String email = "blocked-" + System.nanoTime() + "@test.example.com";
        long id = auth.registerPassenger(new RegisterRequest("Blocked Rider", email,
                "9" + String.valueOf(System.nanoTime()).substring(0, 9), "secret123", null)).id();
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", "secret123"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        adminService.setUserStatus(id, AccountStatus.BLOCKED);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your account has been blocked. Contact support."));
    }

    @Test
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register/passenger").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerThenLoginRedirectsToRoleHome() throws Exception {
        String email = "it-" + System.nanoTime() + "@test.example.com";
        String phone = "9" + String.valueOf(System.nanoTime()).substring(0, 9);

        mvc.perform(post("/api/auth/register/passenger").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test Rider","email":"%s","phone":"%s","password":"secret123"}"""
                                .formatted(email, phone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PASSENGER"));

        mvc.perform(post("/api/auth/login").with(csrf()).param("email", email).param("password", "secret123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("/passenger/home.html"));

        mvc.perform(post("/api/auth/login").with(csrf()).param("email", email).param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void validationErrorsComeBackPerField() throws Exception {
        mvc.perform(post("/api/auth/register/passenger").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","email":"not-an-email","phone":"123","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Enter a valid email"))
                .andExpect(jsonPath("$.errors.phone").value("Enter a valid 10-digit mobile number"));
    }
}
