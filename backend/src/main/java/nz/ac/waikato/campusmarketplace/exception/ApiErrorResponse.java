package nz.ac.waikato.campusmarketplace.exception;

public record ApiErrorResponse(String code, String message) {
    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(code.name(), message);
    }
}
