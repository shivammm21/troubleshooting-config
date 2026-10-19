package com.stackpilot.backend.security;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Per-process token that the desktop UI sends as {@code X-StackPilot-Token}.
 * Generated once when the Spring context starts; never written to disk.
 */
@Service
public class SessionTokenService {

    private final String token = UUID.randomUUID().toString();

    public String token() {
        return token;
    }

    public boolean matches(String headerValue) {
        return headerValue != null && headerValue.equals(token);
    }
}
