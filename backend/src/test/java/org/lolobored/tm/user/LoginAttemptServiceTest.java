package org.lolobored.tm.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(Instant.now());
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        return u;
    }

    @Test
    void recordFailure_increments() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordFailure("a@x.com");
        assertThat(u.getFailedAttempts()).isEqualTo(1);
        assertThat(u.getLockedUntil()).isNull();
        verify(repo).save(u);
    }

    @Test
    void recordFailure_locksOnThreshold() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setFailedAttempts(4);
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        Instant before = Instant.now();
        new LoginAttemptService(repo, 5, 15).recordFailure("a@x.com");
        assertThat(u.getFailedAttempts()).isEqualTo(5);
        assertThat(u.getLockedUntil()).isNotNull();
        assertThat(u.getLockedUntil()).isAfter(before.plus(14, ChronoUnit.MINUTES));
    }

    @Test
    void recordFailure_afterExpiredLock_resetsThenIncrements() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setFailedAttempts(5);
        u.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES)); // expired
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordFailure("a@x.com");
        assertThat(u.getFailedAttempts()).isEqualTo(1);
        assertThat(u.getLockedUntil()).isNull();
    }

    @Test
    void recordFailure_unknownEmail_isNoOp() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByEmail("missing@x.com")).thenReturn(Optional.empty());
        new LoginAttemptService(repo, 5, 15).recordFailure("missing@x.com");
        verify(repo, never()).save(any());
    }

    @Test
    void recordSuccess_resetsCounterAndLock() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setFailedAttempts(3);
        u.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordSuccess("a@x.com");
        assertThat(u.getFailedAttempts()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        verify(repo).save(u);
    }

    @Test
    void recordSuccess_noChange_skipsSave() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com"); // already 0 / null
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordSuccess("a@x.com");
        verify(repo, never()).save(any());
    }

    @Test
    void minutesRemaining_ceilsToMinutes() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setLockedUntil(Instant.now().plus(90, ChronoUnit.SECONDS)); // 1.5 min
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        assertThat(new LoginAttemptService(repo, 5, 15).minutesRemaining("a@x.com")).isEqualTo(2);
    }
}
