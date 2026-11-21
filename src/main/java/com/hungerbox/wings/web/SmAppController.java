package com.hungerbox.wings.web;

import com.hungerbox.wings.service.LeaderboardService;
import com.hungerbox.wings.service.SmAppService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** Site Manager view in the Wings app. Today's action items (roster, checklist, food counters) are unchanged. */
@RestController
@RequestMapping("/api/v1/sm")
@Tag(name = "SM app")
public class SmAppController {
    private final SmAppService sm;

    public SmAppController(SmAppService sm) { this.sm = sm; }

    @GetMapping("/home")
    public SmAppService.SmHome home(@RequestHeader("X-User-Id") Long userId) { return sm.home(userId); }

    @GetMapping("/score")
    public SmAppService.ScoreCard score(@RequestHeader("X-User-Id") Long userId) { return sm.score(userId); }

    @GetMapping("/score/checklist")
    public SmAppService.ChecklistBreakdown checklist(@RequestHeader("X-User-Id") Long userId) {
        return sm.checklistBreakdown(userId);
    }

    @GetMapping("/leaderboard")
    public LeaderboardService.Leaderboard leaderboard(@RequestHeader("X-User-Id") Long userId) {
        return sm.leaderboard(userId);
    }
}
