package com.cabzen.controller;

import com.cabzen.dto.BookingDtos.*;
import com.cabzen.security.AppUser;
import com.cabzen.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passenger")
public class PassengerController {

    private final BookingService bookingService;

    public PassengerController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/estimate")
    public Estimate estimate(@Valid @RequestBody TripRequest trip) {
        return bookingService.estimate(trip);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView book(@AuthenticationPrincipal AppUser me, @Valid @RequestBody TripRequest trip) {
        return bookingService.create(me.id(), trip);
    }

    /** 204 when there is no open booking. */
    @GetMapping("/bookings/active")
    public ResponseEntity<BookingView> active(@AuthenticationPrincipal AppUser me) {
        return bookingService.activeForPassenger(me.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/bookings")
    public List<BookingView> history(@AuthenticationPrincipal AppUser me) {
        return bookingService.passengerHistory(me.id());
    }

    @GetMapping("/bookings/{id}")
    public BookingView booking(@AuthenticationPrincipal AppUser me, @PathVariable Long id) {
        return bookingService.passengerBooking(me.id(), id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingView cancel(@AuthenticationPrincipal AppUser me, @PathVariable Long id) {
        return bookingService.cancelByPassenger(me.id(), id);
    }

    @PostMapping("/bookings/{id}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@AuthenticationPrincipal AppUser me, @PathVariable Long id, @Valid @RequestBody RatingRequest rating) {
        bookingService.rate(me.id(), id, rating);
    }
}
