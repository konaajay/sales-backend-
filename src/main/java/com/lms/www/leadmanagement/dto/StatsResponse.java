package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

import com.lms.www.leadmanagement.entity.Certificate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    private Map<String, Long> summary;
    private List<Certificate> details;
}
