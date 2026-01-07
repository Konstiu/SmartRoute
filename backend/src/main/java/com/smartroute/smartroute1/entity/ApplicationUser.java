package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.ExperienceLevel;
import com.smartroute.smartroute1.entity.enums.Sex;
import com.smartroute.smartroute1.entity.enums.Weekday;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Data
@Entity
public class ApplicationUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 100)
    private String firstname;

    @Column(nullable = false, length = 100)
    private String lastname;

    @Column(nullable = false)
    private boolean verified = false;

    @Column
    private Sex sex;

    @Column
    private Integer height;

    @Column
    private BigDecimal weight;

    @Column
    private Integer ftp;

    @Column
    private LocalDate birthdate;

    @Column
    private ExperienceLevel experienceLevel;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @ToString.Exclude
    private Set<Weekday> activeWeekdays = new HashSet<>();

    @Column
    private String publicIdentityKey;

    @Column
    private String publicIdentityDHKey;

    @Column
    private String publicPreKey;

    @Column
    private String preKeySignature;

    @OneToMany(
        mappedBy = "user",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<PreKey> oneTimePreKeys = new ArrayList<>();

    public ApplicationUser() {
    }

    public ApplicationUser(String email, String password, String firstname, String lastname) {
        this.email = email;
        this.password = password;
        this.firstname = firstname;
        this.lastname = lastname;
        this.verified = false;

    }

    @Override
    public String toString() {
        return "email=" + email + ", password=" + password + ", firstname=" + firstname + ", lastname=" + lastname;
    }
}

