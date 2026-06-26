package org.lolobored.tm.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String email, String password) {}
    public record MeResponse(String email, Role role, boolean mustChangePassword) {}

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserRepository users) {
        this.authenticationManager = authenticationManager;
        this.users = users;
    }

    @PostMapping("/login")
    public MeResponse login(@RequestBody LoginRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.email(), body.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password");
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        contextRepository.saveContext(context, request, response);

        User u = users.findByEmail(body.email()).orElseThrow();
        return new MeResponse(u.getEmail(), u.getRole(), u.isMustChangePassword());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User u = users.findByEmail(authentication.getName()).orElseThrow();
        return new MeResponse(u.getEmail(), u.getRole(), u.isMustChangePassword());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
