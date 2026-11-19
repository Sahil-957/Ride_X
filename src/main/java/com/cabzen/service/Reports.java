package com.cabzen.service;

import com.cabzen.repository.PaymentRepository.DailyTotal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Helpers for per-day report rows (days without rides must still appear as zero). */
final class Reports {

    private Reports() {
    }

    static Map<String, DailyTotal> byDay(List<DailyTotal> rows) {
        return rows.stream().collect(Collectors.toMap(DailyTotal::getDay, Function.identity()));
    }

    /** The last `count` days ending today, oldest first. */
    static List<LocalDate> lastDays(LocalDate today, int count) {
        return Stream.iterate(today.minusDays(count - 1L), d -> d.plusDays(1)).limit(count).toList();
    }
}
