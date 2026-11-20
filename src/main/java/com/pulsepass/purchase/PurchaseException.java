package com.pulsepass.purchase;

import org.springframework.http.HttpStatus;

public class PurchaseException extends RuntimeException {
    public final String code;
    public final HttpStatus status;
    public PurchaseException(String code, HttpStatus status, String message) {
        super(message); this.code = code; this.status = status;
    }
}
