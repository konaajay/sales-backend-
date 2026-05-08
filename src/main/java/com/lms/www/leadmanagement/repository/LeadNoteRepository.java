package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.LeadNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadNoteRepository extends JpaRepository<LeadNote, Long> {
}
