package com.hungerbox.wings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungerbox.wings.common.ApiException;
import com.hungerbox.wings.domain.Role;
import org.springframework.stereotype.Service;

/** "Summarize this page" (Home + drill-down pages) for City Ops Heads and Area Managers. */
@Service
public class PageSummaryService {

    public enum Page { HOME, ATTENDANCE, CHECKLIST, RATING, DOCUMENTS, CAFE_SCORECARDS, SM_SCORECARDS }

    public record SummaryResponse(Page page, String summary, Object pageContext, String note) {}

    private final AccessGuard guard;
    private final ObjectMapper mapper;
    private final AdminChatbotClient chatbot;
    private final HomeService home;
    private final AttendanceService attendance;
    private final ChecklistService checklist;
    private final RatingService rating;
    private final DocumentService documents;
    private final ScopeService scopes;
    private final ScorePeriodService periods;
    private final CafeScoreService cafeScores;
    private final SmScoreService smScores;

    public PageSummaryService(AccessGuard guard, ObjectMapper mapper, AdminChatbotClient chatbot, HomeService home,
                              AttendanceService attendance, ChecklistService checklist, RatingService rating,
                              DocumentService documents, ScopeService scopes, ScorePeriodService periods,
                              CafeScoreService cafeScores, SmScoreService smScores) {
        this.guard = guard; this.mapper = mapper; this.chatbot = chatbot; this.home = home; this.attendance = attendance;
        this.checklist = checklist; this.rating = rating; this.documents = documents; this.scopes = scopes;
        this.periods = periods; this.cafeScores = cafeScores; this.smScores = smScores;
    }

    public SummaryResponse summarize(Long userId, Page page, DashboardFilter f) {
        var user = guard.require(userId, Role.AREA_MANAGER, Role.CITY_OPS_HEAD, Role.OPS_HEAD);
        Object ctx = switch (page) {
            case HOME -> home.home(userId, f);
            case ATTENDANCE -> attendance.report(userId, f, "attendance", false);
            case CHECKLIST -> checklist.report(userId, f, "fillRate", false);
            case RATING -> rating.report(userId, f);
            case DOCUMENTS -> documents.report(userId, f);
            case CAFE_SCORECARDS -> cafeScores.scorecards(scopes.resolve(userId, f), periods.window(f), 0, 5, false);
            case SM_SCORECARDS -> smScores.scorecards(scopes.resolve(userId, f), periods.window(f), 0, 5, false);
        };
        try {
            String json = mapper.writeValueAsString(ctx);
            var summary = chatbot.summarize(user.getRole().name(), json);
            return new SummaryResponse(page, summary.orElse(null), ctx,
                    summary.isPresent() ? null : "Admin chatbot is not configured (wings.chatbot.url); returning page context only.");
        } catch (JsonProcessingException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Could not build page context");
        }
    }
}
