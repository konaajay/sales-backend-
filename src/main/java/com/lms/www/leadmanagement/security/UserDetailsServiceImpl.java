package com.lms.www.leadmanagement.security;

import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        if (!user.isActive()) {
            throw new UsernameNotFoundException("Account disabled");
        }

        return UserDetailsImpl.build(user);
    }
}