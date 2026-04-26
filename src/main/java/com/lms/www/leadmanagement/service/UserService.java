package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.entity.Role;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.exception.InvalidRequestException;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.RoleRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityService securityService;
    private final MailService mailService;

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAuthorizedUsers(Pageable pageable) {
        User requester = securityService.getCurrentUser();
        
        if (securityService.isAdmin(requester)) {
            return userRepository.findAll(pageable).map(UserDTO::fromEntity);
        }

        List<Long> subordinateIds = userRepository.findSubordinateIds(requester.getId());
        subordinateIds.add(requester.getId()); // Include self
        
        return userRepository.findByIdIn(subordinateIds, pageable).map(UserDTO::fromEntity);
    }

    @Transactional
    public UserDTO createUser(UserDTO dto) {
        validateUniqueness(dto);
        
        User requester = securityService.getCurrentUser();
        Role targetRole = roleRepository.findByName(dto.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + dto.getRole()));

        validateHierarchyConstraint(requester, targetRole.getName());

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail().toLowerCase())
                .mobile(dto.getMobile())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(targetRole)
                .active(true)
                .joiningDate(dto.getJoiningDate())
                .build();

        assignHierarchy(user, dto);
        User saved = userRepository.save(user);

        // Async email sending (Conceptual - should be event based or async)
        mailService.sendUserCredentials(saved.getEmail(), dto.getPassword(), saved.getName());

        return UserDTO.fromEntity(saved);
    }

    private void validateUniqueness(UserDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new InvalidRequestException("Email already registered: " + dto.getEmail());
        }
        if (userRepository.existsByMobile(dto.getMobile())) {
            throw new InvalidRequestException("Mobile already registered: " + dto.getMobile());
        }
    }

    private void validateHierarchyConstraint(User requester, String targetRole) {
        String requesterRole = requester.getRole().getName();
        
        if ("MANAGER".equals(requesterRole)) {
            if ("ADMIN".equals(targetRole) || "MANAGER".equals(targetRole)) {
                throw new InvalidRequestException("Branch Managers can only provision Team Leaders and BDAs.");
            }
        } else if (!"ADMIN".equals(requesterRole)) {
            throw new InvalidRequestException("Insufficient clearance for user provisioning.");
        }
    }

    private void assignHierarchy(User user, UserDTO dto) {
        if (dto.getSupervisorId() != null && dto.getSupervisorId() != 0) {
            User supervisor = findById(dto.getSupervisorId());
            user.setSupervisor(supervisor);
            
            // Auto-link Manager
            if ("TEAM_LEADER".equals(supervisor.getRole().getName())) {
                user.setManager(supervisor.getSupervisor());
            } else if ("MANAGER".equals(supervisor.getRole().getName())) {
                user.setManager(supervisor);
            }
        }
    }
}
