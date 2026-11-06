package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Lista de organizadores del admin (D15). Con dueño y claimed = true se puede hacer unclaim; con dueño y
 * claimed = false lo creó su dueño con una NEW y solo se borra. Lleva el email del dueño: solo para el admin,
 * nunca en endpoints públicos. */
@Getter
@Setter
public class OrganizerAdminDto {
    private UUID id;
    private String name;
    private String slug;
    private String logoUrl;
    private String instagram;
    private String type;
    private boolean verified;
    private boolean claimed;
    private UUID ownerId;
    private String ownerName;
    private String ownerEmail;
}
