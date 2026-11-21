package com.hungerbox.wings.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Publishes the score of the week that just ended. */
@Component
public class ScoreJob {
    private static final Logger log = LoggerFactory.getLogger(ScoreJob.class);
    private final ScoreCalculationService calc;
    private final DateRangeService dates;

    public ScoreJob(ScoreCalculationService calc, DateRangeService dates) { this.calc = calc; this.dates = dates; }

    @Scheduled(cron = "${wings.score-job-cron}", zone = "${wings.timezone}")
    public void publishLastWeek() {
        LocalDate week = DateRangeService.mondayOf(dates.today()).minusDays(7);
        log.info("Publishing weekly scores for week starting {}", week);
        calc.calculateWeek(week);
    }
}
