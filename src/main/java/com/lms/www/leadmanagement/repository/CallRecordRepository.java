package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.CallRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {

    java.util.Optional<CallRecord> findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(Long userId);

    @Query("SELECT new com.lms.www.leadmanagement.dto.DailyUserReportDTO(c.user.id, c.user.name, COUNT(c), SUM(c.duration), AVG(c.duration)) " +
           "FROM CallRecord c WHERE c.startTime BETWEEN :start AND :end AND c.endTime IS NOT NULL GROUP BY c.user.id, c.user.name")
    List<com.lms.www.leadmanagement.dto.DailyUserReportDTO> getDailyUserReports(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    List<CallRecord> findByUserIdOrderByStartTimeDesc(Long userId);

    List<CallRecord> findByUserIdInOrderByStartTimeDesc(List<Long> userIds);

    List<CallRecord> findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(Long userId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    List<CallRecord> findByStartTimeBetweenOrderByStartTimeDesc(java.time.LocalDateTime start, java.time.LocalDateTime end);

    @Query("SELECT new map(" +
           "COUNT(c) as totalCalls, " +
           "SUM(c.duration) as totalDuration, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN 1 ELSE 0 END) as incomingCount, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN c.duration ELSE 0 END) as incomingDuration, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN 1 ELSE 0 END) as outgoingCount, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN c.duration ELSE 0 END) as outgoingDuration, " +
           "SUM(CASE WHEN c.status = 'MISSED' THEN 1 ELSE 0 END) as missedCount, " +
           "SUM(CASE WHEN c.status = 'REJECTED' THEN 1 ELSE 0 END) as rejectedCount, " +
           "SUM(CASE WHEN c.status = 'NEVER_ATTENDED' THEN 1 ELSE 0 END) as neverAttendedCount, " +
           "SUM(CASE WHEN c.status = 'NOT_PICKED' OR c.status = 'NOT_PICKUP' THEN 1 ELSE 0 END) as notPickedCount, " +
           "COUNT(DISTINCT c.phoneNumber) as uniqueCount) " +
           "FROM CallRecord c")
    Map<String, Object> getGlobalStats();

    @Query("SELECT new map(" +
           "COUNT(c) as totalCalls, " +
           "SUM(c.duration) as totalDuration, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN 1 ELSE 0 END) as incomingCount, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN c.duration ELSE 0 END) as incomingDuration, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN 1 ELSE 0 END) as outgoingCount, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN c.duration ELSE 0 END) as outgoingDuration, " +
           "SUM(CASE WHEN c.status = 'MISSED' THEN 1 ELSE 0 END) as missedCount, " +
           "SUM(CASE WHEN c.status = 'REJECTED' THEN 1 ELSE 0 END) as rejectedCount, " +
           "SUM(CASE WHEN c.status = 'NEVER_ATTENDED' THEN 1 ELSE 0 END) as neverAttendedCount, " +
           "SUM(CASE WHEN c.status = 'NOT_PICKED' OR c.status = 'NOT_PICKUP' THEN 1 ELSE 0 END) as notPickedCount, " +
           "COUNT(DISTINCT c.phoneNumber) as uniqueCount) " +
           "FROM CallRecord c WHERE c.user.id = :userId")
    Map<String, Object> getStatsForUser(@Param("userId") Long userId);

    @Query("SELECT new map(" +
           "COUNT(c) as totalCalls, " +
           "SUM(c.duration) as totalDuration, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN 1 ELSE 0 END) as incomingCount, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN c.duration ELSE 0 END) as incomingDuration, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN 1 ELSE 0 END) as outgoingCount, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN c.duration ELSE 0 END) as outgoingDuration, " +
           "SUM(CASE WHEN c.status = 'MISSED' THEN 1 ELSE 0 END) as missedCount, " +
           "SUM(CASE WHEN c.status = 'REJECTED' THEN 1 ELSE 0 END) as rejectedCount, " +
           "SUM(CASE WHEN c.status = 'NEVER_ATTENDED' THEN 1 ELSE 0 END) as neverAttendedCount, " +
           "SUM(CASE WHEN c.status = 'NOT_PICKED' OR c.status = 'NOT_PICKUP' THEN 1 ELSE 0 END) as notPickedCount, " +
           "COUNT(DISTINCT c.phoneNumber) as uniqueCount) " +
           "FROM CallRecord c WHERE c.user.id IN :userIds")
    Map<String, Object> getStatsForTeam(@Param("userIds") List<Long> userIds);

    @Query("SELECT new map(" +
           "COUNT(c) as totalCalls, " +
           "SUM(c.duration) as totalDuration, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN 1 ELSE 0 END) as incomingCount, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN c.duration ELSE 0 END) as incomingDuration, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN 1 ELSE 0 END) as outgoingCount, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN c.duration ELSE 0 END) as outgoingDuration, " +
           "SUM(CASE WHEN c.status = 'MISSED' THEN 1 ELSE 0 END) as missedCount, " +
           "SUM(CASE WHEN c.status = 'REJECTED' THEN 1 ELSE 0 END) as rejectedCount, " +
           "SUM(CASE WHEN c.status = 'NEVER_ATTENDED' THEN 1 ELSE 0 END) as neverAttendedCount, " +
           "SUM(CASE WHEN c.status = 'NOT_PICKED' OR c.status = 'NOT_PICKUP' THEN 1 ELSE 0 END) as notPickedCount, " +
           "COUNT(DISTINCT c.phoneNumber) as uniqueCount) " +
           "FROM CallRecord c WHERE c.startTime BETWEEN :start AND :end")
    Map<String, Object> getGlobalStatsByDate(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT new map(" +
           "COUNT(c) as totalCalls, " +
           "SUM(c.duration) as totalDuration, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN 1 ELSE 0 END) as incomingCount, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN c.duration ELSE 0 END) as incomingDuration, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN 1 ELSE 0 END) as outgoingCount, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN c.duration ELSE 0 END) as outgoingDuration, " +
           "SUM(CASE WHEN c.status = 'MISSED' THEN 1 ELSE 0 END) as missedCount, " +
           "SUM(CASE WHEN c.status = 'REJECTED' THEN 1 ELSE 0 END) as rejectedCount, " +
           "SUM(CASE WHEN c.status = 'NEVER_ATTENDED' THEN 1 ELSE 0 END) as neverAttendedCount, " +
           "SUM(CASE WHEN c.status = 'NOT_PICKED' OR c.status = 'NOT_PICKUP' THEN 1 ELSE 0 END) as notPickedCount, " +
           "COUNT(DISTINCT c.phoneNumber) as uniqueCount) " +
           "FROM CallRecord c WHERE c.user.id = :userId AND c.startTime BETWEEN :start AND :end")
    Map<String, Object> getStatsForUserByDate(@Param("userId") Long userId, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT new map(" +
           "COUNT(c) as totalCalls, " +
           "SUM(COALESCE(c.duration, 0)) as totalDuration, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN 1 ELSE 0 END) as incomingCount, " +
           "SUM(CASE WHEN c.callType = 'INCOMING' THEN COALESCE(c.duration, 0) ELSE 0 END) as incomingDuration, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN 1 ELSE 0 END) as outgoingCount, " +
           "SUM(CASE WHEN c.callType = 'OUTGOING' THEN COALESCE(c.duration, 0) ELSE 0 END) as outgoingDuration, " +
           "SUM(CASE WHEN c.status = 'MISSED' THEN 1 ELSE 0 END) as missedCount, " +
           "SUM(CASE WHEN c.status = 'REJECTED' THEN 1 ELSE 0 END) as rejectedCount, " +
           "SUM(CASE WHEN c.status = 'NEVER_ATTENDED' THEN 1 ELSE 0 END) as neverAttendedCount, " +
           "SUM(CASE WHEN c.status = 'NOT_PICKED' OR c.status = 'NOT_PICKUP' THEN 1 ELSE 0 END) as notPickedCount, " +
           "COUNT(DISTINCT c.phoneNumber) as uniqueCount) " +
           "FROM CallRecord c WHERE c.user.id IN :userIds AND c.startTime BETWEEN :start AND :end")
    Map<String, Object> getStatsForUsersByDate(@Param("userIds") List<Long> userIds, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    List<CallRecord> findByUserIdInAndStartTimeBetweenOrderByStartTimeDesc(List<Long> userIds, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
