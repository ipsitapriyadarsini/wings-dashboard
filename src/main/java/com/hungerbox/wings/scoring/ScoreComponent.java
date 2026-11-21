package com.hungerbox.wings.scoring;

/**
 * One scored parameter. valuePct is the raw percentage (null = N/A); weightage is the effective weight after
 * redistribution among available parameters; points = valuePct x weightage / 100.
 */
public record ScoreComponent(String key, Double valuePct, Double weightage, Double points) {}
