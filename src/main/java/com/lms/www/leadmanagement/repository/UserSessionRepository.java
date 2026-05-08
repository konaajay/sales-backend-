package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    Optional<UserSession> findTopByUserIdAndStatusOrderByLoginTimeDesc(Long userId, String status);

    @Modifying
    @Query("UPDATE UserSession u SET u.status = 'INACTIVE', u.logoutTime = :now WHERE u.status = 'ACTIVE' AND u.lastActivity < :cutoff")
    int closeIdleSessions(LocalDateTime cutoff, LocalDateTime now);
}
