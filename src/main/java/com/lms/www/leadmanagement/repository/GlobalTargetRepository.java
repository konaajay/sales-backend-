package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.GlobalTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalTargetRepository extends JpaRepository<GlobalTarget, Long> {
    Optional<GlobalTarget> findFirstByOrderByIdAsc();
}
