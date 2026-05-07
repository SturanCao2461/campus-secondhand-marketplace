package nz.ac.waikato.campusmarketplace.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank String email) {}
