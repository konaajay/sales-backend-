package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "global_targets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer monthlyLeadQuota;
    private Double targetConversionRate;
    private Double targetRetentionRate;
    private BigDecimal monthlyRevenueGoal;
    private Integer activeMemberThreshold;

    private BigDecimal baseIncentiveAmount;
    private BigDecimal targetIncentiveAmount;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Default target singleton - typically we only have one row
    public static GlobalTarget defaultTarget() {
        return GlobalTarget.builder()
                .monthlyLeadQuota(1000)
                .targetConversionRate(15.0)
                .targetRetentionRate(80.0)
                .monthlyRevenueGoal(new BigDecimal("500000"))
                .activeMemberThreshold(10)
                .baseIncentiveAmount(BigDecimal.ZERO)
                .targetIncentiveAmount(BigDecimal.ZERO)
                .build();
    }
}
