package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.Ticket;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.TicketRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    @Transactional
    public Ticket createTicket(Ticket ticket, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ticket.setCreatedBy(creator);
        // Default values if not set
        if (ticket.getStatus() == null) ticket.setStatus(com.lms.www.leadmanagement.entity.TicketStatus.OPEN);
        if (ticket.getPriority() == null) ticket.setPriority(com.lms.www.leadmanagement.entity.TicketPriority.MEDIUM);
        return ticketRepository.save(ticket);
    }

    public List<Ticket> getTeamTickets(Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        java.util.Set<Long> allowedUserIds = securityService.getAllowedUserIds(requester);
        boolean isGlobalAdmin = securityService.isAdmin(requester);

        if (isGlobalAdmin) {
            return ticketRepository.findAllByOrderByIdDesc();
        } else {
            return ticketRepository.findByUserIdIn(new java.util.ArrayList<>(allowedUserIds));
        }
    }

    @Transactional
    public Ticket updateTicketStatus(Long ticketId, String status) {
        User curUser = securityService.getCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        // Security check: Only creator or their hierarchy leads can update
        securityService.validateAccess(curUser, ticket.getCreatedBy().getId());
        
        ticket.setStatus(com.lms.www.leadmanagement.entity.TicketStatus.valueOf(status));
        return ticketRepository.save(ticket);
    }

    public List<Ticket> getMyTickets(Long userId) {
        return ticketRepository.findByUserId(userId);
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }
}
