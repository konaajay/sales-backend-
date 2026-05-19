package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.UserSession;
import com.lms.www.leadmanagement.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@lombok.extern.slf4j.Slf4j
@Service
public class UserSessionService {

    @Autowired
    private UserSessionRepository userSessionRepository;

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    
    @Transactional
    public UserSession startSession(User user, String ip, String userAgent) {
        // Close any existing active sessions
        userSessionRepository.findTopByUserIdAndStatusOrderByLoginTimeDesc(user.getId(), "ACTIVE")
            .ifPresent(existing -> {
                existing.setStatus("INACTIVE");
                existing.setLogoutTime(LocalDateTime.now(INDIA_ZONE));
                userSessionRepository.save(existing);
            });

        UserSession session = UserSession.builder()
                .user(user)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build();
        return userSessionRepository.save(session);
    }

    @Transactional
    public boolean updateActivity(Long userId) {
        Optional<UserSession> sessionOpt = userSessionRepository.findTopByUserIdAndStatusOrderByLoginTimeDesc(userId, "ACTIVE");
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            LocalDateTime now = LocalDateTime.now(INDIA_ZONE);
            
            // 30-minute inactivity check
            if (session.getLastActivity() != null && 
                session.getLastActivity().plusMinutes(30).isBefore(now)) {
                session.setStatus("INACTIVE");
                session.setLogoutTime(now);
                userSessionRepository.save(session);
                return false;
            }

            session.setLastActivity(now);
            userSessionRepository.save(session);
            return true;
        }
        return false;
    }

    @Transactional
    public void endSession(Long userId) {
        userSessionRepository.findTopByUserIdAndStatusOrderByLoginTimeDesc(userId, "ACTIVE")
            .ifPresent(session -> {
                session.setStatus("INACTIVE");
                session.setLogoutTime(LocalDateTime.now(INDIA_ZONE));
                userSessionRepository.save(session);
            });
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void autoLogoutStaleSessions() {
        LocalDateTime now = LocalDateTime.now(INDIA_ZONE);
        LocalDateTime cutoff = now.minusMinutes(30);
        userSessionRepository.closeIdleSessions(cutoff, now);
    }
}
