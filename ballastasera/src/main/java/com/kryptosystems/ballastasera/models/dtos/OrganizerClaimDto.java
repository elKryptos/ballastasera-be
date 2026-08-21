package com.kryptosystems.ballastasera.models.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrganizerClaimDto {
    @NotNull
    private UUID userId;
}
