package com.cabzen.service;

import com.cabzen.dto.AuthDtos.MeResponse;
import com.cabzen.dto.AuthDtos.RegisterRequest;
import com.cabzen.entity.DriverProfile;
import com.cabzen.entity.User;
import com.cabzen.enums.ApprovalStatus;
import com.cabzen.enums.Role;
import com.cabzen.exception.ApiException;
import com.cabzen.repository.DriverProfileRepository;
import com.cabzen.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final DriverProfileRepository drivers;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, DriverProfileRepository drivers, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.drivers = drivers;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MeResponse registerPassenger(RegisterRequest request) {
        return toMe(createUser(request, Role.PASSENGER), null);
    }

    @Transactional
    public MeResponse registerDriver(RegisterRequest request) {
        String license = request.licenseNumber() == null ? "" : request.licenseNumber().trim().toUpperCase(Locale.ROOT);
        if (license.isEmpty()) {
            throw ApiException.badRequest("Driving licence number is required");
        }
        if (drivers.existsByLicenseNumber(license)) {
            throw ApiException.conflict("This licence number is already registered.");
        }
        User user = createUser(request, Role.DRIVER);
        DriverProfile profile = drivers.save(new DriverProfile(user, license));
        return toMe(user, profile.getApprovalStatus());
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        ApprovalStatus approval = user.getRole() == Role.DRIVER
                ? drivers.findById(userId).map(DriverProfile::getApprovalStatus).orElse(null)
                : null;
        return toMe(user, approval);
    }

    private User createUser(RegisterRequest request, Role role) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("This email is already registered.");
        }
        if (users.existsByPhone(request.phone())) {
            throw ApiException.conflict("This phone number is already registered.");
        }
        return users.save(new User(request.fullName().trim(), email, request.phone(),
                passwordEncoder.encode(request.password()), role));
    }

    private static MeResponse toMe(User user, ApprovalStatus approval) {
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getRole(), approval);
    }
}
