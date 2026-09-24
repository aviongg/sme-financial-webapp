package com.app.sme_health_backend.shared.exception;

public class ActiveBusinessRequiredException extends RuntimeException {

    public ActiveBusinessRequiredException() {
        super("Select or create a business before using this feature.");
    }

    public ActiveBusinessRequiredException(String message) {
        super(message);
    }
}
