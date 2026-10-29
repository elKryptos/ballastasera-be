package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.OrganizerRejectReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body de PATCH /rest/admin/organizer-requests/{id}/reject. El usuario ve motivo y mensaje en el menú. */
@Getter
@Setter
public class OrganizerRequestRejectDto {
    @NotNull
    private OrganizerRejectReason reason;

    /** «Messaggio per l'utente · facoltativo». Obligatorio si reason = OTHER (isMessageProvidedForOther). */
    @Size(max = 500)
    private String message;

    @AssertTrue(message = "message is required when reason is OTHER")
    public boolean isMessageProvidedForOther() {
        return reason != OrganizerRejectReason.OTHER || message != null && !message.isBlank();
    }
}
