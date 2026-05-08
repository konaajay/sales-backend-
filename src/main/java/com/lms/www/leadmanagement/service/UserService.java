package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.entity.Role;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.exception.InvalidRequestException;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.RoleRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.repository.AttendanceShiftRepository;
import com.lms.www.leadmanagement.repository.OfficeLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AttendanceShiftRepository attendanceShiftRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityService securityService;
    private final MailService mailService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       AttendanceShiftRepository attendanceShiftRepository,
                       OfficeLocationRepository officeLocationRepository,
                       PasswordEncoder passwordEncoder, SecurityService securityService,
                       MailService mailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.attendanceShiftRepository = attendanceShiftRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityService = securityService;
        this.mailService = mailService;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAuthorizedUsers(Pageable pageable) {
        User requester = securityService.getCurrentUser();
        java.util.Set<Long> allowedIds = securityService.getAllowedUserIds(requester);
        
        return userRepository.findByIdIn(new java.util.ArrayList<>(allowedIds), pageable).map(UserDTO::fromEntity);
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

        if (dto.getShiftId() != null) {
            attendanceShiftRepository.findById(dto.getShiftId()).ifPresent(user::setShift);
        }
        if (dto.getOfficeId() != null) {
            officeLocationRepository.findById(dto.getOfficeId()).ifPresent(user::setAssignedOffice);
        }

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
        if (dto.getSupervisorId() == null || dto.getSupervisorId() <= 0) return;

        securityService.validateAccess(
            securityService.getCurrentUser(),
            dto.getSupervisorId()
        );

        User parent = userRepository.findById(dto.getSupervisorId())
                .orElseThrow(() -> new RuntimeException("Parent not found"));

        // Renamed to avoid any potential duplicate local variable conflicts
        String targetUserRole = user.getRole().getName().toUpperCase().replace("ROLE_", "");
        String supervisorRole = parent.getRole().getName().toUpperCase().replace("ROLE_", "");

        // MANAGER → reports to ADMIN
        if ("MANAGER".equals(targetUserRole)) {
            if (!"ADMIN".equals(supervisorRole)) {
                throw new RuntimeException("Manager must report to an Admin");
            }
            user.setSupervisor(parent);
            user.setManager(null);
        }
        // TEAM LEADER → reports to MANAGER
        else if ("TEAM_LEADER".equals(targetUserRole) || "TL".equals(targetUserRole)) {
            if (!"MANAGER".equals(supervisorRole)) {
                throw new RuntimeException("Team Leader must report to a Manager");
            }
            user.setManager(parent);
            user.setSupervisor(parent);
        }
        // ASSOCIATE / BDA → reports to TL
        else {
            if (!"TEAM_LEADER".equals(supervisorRole) && !"TL".equals(supervisorRole)) {
                throw new RuntimeException("Associate must have a Team Leader as supervisor");
            }
            user.setSupervisor(parent);
            user.setManager(parent.getManager());
        }
    }
}
