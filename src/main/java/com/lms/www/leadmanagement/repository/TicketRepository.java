package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.Ticket;
import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByCreatedByOrderByIdDesc(User createdBy);
    List<Ticket> findAllByOrderByIdDesc();
}
