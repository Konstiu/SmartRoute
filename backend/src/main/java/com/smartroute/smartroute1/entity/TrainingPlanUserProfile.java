package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.WorkoutType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.FetchType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;


import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingPlanUserProfile {

    @Id
    private Long userId; // same as ApplicationUser.id

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    private ApplicationUser user;

    // Preference bias: positive => user tends to like/do it, negative => avoids it.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tp_user_type_bias", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "workout_type")
    @Column(name = "bias")
    private Map<WorkoutType, Double> typeBias = new EnumMap<>(WorkoutType.class);

    // Load multiplier per workout type (mean scaling)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tp_user_load_mult", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "workout_type")
    @Column(name = "multiplier")
    private Map<WorkoutType, Double> loadMultiplier = new EnumMap<>(WorkoutType.class);

    // Global uncertainty multiplier for std (based on adherence)
    @Column(nullable = false)
    private double uncertaintyScale = 1.0;

    @Column(nullable = false)
    private Instant updatedAt;
}
