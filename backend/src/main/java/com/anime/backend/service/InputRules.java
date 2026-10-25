package com.anime.backend.service;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class InputRules {
    private InputRules() { }

    public static String text(String value, String field, int minimum, int maximum) {
        if (value == null || value.trim().length() < minimum || value.trim().length() > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must contain " + minimum + " to " + maximum + " characters");
        }
        return value.trim();
    }

    public static void password(String value) {
        // BCrypt accepts at most 72 bytes, which is not always 72 characters.
        if (value == null || value.isBlank() || value.length() < 4
                || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must have at least 4 characters and at most 72 UTF-8 bytes");
        }
    }
}