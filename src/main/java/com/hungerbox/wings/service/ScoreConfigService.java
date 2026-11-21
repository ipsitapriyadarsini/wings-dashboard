package com.hungerbox.wings.service;

import com.hungerbox.wings.common.ApiException;
import com.hungerbox.wings.domain.ScoreConfig;
import com.hungerbox.wings.repo.ScoreConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;

/** Score parameters, weightages and insight benchmarks. Defaults come from the PRD; overrides live in the DB. */
@Service
public class ScoreConfigService {

    public static final Map<String, Double> DEFAULTS = Map.ofEntries(
            entry("cafe.weight.rating", 50.0),
            entry("cafe.weight.fillRate", 25.0),
            entry("cafe.weight.quality", 25.0),
            entry("sm.weight.attendance", 25.0),
            entry("sm.weight.punctuality", 25.0),
            entry("sm.weight.fillRate", 25.0),
            entry("sm.weight.quality", 25.0),
            entry("insight.fillRate.min", 50.0),
            entry("insight.quality.min", 50.0),
            entry("insight.punctuality.min", 85.0),
            entry("insight.maxCafesPerGap", 2.0),
            entry("trend.monthlyAfterWeeks", 8.0));

    private final ScoreConfigRepository repo;

    public ScoreConfigService(ScoreConfigRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public double get(String key) {
        return repo.findById(key).map(ScoreConfig::getValue).orElseGet(() -> DEFAULTS.getOrDefault(key, 0.0));
    }

    @Transactional(readOnly = true)
    public Map<String, Double> all() {
        Map<String, Double> m = new TreeMap<>(DEFAULTS);
        repo.findAll().forEach(c -> m.put(c.getKey(), c.getValue()));
        return m;
    }

    /** Weights for "cafe" or "sm" keyed by parameter (rating, fillRate, quality, attendance, punctuality). */
    @Transactional(readOnly = true)
    public Map<String, Double> weights(String scope) {
        String prefix = scope + ".weight.";
        Map<String, Double> out = new HashMap<>();
        all().forEach((k, v) -> { if (k.startsWith(prefix)) out.put(k.substring(prefix.length()), v); });
        return out;
    }

    @Transactional
    public void set(String key, double value) {
        if (!DEFAULTS.containsKey(key)) throw ApiException.badRequest("Unknown config key: " + key);
        if (value < 0) throw ApiException.badRequest("Value must be >= 0");
        ScoreConfig c = repo.findById(key).orElseGet(() -> new ScoreConfig(key, value));
        c.setValue(value);
        repo.save(c);
    }
}
