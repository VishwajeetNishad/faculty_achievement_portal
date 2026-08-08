package com.niet.facultyachievement.security;

import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

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

        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(roleName));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                authorities
        );
    }
}
