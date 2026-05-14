package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceGetCurrentTest {

    @Test
    void returnsUserById() {
        UserRepository users = mock(UserRepository.class);
        User u = User.builder().id(7L).email("a@x.nz").nickname("A").password("h").build();
        when(users.findById(7L)).thenReturn(Optional.of(u));

        AuthService auth = new AuthService(users, null, null, null, null, null);
        User got = auth.getCurrentUser(7L);
        assertEquals(7L, got.getId());
    }

    @Test
    void throwsWhenUserMissing() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(99L)).thenReturn(Optional.empty());
        AuthService auth = new AuthService(users, null, null, null, null, null);
        ApiException ex = assertThrows(ApiException.class, () -> auth.getCurrentUser(99L));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
    }
}
