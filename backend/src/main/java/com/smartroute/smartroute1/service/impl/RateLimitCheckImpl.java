package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.exception.RateLimitExceededException;
import com.smartroute.smartroute1.service.RateLimitCheck;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitCheckImpl implements RateLimitCheck {

    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    public void check(String email, String type) {
        Instant now = Instant.now();
        String key = type.toLowerCase() + ":" + email.toLowerCase();

        Deque<Instant> deque = requests.computeIfAbsent(key, k -> new ArrayDeque<>());

        Instant cutoff = now.minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }

        if (deque.size() >= MAX_REQUESTS) {
            throw new RateLimitExceededException(
                    "Too many " + type + " requests. Try again in a few minutes.");
        }

        deque.addLast(now);
    }
}
