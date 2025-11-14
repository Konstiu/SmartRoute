package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.util.Coordinate;
import com.smartroute.smartroute1.util.Sanitary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Setter
@Getter
@ToString
public class ViennaPoint {
    @Id
    private String id;

    @Column(nullable = false)
    private Coordinate coordinate;

    private Sanitary type;



    public Boolean isToilet() {
        return type.equals(Sanitary.Toilet);
    }
    public Boolean isFountain() {
        return type.equals(Sanitary.Fountain);
    }
}
