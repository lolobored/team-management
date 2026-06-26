package org.lolobored.tm.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapRunnerTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsAdmin_whenEmptyAndEnvPresent() throws Exception {
        UserRepository repo = mock(UserRepository.class);
        when(repo.count()).thenReturn(0L);
        when(repo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        new AdminBootstrapRunner(repo, encoder, "admin@example.com", "TempPass1234").run(null);

        verify(repo).save(argThat(u ->
            u.getEmail().equals("admin@example.com")
            && u.getRole() == Role.ADMIN
            && u.isEnabled()
            && u.isMustChangePassword()
            && encoder.matches("TempPass1234", u.getPasswordHash())));
    }

    @Test
    void noop_whenUsersAlreadyExist() throws Exception {
        UserRepository repo = mock(UserRepository.class);
        when(repo.count()).thenReturn(3L);

        new AdminBootstrapRunner(repo, encoder, "admin@example.com", "TempPass1234").run(null);

        verify(repo, never()).save(any());
    }

    @Test
    void noop_whenEnvMissing() throws Exception {
        UserRepository repo = mock(UserRepository.class);
        when(repo.count()).thenReturn(0L);

        new AdminBootstrapRunner(repo, encoder, "", "").run(null);

        verify(repo, never()).save(any());
    }
}
