package org.lolobored.tm.user;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User u = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return org.springframework.security.core.userdetails.User.builder()
                .username(u.getEmail())
                .password(u.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))
                .disabled(!u.isEnabled())
                .build();
    }
}
