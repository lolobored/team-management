package org.lolobored.tm.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    public record CreateUserRequest(String email, Role role, String initialPassword) {}
    public record RoleRequest(Role role) {}
    public record ResetPasswordRequest(String initialPassword) {}
    public record EnabledRequest(boolean enabled) {}
    public record UserDto(Long id, String email, Role role, boolean enabled,
                          boolean mustChangePassword, Instant createdAt) {
        static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getRole(), u.isEnabled(),
                    u.isMustChangePassword(), u.getCreatedAt());
        }
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<UserDto> list() {
        return users.findAll().stream().map(UserDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@RequestBody CreateUserRequest body) {
        if (body.email() == null || body.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (body.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }
        if (body.initialPassword() == null || body.initialPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "initial password is required");
        }
        if (users.existsByEmail(body.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
        }
        User u = new User();
        u.setEmail(body.email());
        u.setPasswordHash(encoder.encode(body.initialPassword()));
        u.setRole(body.role());
        u.setEnabled(true);
        u.setMustChangePassword(true);
        u.setCreatedAt(Instant.now());
        return UserDto.from(users.save(u));
    }

    @PatchMapping("/{id}/role")
    public UserDto changeRole(@PathVariable Long id, @RequestBody RoleRequest body, Authentication auth) {
        User u = find(id);
        if (isSelf(u, auth) && body.role() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot change your own admin role");
        }
        u.setRole(body.role());
        return UserDto.from(users.save(u));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest body) {
        if (body.initialPassword() == null || body.initialPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "initial password is required");
        }
        User u = find(id);
        u.setPasswordHash(encoder.encode(body.initialPassword()));
        u.setMustChangePassword(true);
        users.save(u);
    }

    @PatchMapping("/{id}/enabled")
    public UserDto setEnabled(@PathVariable Long id, @RequestBody EnabledRequest body, Authentication auth) {
        User u = find(id);
        if (isSelf(u, auth) && !body.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot disable your own account");
        }
        u.setEnabled(body.enabled());
        return UserDto.from(users.save(u));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        User u = find(id);
        if (isSelf(u, auth)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot delete your own account");
        }
        users.delete(u);
    }

    private User find(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private boolean isSelf(User u, Authentication auth) {
        return u.getEmail().equals(auth.getName());
    }
}
