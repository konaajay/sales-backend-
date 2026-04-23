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

    public List<Ticket> getMyTickets(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ticketRepository.findByCreatedByOrderByIdDesc(user);
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAllByOrderByIdDesc();
    }

    @Transactional
    public Ticket updateTicketStatus(Long ticketId, String status) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        ticket.setStatus(com.lms.www.leadmanagement.entity.TicketStatus.valueOf(status));
        return ticketRepository.save(ticket);
    }
}
