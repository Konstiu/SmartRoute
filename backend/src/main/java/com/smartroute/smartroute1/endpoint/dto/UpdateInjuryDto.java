package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.BodyPart;
import lombok.Data;

import java.time.LocalDate;


@Data
public class UpdateInjuryDto {

    private Long  injuryId;

    /**
     * Global InjuryIndex in [0, 1].
     */
    private double injuryIndex;

    /**
     * Affected Area.
     */
    private BodyPart affectedArea;

    /**
     * The last day when the user was healthy/ i.e., the date of the Injury or infection.
     */
    private LocalDate lastHealthyDate;

    /**
     * If the user says the injury or infection healed.
     */
    private LocalDate lastInjuryDate;
}
