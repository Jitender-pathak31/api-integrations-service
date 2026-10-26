package com.example.integration;

/**
 * Payload for editing an integrated record locally before re-exposing it.
 * All fields are optional so callers can update only what they need.
 */
public record PostEditRequest(
        String title,
        String body,
        String curatorNotes,
        Boolean approved
) {
}

