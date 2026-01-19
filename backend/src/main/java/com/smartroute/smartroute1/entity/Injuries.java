package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.BodyPart;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
@Entity
public class Injuries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2000)
    private double injuryIndex;

    @Column(length = 2000)
    private BodyPart affectedArea;

    @Column(length = 2000)
    private LocalDate lastHealthyDate;

    @Column(length = 2000)
    private LocalDate lastInjuryDate;

    @ManyToMany
    private Set<ApplicationUser> applicationUser;
}
