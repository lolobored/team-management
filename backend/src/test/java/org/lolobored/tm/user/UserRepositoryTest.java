package org.lolobored.tm.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired private UserRepository repository;

    private User newUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(true);
        u.setCreatedAt(Instant.now());
        return u;
    }

    @Test
    void findByEmail_returnsSavedUser() {
        repository.save(newUser("a@example.com"));
        assertThat(repository.findByEmail("a@example.com")).isPresent();
        assertThat(repository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void existsByEmail_reflectsPresence() {
        repository.save(newUser("b@example.com"));
        assertThat(repository.existsByEmail("b@example.com")).isTrue();
        assertThat(repository.existsByEmail("nope@example.com")).isFalse();
    }
}
