package com.smartroute.smartroute1.endpoint.dto;

import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@Data
@NoArgsConstructor
@Getter
@Setter
public class ViewRouteDto {
    Long id;
    String name;
    Double distance;
    Double pace;
    Double elevation;
    String route;
    LocalDate creationDate;

}
