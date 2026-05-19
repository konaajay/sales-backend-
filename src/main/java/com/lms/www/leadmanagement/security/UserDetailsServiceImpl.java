package com.lms.www.leadmanagement.security;

import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@lombok.extern.slf4j.Slf4j
@Service
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User lookup failed: {}", email);
                    return new UsernameNotFoundException("Invalid credentials");
                });

        if (!user.isActive()) {
            log.warn("Inactive user login attempt: {}", email);
            throw new org.springframework.security.authentication.DisabledException("Account inactive");
        }

        return UserDetailsImpl.build(user);
    }
}