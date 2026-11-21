package com.hungerbox.wings.config;

import com.hungerbox.wings.domain.*;
import com.hungerbox.wings.service.DateRangeService;
import com.hungerbox.wings.service.ScoreCalculationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;

/**
 * Generates ~10 weeks of believable sample data for local development (profile "demo", the default),
 * then publishes the weekly scores. Seeded, so runs are reproducible. Not used in prod.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String[] BUCKETS = {"Hygiene & Cleanliness", "Food Safety", "Operations"};
    private static final String[][] QUESTIONS = {
            {"Are floors clean and dry?", "Are dustbins covered and emptied?", "Is the pantry area sanitised?"},
            {"Is hot food held above 60C?", "Are FIFO labels in place?", "Is the fridge temperature below 5C?"},
            {"Is the menu board updated?", "Are counters fully staffed?"}};
    private static final String[][] ISSUES = {
            {"Food quality", "Cold food", "Taste"}, {"Hygiene", "Dirty tables", "Unclean utensils"},
            {"Service", "Long queue", "Staff behaviour"}, {"Pricing", "Overpriced", "Wrong bill"}};

    @PersistenceContext private EntityManager em;
    private final TransactionTemplate tx;
    private final ScoreCalculationService calc;
    private final DateRangeService dates;

    public DemoDataLoader(TransactionTemplate tx, ScoreCalculationService calc, DateRangeService dates) {
        this.tx = tx; this.calc = calc; this.dates = dates;
    }

    @Override
    public void run(String... args) {
        tx.executeWithoutResult(s -> seed());
        LocalDate lastWeek = DateRangeService.mondayOf(dates.today()).minusDays(7);
        for (int i = 9; i >= 0; i--) calc.calculateWeek(lastWeek.minusWeeks(i));
        log.info("Demo data ready. Swagger UI: /swagger-ui.html");
    }

    private void seed() {
        Random rnd = new Random(42);
        LocalDate today = dates.today();

        List<City> cities = List.of(new City("Bengaluru"), new City("Pune"), new City("Mumbai"));
        List<Company> companies = List.of(new Company("Acme Tech"), new Company("Globex"), new Company("Initech"));
        List<Vendor> vendors = List.of(new Vendor("FreshBite Catering"), new Vendor("Spice Route"), new Vendor("Urban Kitchen"));
        cities.forEach(em::persist); companies.forEach(em::persist); vendors.forEach(em::persist);

        AppUser opsHead = new AppUser("Olivia Ops-Head", "ops.head@demo.local", Role.OPS_HEAD);
        AppUser cityHeadBlr = new AppUser("Chandan City-Head (BLR+PUN)", "chandan@demo.local", Role.CITY_OPS_HEAD);
        AppUser cityHeadMum = new AppUser("Meera City-Head (MUM)", "meera@demo.local", Role.CITY_OPS_HEAD);
        cityHeadBlr.getCities().add(cities.get(0)); cityHeadBlr.getCities().add(cities.get(1));
        cityHeadMum.getCities().add(cities.get(2));
        List<AppUser> ams = List.of(new AppUser("Aarav AM", "aarav@demo.local", Role.AREA_MANAGER),
                new AppUser("Bhavna AM", "bhavna@demo.local", Role.AREA_MANAGER),
                new AppUser("Chirag AM", "chirag@demo.local", Role.AREA_MANAGER));
        em.persist(opsHead); em.persist(cityHeadBlr); em.persist(cityHeadMum); ams.forEach(em::persist);

        List<Cafe> cafes = new ArrayList<>();
        List<AppUser> sms = new ArrayList<>();
        List<Double> quality = new ArrayList<>(); // per-cafe "how well run" factor
        for (int i = 0; i < 12; i++) {
            Cafe c = new Cafe("Cafe " + (char) ('A' + i), cities.get(i % 3), companies.get(i % 3), ams.get(i % 3));
            em.persist(c);
            cafes.add(c);
            quality.add(0.5 + rnd.nextDouble() * 0.48);
            AppUser sm = new AppUser("SM " + (char) ('A' + i), "sm" + i + "@demo.local", Role.SITE_MANAGER);
            sm.getCafes().add(c);
            em.persist(sm);
            sms.add(sm);
        }
        // two SMs cover a second cafe (multi-cafe rostering) and one cafe has two SMs
        sms.get(0).getCafes().add(cafes.get(3));
        sms.get(5).getCafes().add(cafes.get(6));
        AppUser extra = new AppUser("SM Extra", "sm.extra@demo.local", Role.SITE_MANAGER);
        extra.getCafes().add(cafes.get(1));
        em.persist(extra);
        sms.add(extra);

        LocalDate start = today.minusDays(75);
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            for (int i = 0; i < cafes.size(); i++) {
                Cafe cafe = cafes.get(i);
                double q = quality.get(i);
                Vendor vendor = vendors.get(i % vendors.size());

                // rosters: the cafe's own SM (+ SM 0 covers cafe 3 on weekdays, extra SM at cafe 1)
                List<AppUser> onDuty = new ArrayList<>(List.of(sms.get(i)));
                if (i == 3 && d.getDayOfWeek().getValue() <= 5) onDuty.add(sms.get(0));
                if (i == 1) onDuty.add(extra);
                for (AppUser sm : onDuty) {
                    if (sm == sms.get(0) && i == 0 && d.getDayOfWeek().getValue() <= 5 && d.getDayOfMonth() % 2 == 0) continue;
                    double r = rnd.nextDouble();
                    if (r < 0.03) {
                        em.persist(new Roster(sm, cafe, d, RosterStatus.APPROVED_LEAVE, false, null, null, null, null));
                    } else if (r < 0.03 + (1 - q) * 0.25) {
                        em.persist(new Roster(sm, cafe, d, RosterStatus.ASSIGNED, false, null, null, null, null));
                    } else {
                        em.persist(new Roster(sm, cafe, d, RosterStatus.ASSIGNED, true, rnd.nextDouble() < q + 0.1,
                                rnd.nextDouble() < q + 0.1, rnd.nextDouble() < 0.95, rnd.nextDouble() < 0.95));
                    }
                }

                // two checklists a day; fill rate / compliance follow the cafe factor; a few days have none mapped
                if (rnd.nextDouble() < 0.9) {
                    for (int k = 0; k < 2; k++) {
                        Checklist cl = new Checklist(cafe, vendor, d, k == 0 ? "Opening checklist" : "Closing checklist");
                        em.persist(cl);
                        boolean partial = rnd.nextDouble() > q;
                        for (int b = 0; b < BUCKETS.length; b++) {
                            for (String qt : QUESTIONS[b]) {
                                boolean filled = partial ? rnd.nextDouble() < 0.6 : rnd.nextDouble() < 0.97;
                                if (!filled) {
                                    em.persist(new ChecklistResponse(cl, qt, BUCKETS[b], AnswerType.BOOLEAN, ResponseStatus.UNFILLED, null));
                                } else if (rnd.nextDouble() < 0.05) {
                                    em.persist(new ChecklistResponse(cl, qt, BUCKETS[b], AnswerType.BOOLEAN, ResponseStatus.NA, null));
                                } else {
                                    AnswerType t = rnd.nextDouble() < 0.2 ? AnswerType.AI_GRADED : AnswerType.BOOLEAN;
                                    em.persist(new ChecklistResponse(cl, qt, BUCKETS[b], t, ResponseStatus.ANSWERED, rnd.nextDouble() < q - 0.03));
                                }
                            }
                        }
                    }
                }

                // orders + ratings (some days/cafes have no ratings at all)
                int orders = 60 + rnd.nextInt(60);
                em.persist(new CafeDailyOrders(cafe, d, orders));
                if (!(i == 11 && rnd.nextDouble() < 0.7)) {
                    int ratings = (int) (orders * 0.12);
                    for (int n = 0; n < ratings; n++) {
                        int stars = Math.max(1, Math.min(5, (int) Math.round(q * 5 + rnd.nextGaussian() * 1.1)));
                        String[] issue = stars <= 3 ? ISSUES[rnd.nextInt(ISSUES.length)] : null;
                        em.persist(new Rating(cafe, vendor, d, stars, issue == null ? null : issue[0],
                                issue == null ? null : issue[1 + rnd.nextInt(2)]));
                    }
                }
            }
        }

        // documents with a spread of expired / expiring / valid
        String[][] docs = {{"FSSAI Licence", "FOOD_SAFETY"}, {"Trade Licence", "BUSINESS"}, {"Pay Slips", "COMPLIANCE"},
                {"HACCP/ISO Certificate", "FOOD_SAFETY"}, {"Shop & Establishment Licence", "BUSINESS"}, {"Info-Sec Policy", "IT_SECURITY"}};
        for (Cafe c : cafes) {
            for (String[] doc : docs) {
                int offset = rnd.nextInt(240) - 40; // -40..+199 days from today
                em.persist(new ComplianceDocument(c, vendors.get(rnd.nextInt(3)), doc[0], DocumentCategory.valueOf(doc[1]),
                        today.minusDays(rnd.nextInt(300)), today.plusDays(offset), opsHead, rnd.nextBoolean()));
            }
        }

        em.flush();
        StringBuilder sb = new StringBuilder("Demo users (send as X-User-Id): ");
        for (AppUser u : List.of(opsHead, cityHeadBlr, cityHeadMum, ams.get(0), ams.get(1), ams.get(2), sms.get(0), sms.get(1))) {
            sb.append(u.getId()).append("=").append(u.getName()).append(" [").append(u.getRole()).append("]; ");
        }
        log.info(sb.toString());
    }
}
