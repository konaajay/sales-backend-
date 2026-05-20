package com.lms.www.leadmanagement.util;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class DateFormatterUtil {

    private static final List<DateTimeFormatter> PARSERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private static final DateTimeFormatter TARGET_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public static String formatToStandard(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return rawDate;
        }
        
        String datePart = rawDate.split(" ")[0]; // Just in case time is still there

        for (DateTimeFormatter parser : PARSERS) {
            try {
                LocalDate date = LocalDate.parse(datePart, parser);
                return date.format(TARGET_FORMAT); // e.g. 5-Jan-2026
            } catch (DateTimeParseException e) {
                // Try next
            }
        }
        
        // If all parsing fails, just return the original date part to avoid crashing
        log.warn("Could not parse date format for: {}. Using raw value.", datePart);
        return datePart;
    }
}
