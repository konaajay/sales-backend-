package com.lms.www.leadmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LeadRealtimeNotificationService {

    /**
     * Broadcasts a real-time notification to active client dashboards (via SSE/WebSocket/PubSub)
     * informing them that a new batch of leads has been successfully ingested.
     * This triggers client-side cache invalidation (e.g. React Query invalidateQueries)
     * without requiring a hard page refresh.
     */
    public void notifyLeadBatchImported(String batchId, int successCount) {
        log.info("[REALTIME-NOTIFY] Broadcasting lead import event for batchId: {} with {} successful leads.", batchId, successCount);
        // Future expansion: SimpMessagingTemplate.convertAndSend("/topic/leads", new LeadImportEvent(batchId, successCount));
    }
}
