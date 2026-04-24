package com.securepay.common.error;

public final class ErrorCode {
    public static final String VALIDATION = "VALIDATION_ERROR";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String DUPLICATE_REQUEST = "DUPLICATE_REQUEST";
    public static final String INTERNAL = "INTERNAL_ERROR";

    private ErrorCode() {}
}
