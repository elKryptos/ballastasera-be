package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrganizerSummaryDto {
    private UUID id;
    private String name;
    private String slug;
    private String logoUrl;
    private boolean verified;
}
