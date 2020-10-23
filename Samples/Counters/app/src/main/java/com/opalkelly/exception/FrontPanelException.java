package com.opalkelly.exception;

public class FrontPanelException extends Exception {
    public FrontPanelException() {
    }

    public FrontPanelException(String message) {
        super(message);
    }

    public FrontPanelException(Throwable cause) {
        super(cause);
    }

    public FrontPanelException(String message, Throwable cause) {
        super(message, cause);
    }
}
