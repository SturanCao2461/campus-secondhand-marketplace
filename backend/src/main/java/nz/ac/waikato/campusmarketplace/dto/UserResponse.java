package nz.ac.waikato.campusmarketplace.dto;

import nz.ac.waikato.campusmarketplace.entity.User;

public record UserResponse(Long id, String email, String nickname, boolean emailVerified) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getNickname(), u.isEmailVerified());
    }
}
