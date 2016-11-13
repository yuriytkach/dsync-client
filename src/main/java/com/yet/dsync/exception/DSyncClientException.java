package com.yet.dsync.exception;

public class DSyncClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DSyncClientException(String message) {
        super(message);
    }

    public DSyncClientException(Throwable cause) {
        super(cause);
    }

    public DSyncClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public DSyncClientException(String message, Throwable cause,
            boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
