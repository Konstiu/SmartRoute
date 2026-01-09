package com.smartroute.smartroute1.endpoint.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SaveRouteDto {
    String name;
    Double distance;
    Double pace;
    Double elevation;
    String route;
}
