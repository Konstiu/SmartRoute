package com.smartroute.smartroute1.endpoint.dto.subscription;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageDto {
    @NotNull
    String title;
    @NotNull
    String body;
}
