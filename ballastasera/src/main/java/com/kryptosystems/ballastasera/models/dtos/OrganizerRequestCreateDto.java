package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.enums.OrganizerType;
import com.kryptosystems.ballastasera.validations.ValidOrganizerRequest;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

/**
 * Body de POST /rest/organizer-requests: el wizard «Diventa organizzatore».
 * NEW: perfil nuevo (name + organizerType obligatorios; dirección si el tipo tiene local, D11).
 * CLAIM: pide una página que ya existe (targetOrganizerId obligatorio); solo lleva datos de prueba (D6),
 * así que name, organizerType, description, website, facebook y la dirección se ignoran.
 * Las reglas que dependen del tipo las valida @ValidOrganizerRequest.
 */
@Getter
@Setter
@ValidOrganizerRequest
public class OrganizerRequestCreateDto {

    @NotNull
    private OrganizerRequestType type;

    /** Solo CLAIM: id de la página (verificada y sin reclamar) que el usuario pide. */
    private UUID targetOrganizerId;

    /** Solo NEW (obligatorio). */
    @Size(max = 120)
    @Pattern(regexp = "(?s).*\\S.*", message = "name must not be blank")
    private String name;

    /** Solo NEW (obligatorio). Passo 1: Insegnante/DJ/promoter=PERSON, Scuola=SCHOOL,
     *  Discoteca/club=CLUB, Locale/bar=VENUE, Associazione=ASSOCIATION. */
    private OrganizerType organizerType;

    @Size(max = 500)
    private String description;

    /** Obligatorio en los dos tipos: el admin lo usa para verificar que el perfil es suyo. Con o sin "@"
     *  (el servicio lo normaliza con InstagramUtils). */
    @NotBlank
    @Pattern(regexp = "^@?[A-Za-z0-9._]{1,30}$", message = "invalid Instagram handle")
    private String instagram;

    /** Contacto público del organizador (NEW) o prueba de identidad (CLAIM). No es el destino de los
     *  emails de aviso: esos van al email del login. El wizard lo precarga con el email del usuario. */
    @NotBlank
    @Email
    @Size(max = 100)
    private String contactEmail;

    @Size(max = 20)
    private String phone;

    @URL
    @Size(max = 100)
    private String website;

    @URL
    @Size(max = 100)
    private String facebook;

    /** Nota libre para el admin (sobre todo en CLAIM: «sono la titolare, scrivetemi a…»). */
    @Size(max = 500)
    private String message;

    /** Solo NEW de tipo local, paso «Il tuo locale» (D11): dirección para que el admin cree el venue
     *  al aprobar. Van las dos juntas o ninguna (también lo garantiza chk_org_request_venue_proposal). */
    @Size(max = 150)
    private String venueAddress;

    @Positive
    private Long venueCityId;

    /** Checkbox del Passo 3 («Pubblicherò solo serate reali…»). El servicio guarda la fecha en termsAcceptedAt. */
    @AssertTrue(message = "terms must be accepted")
    private boolean termsAccepted;
}
