package com.smartroute.smartroute1.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Data
public class Route {

    String name;
    Double distance;
    Double pace;
    Double elevation;
    @Lob
    String route;
    LocalDate creationDate;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "application_user_id")
    @JsonIgnore
    private ApplicationUser user;

    @ManyToMany
    private Set<ApplicationUser> shared;
}
