package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.entity.Ticket;
import com.lms.www.leadmanagement.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;
    private final com.lms.www.leadmanagement.repository.UserRepository userRepository;

    @PostMapping("/raise")
    public ResponseEntity<Ticket> raiseTicket(@RequestBody Ticket ticket) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User context invalid"))
                .getId();
        return ResponseEntity.ok(ticketService.createTicket(ticket, userId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Ticket>> getMyTickets() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User context invalid"))
                .getId();
        return ResponseEntity.ok(ticketService.getMyTickets(userId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Ticket>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Ticket> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(ticketService.updateTicketStatus(id, status));
    }
}
