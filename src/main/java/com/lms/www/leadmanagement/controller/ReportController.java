package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.LeadStatsDTO;
import com.lms.www.leadmanagement.dto.ReportFilterDTO;
import com.lms.www.leadmanagement.dto.TimeSeriesStatsDTO;
import com.lms.www.leadmanagement.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public LeadStatsDTO getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long teamLeaderId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        
        LocalDate endDate = (to == null) ? LocalDate.now() : to;
        LocalDate startDate = (from == null) ? endDate.minusDays(30) : from;

        ReportFilterDTO filter = ReportFilterDTO.builder()
                .fromDate(startDate)
                .toDate(endDate)
                .teamLeaderId(teamLeaderId)
                .userId(userId)
                .status(status)
                .build();
        
        return reportService.getFilteredStats(filter);
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'VIEW_LEADS')")
    public List<TimeSeriesStatsDTO> getTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long teamLeaderId,
            @RequestParam(required = false) Long userId) {
        
        ReportFilterDTO filter = ReportFilterDTO.builder()
                .fromDate(from)
                .toDate(to)
                .teamLeaderId(teamLeaderId)
                .userId(userId)
                .build();
        
        return reportService.getFilteredTrend(filter);
    }
}
