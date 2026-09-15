package com.example.Common.Exception;

public class ForbiddenOperationException
        extends RuntimeException {

    public ForbiddenOperationException(
            String message
    ) {
        super(message);
    }
}