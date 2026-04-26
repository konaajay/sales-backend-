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
    
    long countByStatusIn(java.util.Collection<com.lms.www.leadmanagement.entity.TicketStatus> statuses);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t) FROM Ticket t WHERE (t.assignedTo IN :users OR (t.assignedTo IS NULL AND t.createdBy IN :users)) AND t.status IN :statuses")
    long countByUsersAndStatusIn(
            @org.springframework.data.repository.query.Param("users") java.util.Collection<com.lms.www.leadmanagement.entity.User> users, 
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<com.lms.www.leadmanagement.entity.TicketStatus> statuses);
}
