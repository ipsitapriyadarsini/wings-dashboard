package com.hungerbox.wings.web;

import com.hungerbox.wings.common.ApiException;
import com.hungerbox.wings.domain.Cafe;
import com.hungerbox.wings.domain.Role;
import com.hungerbox.wings.repo.CafeRepository;
import com.hungerbox.wings.service.AccessGuard;
import com.hungerbox.wings.service.DateRangeService;
import com.hungerbox.wings.service.ScoreCalculationService;
import com.hungerbox.wings.service.ScoreConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/** Configuration and operations for Ops Heads: weightages, benchmarks, excluded locations, score re-publish. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminController {
    private static final String USER = "X-User-Id";
    private final AccessGuard guard;
    private final ScoreConfigService config;
    private final CafeRepository cafes;
    private final ScoreCalculationService calc;
    private final DateRangeService dates;

    public AdminController(AccessGuard guard, ScoreConfigService config, CafeRepository cafes,
                           ScoreCalculationService calc, DateRangeService dates) {
        this.guard = guard; this.config = config; this.cafes = cafes; this.calc = calc; this.dates = dates;
    }

    @GetMapping("/config")
    public Map<String, Double> config(@RequestHeader(USER) Long userId) {
        guard.require(userId, Role.OPS_HEAD);
        return config.all();
    }

    @PutMapping("/config")
    public Map<String, Double> update(@RequestHeader(USER) Long userId, @RequestBody Map<String, Double> values) {
        guard.require(userId, Role.OPS_HEAD);
        values.forEach(config::set);
        return config.all();
    }

    @PutMapping("/cafes/{id}/score-exclusion")
    @Transactional
    public Map<String, Object> exclude(@RequestHeader(USER) Long userId, @PathVariable Long id, @RequestParam boolean excluded) {
        guard.require(userId, Role.OPS_HEAD);
        Cafe c = cafes.findById(id).orElseThrow(() -> ApiException.notFound("Cafe " + id));
        c.setScoreExcluded(excluded);
        return Map.of("cafeId", id, "scoreExcluded", excluded);
    }

    /** Re-publish a completed week (idempotent). weekStart must be a Monday in the past. */
    @PostMapping("/scores/recalculate")
    public Map<String, Object> recalculate(@RequestHeader(USER) Long userId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        guard.require(userId, Role.OPS_HEAD);
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) throw ApiException.badRequest("weekStart must be a Monday");
        if (!weekStart.plusDays(6).isBefore(dates.today())) throw ApiException.badRequest("Week is not complete yet");
        calc.calculateWeek(weekStart);
        return Map.of("recalculatedWeek", weekStart.toString());
    }
}
