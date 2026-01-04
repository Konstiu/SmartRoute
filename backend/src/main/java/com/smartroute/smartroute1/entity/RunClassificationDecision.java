package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.RunType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class RunClassificationDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RunType runType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "run_classification_probabilities",
            joinColumns = @JoinColumn(name = "decision_id")
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "run_type")
    @Column(name = "probability")
    private Map<RunType, Double> probabilities;

    public RunClassificationDecision(Map<RunType, Double> probabilities, RunType runType) {
        this.probabilities = probabilities;
        this.runType = runType;
    }
}
