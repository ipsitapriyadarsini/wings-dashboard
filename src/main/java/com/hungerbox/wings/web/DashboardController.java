package com.hungerbox.wings.web;

import com.hungerbox.wings.common.PageResult;
import com.hungerbox.wings.domain.Cafe;
import com.hungerbox.wings.repo.VendorRepository;
import com.hungerbox.wings.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Wings Dashboard (Area Managers, City Ops Heads, Ops Heads). Every endpoint takes the global filters
 * (see {@link DashboardFilter}) and the caller's identity in the X-User-Id header.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {
    private static final String USER = "X-User-Id";

    private final HomeService home;
    private final CafeScoreService cafeScores;
    private final SmScoreService smScores;
    private final LeaderboardService leaderboard;
    private final AttendanceService attendance;
    private final ChecklistService checklist;
    private final RatingService rating;
    private final DocumentService documents;
    private final PageSummaryService summary;
    private final ScopeService scopes;
    private final ScorePeriodService periods;
    private final VendorRepository vendors;

    public DashboardController(HomeService home, CafeScoreService cafeScores, SmScoreService smScores,
                               LeaderboardService leaderboard, AttendanceService attendance, ChecklistService checklist,
                               RatingService rating, DocumentService documents, PageSummaryService summary,
                               ScopeService scopes, ScorePeriodService periods, VendorRepository vendors) {
        this.home = home; this.cafeScores = cafeScores; this.smScores = smScores; this.leaderboard = leaderboard;
        this.attendance = attendance; this.checklist = checklist; this.rating = rating; this.documents = documents;
        this.summary = summary; this.scopes = scopes; this.periods = periods; this.vendors = vendors;
    }

    public record Option(Long id, String name) {}
    public record FilterOptions(List<Option> cities, List<Option> companies, List<Option> cafes, List<Option> vendors) {}

    @GetMapping("/filters/options")
    @Operation(summary = "City / company / cafe / vendor options within the caller's access scope")
    public FilterOptions filterOptions(@RequestHeader(USER) Long userId) {
        Map<Long, Option> cities = new TreeMap<>(), companies = new TreeMap<>(), cafes = new TreeMap<>();
        for (Cafe c : scopes.accessibleCafes(userId)) {
            cities.put(c.getCity().getId(), new Option(c.getCity().getId(), c.getCity().getName()));
            if (c.getCompany() != null) companies.put(c.getCompany().getId(), new Option(c.getCompany().getId(), c.getCompany().getName()));
            cafes.put(c.getId(), new Option(c.getId(), c.getName()));
        }
        List<Option> vs = vendors.findAll().stream().map(v -> new Option(v.getId(), v.getName())).toList();
        return new FilterOptions(List.copyOf(cities.values()), List.copyOf(companies.values()), List.copyOf(cafes.values()), vs);
    }

    @GetMapping("/home")
    @Operation(summary = "Home page: Cafe Score (+trend, top/bottom 3), attendance & checklist, rating, insights")
    public HomeService.HomeResponse home(@RequestHeader(USER) Long userId, DashboardFilter f) {
        return home.home(userId, f);
    }

    @GetMapping("/scorecards/cafes")
    @Operation(summary = "Cafe scorecards, 5 per page; sort=asc|desc only honoured when 10+ cafes")
    public CafeScoreService.ScorecardPage cafeScorecards(@RequestHeader(USER) Long userId, DashboardFilter f,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "5") int size,
                                                         @RequestParam(defaultValue = "desc") String sort) {
        return cafeScores.scorecards(scopes.resolve(userId, f), periods.window(f), page, size, "asc".equalsIgnoreCase(sort));
    }

    @GetMapping("/scorecards/sms")
    @Operation(summary = "SM scorecards, 5 per page; sort=asc|desc only honoured when 10+ SMs")
    public SmScoreService.SmScorecardPage smScorecards(@RequestHeader(USER) Long userId, DashboardFilter f,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "5") int size,
                                                       @RequestParam(defaultValue = "desc") String sort) {
        return smScores.scorecards(scopes.resolve(userId, f), periods.window(f), page, size, "asc".equalsIgnoreCase(sort));
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Weekly Top 10 SM leaderboard (no filters)")
    public LeaderboardService.Leaderboard leaderboard(@RequestHeader(USER) Long userId) {
        scopes.resolve(userId, DashboardFilter.empty()); // access check only
        return leaderboard.current(null);
    }

    @GetMapping("/attendance")
    @Operation(summary = "Attendance drill-down. sortBy=attendance|punctuality")
    public AttendanceService.AttendanceReport attendance(@RequestHeader(USER) Long userId, DashboardFilter f,
                                                         @RequestParam(defaultValue = "attendance") String sortBy,
                                                         @RequestParam(defaultValue = "desc") String dir) {
        return attendance.report(userId, f, sortBy, "asc".equalsIgnoreCase(dir));
    }

    @GetMapping("/checklists")
    @Operation(summary = "Checklist drill-down (supports vendorIds). sortBy=fillRate|quality")
    public ChecklistService.ChecklistReport checklists(@RequestHeader(USER) Long userId, DashboardFilter f,
                                                       @RequestParam(defaultValue = "fillRate") String sortBy,
                                                       @RequestParam(defaultValue = "desc") String dir) {
        return checklist.report(userId, f, sortBy, "asc".equalsIgnoreCase(dir));
    }

    @GetMapping("/ratings")
    @Operation(summary = "Rating drill-down")
    public RatingService.RatingReport ratings(@RequestHeader(USER) Long userId, DashboardFilter f) {
        return rating.report(userId, f);
    }

    @GetMapping("/documents")
    @Operation(summary = "Compliance documents drill-down (supports vendorIds)")
    public DocumentService.DocumentsReport documents(@RequestHeader(USER) Long userId, DashboardFilter f) {
        return documents.report(userId, f);
    }

    @PostMapping("/documents/{id}/approve")
    @Operation(summary = "Approve an uploaded document (City Ops Head only)")
    public DocumentService.DocItem approve(@RequestHeader(USER) Long userId, @PathVariable Long id) {
        return documents.approve(userId, id);
    }

    @PutMapping("/documents/{id}/upload")
    @Operation(summary = "Upload / re-upload a document (stores the file reference and new expiry date)")
    public DocumentService.DocItem upload(@RequestHeader(USER) Long userId, @PathVariable Long id,
                                          @RequestBody DocumentService.UploadRequest body) {
        return documents.upload(userId, id, body);
    }

    @PostMapping("/summary")
    @Operation(summary = "Summarize this page via the admin chatbot (AM / City Ops Head)")
    public PageSummaryService.SummaryResponse summarize(@RequestHeader(USER) Long userId,
                                                        @RequestParam PageSummaryService.Page page, DashboardFilter f) {
        return summary.summarize(userId, page, f);
    }
}
