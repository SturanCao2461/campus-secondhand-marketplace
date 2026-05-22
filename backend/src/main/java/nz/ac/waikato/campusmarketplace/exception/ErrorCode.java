package nz.ac.waikato.campusmarketplace.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_EMAIL(HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    EMAIL_EXISTS(HttpStatus.CONFLICT),
    NICKNAME_TAKEN(HttpStatus.CONFLICT),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    TOO_MANY_REGISTRATIONS(HttpStatus.TOO_MANY_REQUESTS),

    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOT_LISTING_OWNER(HttpStatus.FORBIDDEN),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST),
    LISTING_REMOVED(HttpStatus.BAD_REQUEST),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST),
    MISSING_IMAGE(HttpStatus.BAD_REQUEST),
    INVALID_PRICE(HttpStatus.BAD_REQUEST),

    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONVERSATION_FORBIDDEN(HttpStatus.FORBIDDEN),
    CANNOT_MESSAGE_SELF(HttpStatus.BAD_REQUEST),
    MESSAGE_CONTENT_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
