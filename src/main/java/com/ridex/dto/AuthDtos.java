package com.ridex.dto;

import com.ridex.enums.ApprovalStatus;
import com.ridex.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Used by both sign-up forms; licenseNumber is required only for drivers (checked in AuthService). */
    public record RegisterRequest(
            @NotBlank(message = "Enter your full name") @Size(max = 100) String fullName,
            @NotBlank(message = "Enter your email") @Email(message = "Enter a valid email") @Size(max = 150) String email,
            @NotBlank(message = "Enter your mobile number")
            @Pattern(regexp = "[6-9][0-9]{9}", message = "Enter a valid 10-digit mobile number") String phone,
            @NotBlank(message = "Choose a password")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @Size(max = 30) String licenseNumber) {
    }

    public record MeResponse(Long id, String fullName, String email, String phone, Role role,
                             ApprovalStatus approvalStatus) {
    }
}
