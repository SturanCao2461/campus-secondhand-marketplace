package nz.ac.waikato.campusmarketplace.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionRetryAfterTest {

    @Test
    void defaultConstructorHasNoRetryAfter() {
        ApiException ex = new ApiException(ErrorCode.BAD_CREDENTIALS, "wrong");
        assertThat(ex.getRetryAfterSeconds()).isNull();
    }

    @Test
    void constructorWithRetryAfterStoresValue() {
        ApiException ex = new ApiException(ErrorCode.TOO_MANY_ATTEMPTS, "slow down", 900L);
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(900L);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS);
        assertThat(ex.getMessage()).isEqualTo("slow down");
    }
}
