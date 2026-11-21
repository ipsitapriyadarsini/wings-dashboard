package com.hungerbox.wings.service;

import java.time.LocalDate;

public record DateRange(RangePreset preset, LocalDate from, LocalDate to) {}
