package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.repository.AttendanceSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceService attendanceService;

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    @Scheduled(cron = "0 0/5 * * * *") // Every 5 minutes
    @Transactional
    public void autoCheckOutGhostSessions() {
        LocalDateTime now = LocalDateTime.now(INDIA_ZONE);
        List<AttendanceStatus> activeStatuses = List.of(
            AttendanceStatus.WORKING, 
            AttendanceStatus.ON_BREAK, 
            AttendanceStatus.AUTO_BREAK, 
            AttendanceStatus.OUTSIDE
        );

        List<AttendanceSession> activeSessions = sessionRepository.findAllByStatusIn(activeStatuses);
        
        for (AttendanceSession session : activeSessions) {
            try {
                User user = session.getUser();
                AttendanceShift shift = user.getShift();
                
                // If user has no shift, we can't auto-checkout based on shift end
                if (shift == null) continue;

                LocalTime shiftEnd = shift.getEndTime();
                LocalDateTime shiftEndToday = session.getCheckInTime().toLocalDate().atTime(shiftEnd);
                
                // Add 30 minute buffer
                if (now.isAfter(shiftEndToday.plusMinutes(30))) {
                    log.info("Auto-checking out user {} due to shift end at {}", user.getName(), shiftEnd);
                    attendanceService.finalizeSession(session, shiftEndToday, true);
                }
            } catch (Exception e) {
                log.error("Error in auto-checkout for session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
