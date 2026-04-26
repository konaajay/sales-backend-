package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesStatsDTO {
    private LocalDate date;
    private long leadsCount;
    private long lostCount;
    private long convertedCount;
    private BigDecimal revenue;
}
