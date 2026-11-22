package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.ReportReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body de POST /rest/users/{id}/report (menu ⋯ del perfil, «Segnala»). */
@Getter
@Setter
public class UserReportCreateDto {
    @NotNull
    private ReportReason reason;

    /** Obligatoria si reason = OTHER (isNoteProvidedForOther). */
    @Size(max = 500)
    private String note;

    @AssertTrue(message = "note is required when reason is OTHER")
    public boolean isNoteProvidedForOther() {
        return reason != ReportReason.OTHER || note != null && !note.isBlank();
    }
}
