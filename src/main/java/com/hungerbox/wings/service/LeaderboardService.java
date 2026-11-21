package com.hungerbox.wings.service;

import com.hungerbox.wings.domain.AppUser;
import com.hungerbox.wings.domain.SmWeeklyScore;
import com.hungerbox.wings.repo.AppUserRepository;
import com.hungerbox.wings.repo.SmWeeklyScoreRepository;
import com.hungerbox.wings.scoring.Grades;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Weekly Top-10 SM leaderboard. No filters; reflects the latest weekly snapshot. Scores are not exposed, only
 * rank, grade, rostered days and rank movement.
 */
@Service
@Transactional(readOnly = true)
public class LeaderboardService {
    public static final String NOTE = "Grades are derived from SM Scores and are updated weekly.";

    public record Movement(String type, Integer positions, String display) {}
    public record Entry(int rank, Long smId, String smName, long totalRosteredDays, String grade, Movement movement) {}
    public record Leaderboard(LocalDate weekStart, String note, List<Entry> entries, Entry me) {}

    private final SmWeeklyScoreRepository weekly;
    private final AppUserRepository users;

    public LeaderboardService(SmWeeklyScoreRepository weekly, AppUserRepository users) {
        this.weekly = weekly; this.users = users;
    }

    /** @param viewerSmId when set (SM app), also returns that SM's own entry even if outside the Top 10. */
    public Leaderboard current(Long viewerSmId) {
        LocalDate latest = weekly.latestWeek();
        if (latest == null) return new Leaderboard(null, NOTE, List.of(), null);
        LocalDate prevWeek = latest.minusDays(7);

        List<SmWeeklyScore> cur = weekly.findByWeekStartAndScoreIsNotNull(latest);
        List<SmWeeklyScore> prev = weekly.findByWeekStartAndScoreIsNotNull(prevWeek);
        Set<Long> ids = new HashSet<>();
        cur.forEach(s -> ids.add(s.getSmId()));
        prev.forEach(s -> ids.add(s.getSmId()));
        Map<Long, String> names = new HashMap<>();
        for (AppUser u : users.findAllById(ids)) names.put(u.getId(), u.getName());

        List<SmWeeklyScore> curRanked = ranked(cur, names);
        Map<Long, Integer> prevRank = new HashMap<>();
        List<SmWeeklyScore> prevRanked = ranked(prev, names);
        for (int i = 0; i < prevRanked.size(); i++) prevRank.put(prevRanked.get(i).getSmId(), i + 1);

        List<Entry> entries = new ArrayList<>();
        Entry me = null;
        for (int i = 0; i < curRanked.size(); i++) {
            SmWeeklyScore s = curRanked.get(i);
            boolean top = i < 10, mine = Objects.equals(s.getSmId(), viewerSmId);
            if (!top && !mine) continue;
            Entry e = new Entry(i + 1, s.getSmId(), names.get(s.getSmId()), s.getRostered(), Grades.of(s.getScore()),
                    movement(s.getSmId(), i + 1, prevRank.get(s.getSmId()), prevWeek));
            if (top) entries.add(e);
            if (mine) me = e;
        }
        return new Leaderboard(latest, NOTE, entries, me);
    }

    /** Score desc, then more rostered days, then name A-Z. Every SM gets a unique rank. */
    private List<SmWeeklyScore> ranked(List<SmWeeklyScore> list, Map<Long, String> names) {
        List<SmWeeklyScore> l = new ArrayList<>(list);
        l.sort(Comparator.comparing(SmWeeklyScore::getScore, Comparator.reverseOrder())
                .thenComparing(SmWeeklyScore::getRostered, Comparator.reverseOrder())
                .thenComparing(s -> names.getOrDefault(s.getSmId(), "")));
        return l;
    }

    private Movement movement(Long smId, int rank, Integer prevRank, LocalDate prevWeek) {
        if (prevRank != null) {
            int diff = prevRank - rank;
            if (diff > 0) return new Movement("UP", diff, "↑ " + diff + (diff == 1 ? " position" : " positions"));
            if (diff < 0) return new Movement("DOWN", -diff, "↓ " + (-diff) + (diff == -1 ? " position" : " positions"));
            return new Movement("SAME", 0, "—");
        }
        // no score last week: "New" only if never scored before, otherwise show nothing (avoid misleading movement)
        boolean earlier = weekly.existsBySmIdAndWeekStartLessThanAndScoreIsNotNull(smId, prevWeek);
        return earlier ? new Movement("NONE", null, null) : new Movement("NEW", null, "New");
    }
}
