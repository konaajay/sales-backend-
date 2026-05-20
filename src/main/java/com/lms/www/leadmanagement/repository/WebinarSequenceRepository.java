package com.lms.www.leadmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.lms.www.leadmanagement.entity.WebinarSequence;
import com.lms.www.leadmanagement.entity.WebinarSequenceId;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface WebinarSequenceRepository
        extends JpaRepository<WebinarSequence, WebinarSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ws FROM WebinarSequence ws WHERE ws.dateKey = :dateKey")
    Optional<WebinarSequence> findByDateKeyForUpdate(String dateKey);
}