package com.campus.marketplace.dto.auth;

import com.campus.marketplace.model.User;
import lombok.Data;

import java.time.Instant;

@Data
public class UserDto {
    private Long id;
    private String email;
    private String name;
    private User.UserRole role;
    private Instant createdAt;
    // TODO: Add avatar URL
}
