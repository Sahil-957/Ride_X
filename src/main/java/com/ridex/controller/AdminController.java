package com.ridex.controller;

import com.ridex.dto.AdminDtos.*;
import com.ridex.dto.BookingDtos.BookingView;
import com.ridex.dto.DriverDtos.DriverProfileView;
import com.ridex.entity.DriverDocument;
import com.ridex.enums.ApprovalStatus;
import com.ridex.enums.BookingStatus;
import com.ridex.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public Stats stats() {
        return adminService.stats();
    }

    @GetMapping("/analytics")
    public Analytics analytics(@RequestParam(defaultValue = "14") int days) {
        return adminService.analytics(days);
    }

    @GetMapping("/passengers")
    public List<PassengerRow> passengers() {
        return adminService.passengers();
    }

    @PatchMapping("/users/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void userStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        adminService.setUserStatus(id, request.status());
    }

    @GetMapping("/drivers")
    public List<DriverRow> drivers(@RequestParam(required = false) ApprovalStatus approval) {
        return adminService.drivers(approval);
    }

    @GetMapping("/drivers/{id}")
    public DriverProfileView driver(@PathVariable Long id) {
        return adminService.driver(id);
    }

    @PostMapping("/drivers/{id}/approve")
    public DriverProfileView approve(@PathVariable Long id) {
        return adminService.approve(id);
    }

    @PostMapping("/drivers/{id}/reject")
    public DriverProfileView reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return adminService.reject(id, request.reason());
    }

    /** Streams an uploaded document inline (PDF or image) for review. */
    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> document(@PathVariable Long id) {
        DriverDocument document = adminService.document(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(document.getFileName()).build().toString())
                .body(document.getData());
    }

    @GetMapping("/vehicles")
    public List<VehicleRow> vehicles() {
        return adminService.vehicles();
    }

    @PatchMapping("/vehicles/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vehicleStatus(@PathVariable Long id, @Valid @RequestBody VehicleStatusRequest request) {
        adminService.setVehicleStatus(id, request.status());
    }

    @GetMapping("/bookings")
    public List<BookingView> bookings(@RequestParam(required = false) BookingStatus status) {
        return adminService.bookings(status);
    }

    @GetMapping("/live")
    public LiveMap live() {
        return adminService.live();
    }
}
