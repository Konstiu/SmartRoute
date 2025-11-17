package com.smartroute.smartroute1.util;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Embeddable
@NotNull
@Getter
@Setter
@ToString
public class Coordinate {
    @NotNull
    private Double latitude;
    @NotNull
    private Double longitude;

    public Coordinate(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Coordinate() {
        latitude = 0.0;
        longitude = 0.0;
    }

}
