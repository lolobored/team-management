package org.lolobored.tm.user;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
        "/api/auth/me", "/api/auth/logout", "/api/auth/change-password");

    private final UserRepository users;

    public MustChangePasswordFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        if (authenticated && !ALLOWED.contains(request.getRequestURI())) {
            User u = users.findByEmail(auth.getName()).orElse(null);
            if (u != null && u.isMustChangePassword()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Password change required");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
