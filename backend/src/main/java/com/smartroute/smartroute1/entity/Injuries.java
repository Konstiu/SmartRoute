package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.BodyPart;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Entity
@NoArgsConstructor
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

    public Injuries(ApplicationUser user, int i, BodyPart area, LocalDate localDate, LocalDate lastInjuryDate) {
        this.applicationUser = user;
        this.injuryIndex = i;
        this.affectedArea = area;
        this.lastHealthyDate = localDate;
        this.lastInjuryDate = lastInjuryDate;
    }
}
