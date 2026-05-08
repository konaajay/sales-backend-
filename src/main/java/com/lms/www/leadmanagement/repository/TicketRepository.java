package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.Ticket;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByCreatedByOrderByIdDesc(User createdBy);
    List<Ticket> findAllByOrderByIdDesc();
    
    long countByStatusIn(Collection<TicketStatus> statuses);
    
    @Query("SELECT COUNT(t) FROM Ticket t WHERE (t.assignedTo.id IN :userIds OR (t.assignedTo IS NULL AND t.createdBy.id IN :userIds)) AND t.status IN :statuses")
    long countByUserIdInAndStatusIn(@Param("userIds") Collection<Long> userIds, @Param("statuses") Collection<TicketStatus> statuses);

    @Query("SELECT t FROM Ticket t WHERE t.createdBy.id IN :userIds ORDER BY t.id DESC")
    List<Ticket> findByUserIdIn(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT t FROM Ticket t WHERE t.createdBy.id = :userId ORDER BY t.id DESC")
    List<Ticket> findByUserId(@Param("userId") Long userId);
}
