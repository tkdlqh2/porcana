package com.porcana.global.exception;

/**
 * Exception thrown when a user attempts to access a resource they don't own
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
