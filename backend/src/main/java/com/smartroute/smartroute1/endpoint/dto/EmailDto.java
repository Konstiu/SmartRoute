package com.smartroute.smartroute1.endpoint.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailDto {
    @NotBlank(message = "Email is mandatory")
    @Email(message = "Email should be valid")
    public String email;
}
