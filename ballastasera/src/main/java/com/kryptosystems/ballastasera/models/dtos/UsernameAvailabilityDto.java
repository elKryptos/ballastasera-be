package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.UsernameStatus;
import lombok.Getter;
import lombok.Setter;

/** GET /users/username-available: si se puede elegir y, si no, por que. El propio cuenta como disponible. */
@Getter
@Setter
public class UsernameAvailabilityDto {
    private boolean available;
    /** INVALID, RESERVED o TAKEN; null si available. */
    private UsernameStatus reason;
}
