package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Formulario completo del perfil (PATCH /auth/me): el front manda siempre todo,
 * un campo que no viene queda en null (danceStyles null = sin estilos).
 * Los estilos se sincronizan por diferencia en el service.
 */
@Getter
@Setter
public class UserUpdateDto {

    private static final int MIN_AGE = 16;
    private static final int MAX_AGE = 100;

    /** Handle de Instagram, con o sin @ inicial (se normaliza en el service). */
    @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "invalid Instagram handle")
    private String instagram;

    @NotNull
    private Boolean showProfilePublic;

    /** Formato internacional E.164: + prefijo de pais y numero, sin espacios. */
    @Pattern(regexp = "^$|^\\+[1-9]\\d{6,14}$", message = "whatsapp must be in E.164 format, e.g. +393331234567")
    private String whatsapp;

    /** Null se guarda como MUTUALS, solo amigos (defaultValue en UserMapper, igual que el DEFAULT de la DB).
     * Quien no quiere mostrarlo manda NOBODY. */
    private ContactVisibility whatsappVisibility;

    @Past
    private LocalDate birthDate;

    private Gender gender;

    private DanceRole danceRole;

    @Size(max = 10, message = "at most 10 dance styles")
    private List<@Valid @NotNull UserDanceStyleUpdateDto> danceStyles;

    @Size(max = 150, message = "bio must be at most 150 characters")
    private String bio;

    /** Quien ve las serate a las que va. Null se guarda como EVERYONE (como el DEFAULT de la DB). */
    private ContactVisibility attendanceVisibility;

    /** Escuela enlazada: tiene que ser un organizer de tipo SCHOOL. Si viene, schoolName se ignora. */
    private UUID schoolOrganizerId;

    /** Escuela escrita a mano, cuando no esta en la app. */
    @Size(max = 80, message = "school name must be at most 80 characters")
    private String schoolName;

    @Pattern(regexp = "^[A-Z]{2}$", message = "originCountry must be an ISO 3166-1 alpha-2 code, e.g. CO")
    private String originCountry;

    /** Null = sin ciudad. */
    @Valid
    private HomeCityDto homeCity;

    /** «Mostra la città nel profilo». Null se guarda como false. */
    private Boolean showHomeCity;

    @AssertTrue(message = "age must be between 16 and 100")
    public boolean isBirthDateValid() {
        if (birthDate == null) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return !birthDate.isAfter(today.minusYears(MIN_AGE)) && birthDate.isAfter(today.minusYears(MAX_AGE));
    }
}