package com.example.docprocessing.exception;

public class DocumentTooLargeException extends RuntimeException {

    public DocumentTooLargeException(String message) {
        super(message);
    }

    public DocumentTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }
}