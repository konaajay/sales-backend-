package com.lms.www.leadmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lms.www.leadmanagement.entity.College;

import java.util.List;

@Repository
public interface CollegeRepository extends JpaRepository<College, Long> {

    List<College> findByStatusTrue();

    List<College> findByCollegeNameContainingIgnoreCase(String collegeName);

    boolean existsByCollegeNameIgnoreCase(String collegeName);
}
