package com.smartroute.smartroute1.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalDataDto {
    private Sex sex;

    @Min(1)
    @Max(300)
    private Integer height;

    @DecimalMin("0.1")
    @DecimalMax("300")
    private BigDecimal weight;

    @Past
    private LocalDate birthdate;

    private ExperienceLevel experienceLevel;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Set<Weekday> activeWeekdays;
}
