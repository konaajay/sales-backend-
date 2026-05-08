package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.LeadTask;
import com.lms.www.leadmanagement.repository.LeadTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class LeadTaskScheduler {

    @Autowired
    private LeadTaskRepository taskRepository;

    /**
     * Runs every 30 minutes to check for overdue tasks.
     * A task is OVERDUE if its status is PENDING/RESCHEDULED and the dueDate has passed.
     */
    @Scheduled(cron = "0 0/30 * * * *")
    @Transactional
    public void checkOverdueTasks() {
        log.info(">>> Running Lead Task Overdue Check at {}", LocalDateTime.now());
        
        LocalDateTime now = LocalDateTime.now();
        List<LeadTask> tasks = taskRepository.findByStatusInAndDueDateBefore(
            List.of(LeadTask.TaskStatus.PENDING, LeadTask.TaskStatus.RESCHEDULED),
            now
        );
        
        long count = 0;
        for (LeadTask task : tasks) {
            task.setStatus(LeadTask.TaskStatus.OVERDUE);
            taskRepository.save(task);
            count++;
        }
        
        if (count > 0) {
            log.info(">>> Marked {} lead tasks as OVERDUE", count);
        }
    }
}
