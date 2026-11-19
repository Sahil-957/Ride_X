package com.cabzen.controller;

import com.cabzen.dto.AuthDtos.MeResponse;
import com.cabzen.dto.AuthDtos.RegisterRequest;
import com.cabzen.security.AppUser;
import com.cabzen.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Login and logout are handled by Spring Security (see SecurityConfig); this covers sign-up and "who am I". */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/passenger")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse registerPassenger(@Valid @RequestBody RegisterRequest request) {
        return authService.registerPassenger(request);
    }

    @PostMapping("/register/driver")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse registerDriver(@Valid @RequestBody RegisterRequest request) {
        return authService.registerDriver(request);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUser me) {
        return authService.me(me.id());
    }
}
