package com.porcana.global.exception;

/**
 * Exception thrown when there are not enough assets available
 * for arena recommendations
 */
public class InsufficientAssetsException extends RuntimeException {
    public InsufficientAssetsException(String message) {
        super(message);
    }
}
