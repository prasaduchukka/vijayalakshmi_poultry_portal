package com.vpf.exception;

/** Thrown when a request violates a confirmed business rule (e.g. negative amounts). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
