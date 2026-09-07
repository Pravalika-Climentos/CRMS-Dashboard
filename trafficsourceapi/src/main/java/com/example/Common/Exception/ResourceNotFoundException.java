package com.example.Common.Exception;

import java.util.NoSuchElementException;

public class ResourceNotFoundException
        extends NoSuchElementException {

    public ResourceNotFoundException(
            String message
    ) {
        super(message);
    }
}