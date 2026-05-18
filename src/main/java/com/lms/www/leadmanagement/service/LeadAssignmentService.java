package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.service.CsvValidationService.ValidLeadMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LeadAssignmentService {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private SecurityService securityService;

    @Transactional
    public List<Lead> assignAndBatchSave(List<ValidLeadMapping> validMappings, String batchId) {
        User currentUser = securityService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        List<Lead> leadsToSave = new ArrayList<>(validMappings.size());

        for (ValidLeadMapping mapping : validMappings) {
            String status = mapping.getRawRow().getStatus() != null && !mapping.getRawRow().getStatus().trim().isEmpty()
                    ? mapping.getRawRow().getStatus().trim()
                    : "OLD_LEAD";

            Lead lead = Lead.builder()
                    .name(mapping.getRawRow().getName().trim())
                    .email(mapping.getCleanEmail().isEmpty() ? null : mapping.getCleanEmail())
                    .mobile(mapping.getCleanMobile())
                    .course(mapping.getCourse())
                    .assignedTo(mapping.getAssignedUser())
                    .teamLeader(mapping.getTeamLeader())
                    .status(status)
                    .createdBy(currentUser)
                    .importBatchId(batchId)
                    .importedBy(currentUser)
                    .importedAt(now)
                    .source("CSV_IMPORT")
                    .build();

            leadsToSave.add(lead);
        }

        // Use batch saveAll() for maximum performance on large CSVs
        List<Lead> savedLeads = leadRepository.saveAll(leadsToSave);
        log.info("Successfully batch saved {} leads for batchId {}", savedLeads.size(), batchId);
        return savedLeads;
    }
}
