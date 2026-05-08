package com.lms.www.leadmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocationRequestDTO {
    private Long userId;

    @NotNull(message = "Latitude is required")
    private Double lat;

    @NotNull(message = "Longitude is required")
    private Double lng;

    private Double accuracy;

    private Long timestamp;

    private String deviceId;

    private boolean isMockLocation;
}
