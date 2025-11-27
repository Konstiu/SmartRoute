package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Data
public class UserDetailDto {
    public String firstname;
    public String lastname;
    public String email;
    private Sex sex;
    private Integer height;
    private BigDecimal weight;
    private LocalDate birthdate;
    private ExperienceLevel experienceLevel;
    private Set<Weekday> activeWeekdays;
}
