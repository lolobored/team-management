package org.lolobored.tm.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LoginLockoutTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private void seed(String email, String rawPassword) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(Instant.now());
        users.save(u);
    }

    private int login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @BeforeEach
    void clean() { users.deleteAll(); }

    @Test
    void fiveFailures_thenCorrectPassword_isLocked() throws Exception {
        seed("u@example.com", "Right-Password-1");
        for (int i = 0; i < 5; i++) {
            assertThat(login("u@example.com", "wrong")).isEqualTo(401);
        }
        // 6th attempt with the CORRECT password is rejected because the account is locked
        assertThat(login("u@example.com", "Right-Password-1")).isEqualTo(423);
        assertThat(users.findByEmail("u@example.com").orElseThrow().getLockedUntil()).isNotNull();
    }

    @Test
    void successBeforeThreshold_resetsCounter() throws Exception {
        seed("u@example.com", "Right-Password-1");
        login("u@example.com", "wrong");
        login("u@example.com", "wrong");
        assertThat(login("u@example.com", "Right-Password-1")).isEqualTo(200);
        assertThat(users.findByEmail("u@example.com").orElseThrow().getFailedAttempts()).isZero();
    }

    @Test
    void expiredLock_allowsLoginAndResets() throws Exception {
        seed("u@example.com", "Right-Password-1");
        User u = users.findByEmail("u@example.com").orElseThrow();
        u.setFailedAttempts(5);
        u.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES)); // already expired
        users.save(u);
        assertThat(login("u@example.com", "Right-Password-1")).isEqualTo(200);
        User after = users.findByEmail("u@example.com").orElseThrow();
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }
}
