package com.smartroute.smartroute1.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Data
@Entity
@Getter
@Setter
@ToString
public class Exercise {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String gifUrl;

    @ElementCollection
    @CollectionTable(name = "exercise_target_muscles", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "muscle", nullable = false)
    private List<String> targetMuscles;

    @ElementCollection
    @CollectionTable(name = "exercise_body_parts", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "body_part", nullable = false)
    private List<String> bodyParts;

    @ElementCollection
    @CollectionTable(name = "exercise_equipments", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "equipment", nullable = false)
    private List<String> equipments;

    @ElementCollection
    @CollectionTable(name = "exercise_secondary_muscles", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "muscle", nullable = false)
    private List<String> secondaryMuscles;

    @ElementCollection
    @CollectionTable(name = "exercise_instructions", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "instruction", nullable = false)
    private List<String> instructions;

}
