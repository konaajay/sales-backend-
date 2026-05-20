package com.lms.www.leadmanagement.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lms.www.leadmanagement.entity.WebinarSequence;
import com.lms.www.leadmanagement.repository.WebinarSequenceRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateIdGenerator {

    private static final String PREFIX = "GTAWP";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMM");
    private static final String GLOBAL_KEY = "GLOBAL";

    private final WebinarSequenceRepository sequenceRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initSequence() {
        WebinarSequence sequence = sequenceRepository.findByDateKeyForUpdate(GLOBAL_KEY)
                .orElseGet(() -> {
                    WebinarSequence newSeq = new WebinarSequence();
                    newSeq.setDateKey(GLOBAL_KEY);
                    newSeq.setLastCounter(8539);
                    return newSeq;
                });
        if (sequence.getLastCounter() < 8539) {
            log.info("Updating global certificate sequence from {} to 8539", sequence.getLastCounter());
            sequence.setLastCounter(8539);
            sequenceRepository.save(sequence);
        }
    }

    @Transactional
    public synchronized String generateCertificateId() {
        // Always use the same GLOBAL key so counter is continuous
        WebinarSequence sequence = sequenceRepository.findByDateKeyForUpdate(GLOBAL_KEY)
                .orElseGet(() -> {
                    WebinarSequence newSeq = new WebinarSequence();
                    newSeq.setDateKey(GLOBAL_KEY);
                    newSeq.setLastCounter(8539);
                    return newSeq;
                });

        int nextCounter = sequence.getLastCounter() + 1;
        sequence.setLastCounter(nextCounter);
        sequenceRepository.save(sequence);

        // Date is today's date (just for display in the ID), counter is global
        String dateKey = LocalDate.now().format(DATE_FORMATTER);
        return String.format("%s%s%04d", PREFIX, dateKey, nextCounter);
    }
}
