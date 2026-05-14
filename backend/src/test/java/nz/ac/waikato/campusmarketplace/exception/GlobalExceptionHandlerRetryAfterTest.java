package nz.ac.waikato.campusmarketplace.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerRetryAfterTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionWithRetryAfterAddsHeader() {
        HttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        ApiException ex = new ApiException(ErrorCode.TOO_MANY_ATTEMPTS, "wait", 900L);

        ResponseEntity<ApiErrorResponse> response = handler.handleApi(ex, req);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("900");
        assertThat(response.getBody().code()).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void apiExceptionWithoutRetryAfterOmitsHeader() {
        HttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        ApiException ex = new ApiException(ErrorCode.BAD_CREDENTIALS, "nope");

        ResponseEntity<ApiErrorResponse> response = handler.handleApi(ex, req);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }
}
