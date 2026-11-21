package com.hungerbox.wings.repo;

import com.hungerbox.wings.domain.RosterStatus;

import java.time.LocalDate;

/** Flat, scalar-only view of a roster (+ cafe + AM) so aggregation never loads entity graphs. */
public record RosterRow(Long smId, String smName, Long cafeId, String cafeName, Long amId, String amName,
                        LocalDate date, RosterStatus status, Boolean punched, Boolean ciOnTime, Boolean coOnTime,
                        Boolean ciLoc, Boolean coLoc) {}
