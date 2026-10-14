package ru.twilson.wakeup.service;

public class ShutdownException extends RuntimeException {

    public ShutdownException(String message) {
        super(message);
    }

    public ShutdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
