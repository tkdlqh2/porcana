package com.porcana.global.exception;

/**
 * Exception thrown when an invalid operation is attempted
 * (e.g., trying to pick a risk profile in round 2)
 */
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
