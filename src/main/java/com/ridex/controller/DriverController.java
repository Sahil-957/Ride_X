package com.ridex.controller;

import com.ridex.dto.BookingDtos.BookingView;
import com.ridex.dto.BookingDtos.OtpRequest;
import com.ridex.dto.BookingDtos.RequestCard;
import com.ridex.dto.DriverDtos.*;
import com.ridex.enums.DocumentType;
import com.ridex.security.AppUser;
import com.ridex.service.BookingService;
import com.ridex.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/driver")
public class DriverController {

    private final DriverService driverService;
    private final BookingService bookingService;

    public DriverController(DriverService driverService, BookingService bookingService) {
        this.driverService = driverService;
        this.bookingService = bookingService;
    }

    // ---- onboarding

    @GetMapping("/profile")
    public DriverProfileView profile(@AuthenticationPrincipal AppUser me) {
        return driverService.profile(me.id());
    }

    @PutMapping("/profile")
    public DriverProfileView updateProfile(@AuthenticationPrincipal AppUser me, @Valid @RequestBody ProfileRequest request) {
        return driverService.updateProfile(me.id(), request);
    }

    @PutMapping("/vehicle")
    public DriverProfileView saveVehicle(@AuthenticationPrincipal AppUser me, @Valid @RequestBody VehicleRequest request) {
        return driverService.saveVehicle(me.id(), request);
    }

    @GetMapping("/documents")
    public List<DocumentInfo> documents(@AuthenticationPrincipal AppUser me) {
        return driverService.documents(me.id());
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentInfo upload(@AuthenticationPrincipal AppUser me, @RequestParam DocumentType type,
                               @RequestParam MultipartFile file) {
        return driverService.upload(me.id(), type, file);
    }

    // ---- availability

    @PutMapping("/availability")
    public DriverProfileView availability(@AuthenticationPrincipal AppUser me, @Valid @RequestBody AvailabilityRequest request) {
        return driverService.setOnline(me.id(), request.online());
    }

    @PutMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void location(@AuthenticationPrincipal AppUser me, @Valid @RequestBody LocationRequest request) {
        driverService.updateLocation(me.id(), request.lat(), request.lng());
    }

    // ---- rides

    @GetMapping("/requests")
    public List<RequestCard> requests(@AuthenticationPrincipal AppUser me) {
        return bookingService.openRequests(me.id());
    }

    @PostMapping("/bookings/{id}/accept")
    public BookingView accept(@AuthenticationPrincipal AppUser me, @PathVariable Long id) {
        return bookingService.accept(me.id(), id);
    }

    @PostMapping("/bookings/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AppUser me, @PathVariable Long id) {
        bookingService.reject(me.id(), id);
    }

    /** 204 when the driver has no ride in progress. */
    @GetMapping("/bookings/active")
    public ResponseEntity<BookingView> active(@AuthenticationPrincipal AppUser me) {
        return bookingService.activeForDriver(me.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/bookings/{id}/start")
    public BookingView start(@AuthenticationPrincipal AppUser me, @PathVariable Long id, @Valid @RequestBody OtpRequest request) {
        return bookingService.start(me.id(), id, request.otp());
    }

    @PostMapping("/bookings/{id}/complete")
    public BookingView complete(@AuthenticationPrincipal AppUser me, @PathVariable Long id) {
        return bookingService.complete(me.id(), id);
    }

    @PostMapping("/bookings/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AppUser me, @PathVariable Long id) {
        bookingService.releaseByDriver(me.id(), id);
    }

    @GetMapping("/bookings")
    public List<BookingView> history(@AuthenticationPrincipal AppUser me) {
        return bookingService.driverHistory(me.id());
    }

    @GetMapping("/earnings")
    public Earnings earnings(@AuthenticationPrincipal AppUser me) {
        return driverService.earnings(me.id());
    }
}
