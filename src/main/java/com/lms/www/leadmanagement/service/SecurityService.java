package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.exception.UnauthorizedAccessException;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SecurityService {

    private final UserRepository userRepository;

    public SecurityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedAccessException("Current user session is invalid"));
    }

    public void validateHierarchyAccess(User requester, User target) {
        if (requester.getRole().getName().equals("ADMIN")) return;
        if (requester.getId().equals(target.getId())) return;

        List<Long> subordinates = userRepository.findSubordinateIds(requester.getId());
        if (subordinates == null || !subordinates.contains(target.getId())) {
            throw new UnauthorizedAccessException("You do not have permission to access data for user: " + target.getName());
        }
    }

    public boolean isAdmin(User user) {
        return "ADMIN".equals(user.getRole().getName());
    }

    public boolean isManager(User user) {
        return "MANAGER".equals(user.getRole().getName());
    }
}
