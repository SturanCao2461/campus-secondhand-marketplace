package nz.ac.waikato.campusmarketplace.filter;

public record AuthPrincipal(Long userId, String email, String nickname) {}
