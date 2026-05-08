package com.lms.www.leadmanagement.config;

import com.lms.www.leadmanagement.entity.AttendanceShift;
import com.lms.www.leadmanagement.entity.OfficeLocation;
import com.lms.www.leadmanagement.repository.AttendanceShiftRepository;
import com.lms.www.leadmanagement.repository.OfficeLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializerConfig implements CommandLineRunner {

    private final OfficeLocationRepository officeLocationRepository;
    private final AttendanceShiftRepository attendanceShiftRepository;

    @Override
    public void run(String... args) throws Exception {
        if (officeLocationRepository.count() == 0) {
            log.info("Initializing default office location...");
            OfficeLocation office = OfficeLocation.builder()
                    .name("Main Headquarters")
                    .latitude(17.3850)
                    .longitude(78.4867)
                    .radius(500.0)
                    .build();
            office = officeLocationRepository.save(office);

            if (attendanceShiftRepository.count() == 0) {
                log.info("Initializing default shift...");
                AttendanceShift shift = AttendanceShift.builder()
                        .name("Standard Morning Shift")
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(19, 0))
                        .graceMinutes(15)
                        .minFullDayMinutes(480)
                        .minHalfDayMinutes(240)
                        .office(office)
                        .shortBreakStartTime(LocalTime.of(13, 0))
                        .shortBreakEndTime(LocalTime.of(13, 15))
                        .longBreakStartTime(LocalTime.of(14, 0))
                        .longBreakEndTime(LocalTime.of(15, 0))
                        .build();
                attendanceShiftRepository.save(shift);
            }
        }
    }
}
