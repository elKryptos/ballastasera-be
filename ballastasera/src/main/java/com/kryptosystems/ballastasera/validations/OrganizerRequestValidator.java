package com.kryptosystems.ballastasera.validations;

import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.models.dtos.OrganizerRequestCreateDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OrganizerRequestValidator implements ConstraintValidator<ValidOrganizerRequest, OrganizerRequestCreateDto> {

    @Override
    public boolean isValid(OrganizerRequestCreateDto dto, ConstraintValidatorContext context) {
        // type null lo gestiona el @NotNull del dto
        if (dto == null || dto.getType() == null) return true;

        context.disableDefaultConstraintViolation();

        // CLAIM: solo hace falta el organizador pedido; el resto de campos de perfil y la dirección se ignoran (D6)
        if (dto.getType() == OrganizerRequestType.CLAIM) {
            if (dto.getTargetOrganizerId() != null) return true;
            return violation(context, "targetOrganizerId", "targetOrganizerId is required for CLAIM");
        }

        boolean valid = true;
        if (dto.getName() == null || dto.getName().isBlank()) {
            valid = violation(context, "name", "name is required for NEW");
        }
        if (dto.getOrganizerType() == null) {
            // sin tipo no se pueden comprobar las reglas del local
            return violation(context, "organizerType", "organizerType is required for NEW");
        }

        boolean hasAddress = dto.getVenueAddress() != null && !dto.getVenueAddress().isBlank();
        boolean hasCity = dto.getVenueCityId() != null;
        if (hasAddress != hasCity) {
            return violation(context, "venueAddress", "venueAddress and venueCityId go together");
        }
        // D11: CLUB/VENUE/SCHOOL necesitan la dirección; PERSON/ASSOCIATION no pueden traerla
        if (dto.getOrganizerType().requiresVenue() && !hasAddress) {
            valid = violation(context, "venueAddress", "venueAddress is required for this organizer type");
        } else if (!dto.getOrganizerType().requiresVenue() && hasAddress) {
            valid = violation(context, "venueAddress", "venueAddress is not allowed for this organizer type");
        }
        return valid;
    }

    /** Asocia el error al campo (errors.<field> en el 400), igual que EventTimingValidator con endAt. */
    private boolean violation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
}
