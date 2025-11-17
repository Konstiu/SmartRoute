package com.smartroute.smartroute1.endpoint.dto;

import com.smartroute.smartroute1.entity.enums.Sanitary;
import com.smartroute.smartroute1.util.Coordinate;
import lombok.Data;


@Data
public class ViennaPointDto {
    private String id;
    private Coordinate coordinate;
    private Sanitary type;
}
