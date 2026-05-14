package nz.ac.waikato.campusmarketplace.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionTest {

    @Test
    void carriesCodeAndMessageAndStatus() {
        ApiException ex = new ApiException(ErrorCode.EMAIL_EXISTS,
                "This email is already registered. Log in instead?");
        assertEquals(ErrorCode.EMAIL_EXISTS, ex.getCode());
        assertEquals("This email is already registered. Log in instead?", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getCode().getStatus());
    }

    @Test
    void allErrorCodesHaveStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getStatus(), "ErrorCode " + code + " must have an HTTP status");
        }
    }
}
