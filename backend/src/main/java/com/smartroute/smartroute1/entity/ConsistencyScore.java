package com.smartroute.smartroute1.entity;


import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
public class ConsistencyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant date;

    private double finalScore;
    private double frequencyConsistency;
    private double regularityConsistency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private ApplicationUser user;


    public ConsistencyScore(ApplicationUser user, ConsistencyScoreResultDto dto) {
        this.date = Instant.now();
        this.user = user;
        this.finalScore = dto.getFinalScore();
        this.frequencyConsistency = dto.getFrequencyConsistency();
        this.regularityConsistency = dto.getRegularityConsistency();
    }
}
