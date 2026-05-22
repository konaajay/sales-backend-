package com.lms.www.leadmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lms.www.leadmanagement.entity.Registration;
import com.lms.www.leadmanagement.entity.Webinar;

import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Optional<Registration> findByEmailAndWebinar(String email, Webinar webinar);
    Optional<Registration> findByPhoneAndWebinar(String phone, Webinar webinar);
    java.util.List<Registration> findByWebinarId(Long webinarId);
}
