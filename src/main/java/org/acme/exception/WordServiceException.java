package org.acme.exception;

public class WordServiceException extends RuntimeException {

    public WordServiceException(String message) {
        super(message);
    }

    public WordServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public WordServiceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}