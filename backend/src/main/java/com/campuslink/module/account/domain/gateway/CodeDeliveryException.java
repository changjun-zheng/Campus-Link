package com.campuslink.module.account.domain.gateway;

/** Transport failure without recipient, credentials, message content or provider exception details. */
public final class CodeDeliveryException extends RuntimeException {
    public CodeDeliveryException() {
        super("Code delivery unavailable");
    }
}
