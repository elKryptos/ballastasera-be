package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.OrganizerType;
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
    private String instagram;
    private String whatsapp;
    private String type;
    private boolean verified;
}
