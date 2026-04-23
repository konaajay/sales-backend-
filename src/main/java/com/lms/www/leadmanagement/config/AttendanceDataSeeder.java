package com.lms.www.leadmanagement.config;

import com.lms.www.leadmanagement.entity.AttendanceShift;
import com.lms.www.leadmanagement.entity.OfficeLocation;
import com.lms.www.leadmanagement.repository.AttendanceShiftRepository;
import com.lms.www.leadmanagement.repository.OfficeLocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import java.time.LocalTime;
import java.util.List;

// @Component
public class AttendanceDataSeeder implements CommandLineRunner {

    @Autowired
    private AttendanceShiftRepository shiftRepository;

    @Autowired
    private OfficeLocationRepository officeRepository;

    @Override
    public void run(String... args) throws Exception {
        seedShifts();
        seedOffices();
    }

    private void seedShifts() {
        if (shiftRepository.count() == 0) {
            System.out.println("SEEDING: Initializing operational attendance shifts...");

            AttendanceShift dayShift = AttendanceShift.builder()
                    .name("Day Shift (Standard)")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .graceMinutes(15)
                    .minFullDayMinutes(480)
                    .minHalfDayMinutes(240)
                    .build();

            AttendanceShift afternoonShift = AttendanceShift.builder()
                    .name("Afternoon Shift")
                    .startTime(LocalTime.of(14, 0))
                    .endTime(LocalTime.of(23, 0))
                    .graceMinutes(15)
                    .minFullDayMinutes(480)
                    .minHalfDayMinutes(240)
                    .build();

            AttendanceShift nightShift = AttendanceShift.builder()
                    .name("Night Shift")
                    .startTime(LocalTime.of(22, 0))
                    .endTime(LocalTime.of(7, 0))
                    .graceMinutes(15)
                    .minFullDayMinutes(480)
                    .minHalfDayMinutes(240)
                    .build();

            shiftRepository.saveAll(List.of(dayShift, afternoonShift, nightShift));
            System.out.println("SEEDING COMPLETE: 3 shifts initialized.");
        }
    }

    private void seedOffices() {
        if (officeRepository.count() == 0) {
            System.out.println("SEEDING: Initializing default office location...");
            OfficeLocation hq = OfficeLocation.builder()
                    .name("Main HQ")
                    .latitude(17.3850)
                    .longitude(78.4867)
                    .radius(100000.0) // 100km radius for flexible testing
                    .build();
            officeRepository.save(hq);
            System.out.println("SEEDING COMPLETE: Primary office node established.");
        }
    }
}
