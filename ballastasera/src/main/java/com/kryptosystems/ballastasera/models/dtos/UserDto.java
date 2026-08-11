package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserDto {
    private UUID userId;
    private String email;
    private String displayName;
    private UserRole role;
    private String avatarUrl;
    private String instagram;
    private boolean showProfilePublic;
}