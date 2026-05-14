package nz.ac.waikato.campusmarketplace.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_EMAIL(HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST),
    EMAIL_EXISTS(HttpStatus.CONFLICT),
    NICKNAME_TAKEN(HttpStatus.CONFLICT),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    TOO_MANY_REGISTRATIONS(HttpStatus.TOO_MANY_REQUESTS);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
