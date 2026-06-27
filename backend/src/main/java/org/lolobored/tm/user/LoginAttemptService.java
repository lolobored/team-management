package org.lolobored.tm.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LoginAttemptService {

    private final UserRepository users;
    private final int maxAttempts;
    private final long lockoutMinutes;

    public LoginAttemptService(UserRepository users,
                               @Value("${app.security.lockout.max-attempts:5}") int maxAttempts,
                               @Value("${app.security.lockout.minutes:15}") long lockoutMinutes) {
        this.users = users;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    public void recordFailure(String email) {
        User u = users.findByEmail(email).orElse(null);
        if (u == null) {
            return; // unknown email — cannot lock a non-existent account
        }
        Instant now = Instant.now();
        if (u.getLockedUntil() != null && !u.getLockedUntil().isAfter(now)) {
            // previous lock has expired — start a fresh window
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
        }
        u.setFailedAttempts(u.getFailedAttempts() + 1);
        if (u.getFailedAttempts() >= maxAttempts) {
            u.setLockedUntil(now.plus(lockoutMinutes, ChronoUnit.MINUTES));
        }
        users.save(u);
    }

    public void recordSuccess(String email) {
        User u = users.findByEmail(email).orElseThrow();
        if (u.getFailedAttempts() != 0 || u.getLockedUntil() != null) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            users.save(u);
        }
    }

    /** Minutes (ceiling, ≥1) until the lock expires; falls back to the window if absent. */
    public long minutesRemaining(String email) {
        User u = users.findByEmail(email).orElse(null);
        if (u == null || u.getLockedUntil() == null) {
            return lockoutMinutes;
        }
        long seconds = u.getLockedUntil().getEpochSecond() - Instant.now().getEpochSecond();
        if (seconds <= 0) {
            return 1;
        }
        return (seconds + 59) / 60;
    }
}
