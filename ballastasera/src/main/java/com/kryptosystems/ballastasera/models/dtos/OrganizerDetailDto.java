package com.kryptosystems.ballastasera.models.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrganizerDetailDto {
    private UUID id;
    private String name;
    private String slug;
    private String type;
    private String description;
    private String logoUrl;
    private String website;
    private String phone;
    private String contactEmail;
    private String instagram;
    private String facebook;
    private boolean verified;
    /** D15: true si llegó por una CLAIM. En el perfil del dueño: false → «Elimina», true → «Deja el organizador creado». */
    private boolean claimed;
    /** Verificada y sin dueño (Organizers.isClaimable): el FE enseña «È la tua pagina?» en la serata y en el locale. */
    private boolean claimable;
}