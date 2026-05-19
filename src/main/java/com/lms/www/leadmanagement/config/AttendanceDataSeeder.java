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
@lombok.extern.slf4j.Slf4j
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
            AttendanceShift dayShift = AttendanceShift.builder()
                    .name("Day Shift (Standard)")
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(19, 0))
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
        }
    }

    private void seedOffices() {
        if (officeRepository.count() == 0) {
            OfficeLocation hq = OfficeLocation.builder()
                    .name("gYantrix")
                    .latitude(17.4535791)
                    .longitude(78.3720483)
                    .radius(100.0) 
                    .build();
            officeRepository.save(hq);
        }
    }
}
