package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.enums.BodyPart;

import java.time.LocalDate;

public record InjuryPeriod(LocalDate start, LocalDate end, BodyPart bodyPart, double injuryIndex) {

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}