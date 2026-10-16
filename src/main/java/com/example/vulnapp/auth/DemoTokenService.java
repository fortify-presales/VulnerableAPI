package com.example.vulnapp.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.example.vulnapp.util.CryptoUtil;
import org.springframework.stereotype.Service;

@Service
public class DemoTokenService {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Supplier<Instant> currentTime;

    public DemoTokenService() {
        this(Instant::now);
    }

    public DemoTokenService(Supplier<Instant> currentTime) {
        this.currentTime = currentTime;
    }

    public String issueToken(String username) {
        Instant now = currentTime.get();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Session session = new Session(username, now.plus(1, ChronoUnit.HOURS));
        String token;
        do {
            token = CryptoUtil.generateSessionToken();
        } while (sessions.putIfAbsent(token, session) != null);
        return token;
    }

    public String findUsername(String token) {
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (!session.expiresAt().isAfter(currentTime.get())) {
            sessions.remove(token, session);
            return null;
        }
        return session.username();
    }

    private record Session(String username, Instant expiresAt) {
    }
}