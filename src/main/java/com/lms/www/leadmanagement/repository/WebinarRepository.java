package com.lms.www.leadmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lms.www.leadmanagement.entity.Webinar;

@Repository
public interface WebinarRepository extends JpaRepository<Webinar, Long> {
}
