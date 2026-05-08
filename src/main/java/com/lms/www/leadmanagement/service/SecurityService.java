package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.exception.UnauthorizedAccessException;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
@Slf4j
public class SecurityService {

    private final UserRepository userRepository;

    public SecurityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    public Set<Long> getScopedUserIds(User requester, Long managerId, Long teamLeaderId) {
        return getScopedUserIdsInternal(requester, managerId, teamLeaderId);
    }

    public Set<Long> getAllowedUserIds(User requester) {
        return getScopedUserIdsInternal(requester, null, null);
    }

    private Set<Long> getScopedUserIdsInternal(User requester, Long managerId, Long teamLeaderId) {
        Set<Long> ids = new HashSet<>();
        if (requester == null) return ids;
        
        // Use provided managerId if requester is ADMIN, otherwise use requester's context
        Long activeManagerId = isAdmin(requester) ? managerId : (isManager(requester) ? requester.getId() : null);
        
        // If specific TL selected
        if (teamLeaderId != null && teamLeaderId > 0) {
            ids.add(teamLeaderId);
            ids.addAll(userRepository.findSubordinateIds(teamLeaderId));
            return ids;
        }

        // If specific Manager selected (or current user is Manager)
        if (activeManagerId != null && activeManagerId > 0) {
            ids.add(activeManagerId);
            ids.addAll(userRepository.findSubordinateIds(activeManagerId));
            return ids;
        }

        // Default: ADMIN → all users
        if (isAdmin(requester)) {
            return new HashSet<>(userRepository.findAllIds());
        }

        // Default: TEAM LEADER
        if (isTeamLeader(requester)) {
            ids.add(requester.getId());
            ids.addAll(userRepository.findSubordinateIds(requester.getId()));
            return ids;
        }

        // Default: ASSOCIATE
        ids.add(requester.getId());
        return ids;
    }

    public void validateAccess(User requester, Long targetUserId) {
        if (requester == null || targetUserId == null || targetUserId <= 0) {
            return; // Ignore invalid or "all" (0) IDs
        }
        if (requester.getId().equals(targetUserId)) return;
        if (isAdmin(requester)) return;

        Set<Long> allowed = getScopedUserIds(requester, null, null);
        if (!allowed.contains(targetUserId)) {
            log.warn("Access Denied: User {} attempted to access target {}", requester.getId(), targetUserId);
            throw new UnauthorizedAccessException("User outside hierarchy");
        }
    }

    public void validateHierarchyAccess(User requester, User target) {
        if (target == null) return;
        validateAccess(requester, target.getId());
    }

    private String getRole(User user) {
        if (user == null) return "";
        if (user.getRole() == null) {
            user = userRepository.findById(user.getId()).orElse(user);
        }
        if (user.getRole() == null) return "";
        
        // Robust normalization: strip spaces, underscores, and common prefixes
        String roleName = user.getRole().getName().trim().toUpperCase()
            .replace("ROLE_", "")
            .replace(" ", "")
            .replace("_", "");
            
        log.info("[ROLE-DEBUG] User: {}, RawRole: {}, Normalized: {}", user.getEmail(), user.getRole().getName(), roleName);
        return roleName;
    }

    public boolean isAdmin(User user) {
        String role = getRole(user);
        return role.equals("ADMIN") || role.equals("SUPERADMIN") || "admin@lms.com".equalsIgnoreCase(user.getEmail());
    }

    public boolean isManager(User user) {
        String role = getRole(user);
        return role.equals("MANAGER") || role.equals("MGR");
    }

    public boolean isTeamLeader(User user) {
        String role = getRole(user);
        return role.equals("TEAMLEADER") || role.equals("TL") || role.equals("TEAMLEAD") || role.equals("TEAMLEAS");
    }

    public boolean isRoot(User user) {
        if (user == null) return false;
        return user.getManager() == null && user.getSupervisor() == null;
    }
}
