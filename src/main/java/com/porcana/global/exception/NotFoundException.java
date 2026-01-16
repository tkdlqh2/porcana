package com.porcana.global.exception;

/**
 * Exception thrown when a requested resource is not found
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
