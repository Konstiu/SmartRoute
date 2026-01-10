package com.smartroute.smartroute1.endpoint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
