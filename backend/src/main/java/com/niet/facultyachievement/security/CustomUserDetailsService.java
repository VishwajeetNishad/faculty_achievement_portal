package com.niet.facultyachievement.security;

import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserPermissionResolver userPermissionResolver;

    /**
     * Loads a user and turns them into a Spring Security principal.
     *
     * <p>This method runs on EVERY authenticated request, because
     * {@code JwtAuthenticationFilter} re-reads the user from the database each
     * time rather than trusting whatever was baked into the JWT. That has a
     * useful consequence: when an administrator grants or revokes a
     * permission, or deactivates an account, it takes effect on the user's very
     * next request — no logout, no new token, no waiting for the old one to
     * expire.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByEmployeeId(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email or employee ID: " + usernameOrEmail));

        String roleName = user.getRole() != null ? user.getRole().getName() : "FACULTY";
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        List<GrantedAuthority> authorities = new ArrayList<>();

        // 1) The role authority, exactly as before. Every existing
        //    hasRole('ADMIN') / hasAuthority('ROLE_HOD') check keeps working —
        //    permissions are added alongside roles, never instead of them.
        authorities.add(new SimpleGrantedAuthority(roleName));

        // 2) The user's individual permissions, read from the database only.
        //    A permission code becomes an authority with no prefix (e.g.
        //    CREATE_FACULTY), which is what hasAuthority('CREATE_FACULTY')
        //    matches. Spring's hasRole() only ever looks at ROLE_-prefixed
        //    authorities, so the two kinds cannot be confused with each other.
        Set<String> permissionCodes = userPermissionResolver.resolvePermissionCodes(user);
        for (String code : permissionCodes) {
            authorities.add(new SimpleGrantedAuthority(code));
        }

        // A user is only usable while their account is ACTIVE. Passing this as
        // the "enabled" flag makes Spring Security reject INACTIVE and
        // SUSPENDED accounts with a DisabledException during login, and stops
        // an already-issued JWT from working after the account is switched off.
        // Without it, deactivating a user would change nothing at all.
        boolean enabled = user.getStatus() == UserStatus.ACTIVE;

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                enabled,
                true,   // accountNonExpired  — accounts do not expire in this system
                true,   // credentialsNonExpired — passwords do not expire in this system
                true,   // accountNonLocked   — lockout is handled by LoginRateLimiter instead
                authorities
        );
    }
}
