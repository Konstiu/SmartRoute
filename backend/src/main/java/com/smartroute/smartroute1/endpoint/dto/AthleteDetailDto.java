package com.smartroute.smartroute1.endpoint.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class AthleteDetailDto {

    private String sex;

    private Integer ftp;

    private Float weight;
}
