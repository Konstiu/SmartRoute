package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.enums.BodyPart;
import java.time.LocalDate;

public class InjuryPeriod {
    private final LocalDate start;
    private final LocalDate end;
    private final BodyPart bodyPart;
    private final double injuryIndex;

    public InjuryPeriod(LocalDate start, LocalDate end, BodyPart bodyPart, double injuryIndex) {
        this.start = start;
        this.end = end;
        this.bodyPart = bodyPart;
        this.injuryIndex = injuryIndex;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public BodyPart getBodyPart() {
        return bodyPart;
    }

    public double getInjuryIndex() {
        return injuryIndex;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}