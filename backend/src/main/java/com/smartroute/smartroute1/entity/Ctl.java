package com.smartroute.smartroute1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Ctl {

    @ManyToOne
    ApplicationUser user;

    Double score;
    Instant date;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
