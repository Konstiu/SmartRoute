package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.BodyPart;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;

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

    @ManyToOne
    @JoinColumn(name = "application_user_id")
    private ApplicationUser applicationUser;
}
