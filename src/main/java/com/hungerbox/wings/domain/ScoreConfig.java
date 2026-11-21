package com.hungerbox.wings.domain;

import jakarta.persistence.*;

/** Runtime-configurable score weightages and insight benchmarks (key/value). */
@Entity
@Table(name = "score_config")
public class ScoreConfig {
    @Id @Column(name = "config_key")
    private String key;
    @Column(name = "config_value", nullable = false)
    private double value;

    protected ScoreConfig() {}
    public ScoreConfig(String key, double value) { this.key = key; this.value = value; }

    public String getKey() { return key; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}
