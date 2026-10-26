package com.example.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable Java record representing the raw payload returned by the 3rd-party API
 * (https://jsonplaceholder.typicode.com/posts).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalPostDto(
        Long id,
        Long userId,
        String title,
        String body
) {
}

