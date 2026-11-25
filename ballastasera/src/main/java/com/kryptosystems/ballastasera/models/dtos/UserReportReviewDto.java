package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.ReportStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Body de PATCH /rest/admin/reports/{id}: «Ignora» (DISMISSED) o «Banna» (BANNED). */
@Getter
@Setter
public class UserReportReviewDto {
    @NotNull
    private ReportStatus status;

    @AssertTrue(message = "status must be DISMISSED or BANNED")
    public boolean isStatusValid() {
        return status != ReportStatus.OPEN;
    }
}
