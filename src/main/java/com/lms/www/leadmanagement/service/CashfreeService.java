package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest;
import com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class CashfreeService {

    @Value("${cashfree.app.id}")
    private String appId;

    @Value("${cashfree.secret.key}")
    private String secretKey;

    @Value("${cashfree.environment}")
    private String environment;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getBaseUrl() {
        return "PROD".equalsIgnoreCase(environment) 
            ? "https://api.cashfree.com/pg/orders" 
            : "https://sandbox.cashfree.com/pg/orders";
    }

    public CashfreeOrderResponse createOrder(CashfreeOrderRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", "2023-08-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<CashfreeOrderRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<CashfreeOrderResponse> response = restTemplate.postForEntity(
                getBaseUrl(), entity, CashfreeOrderResponse.class);
            CashfreeOrderResponse body = response.getBody();
            if (body != null) {
                log.info("Cashfree Order Created: ID={}, SessionID={}", body.getOrder_id(), body.getPayment_session_id());
            }
            return body;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("Cashfree API Rejection [{}]: {}", e.getStatusCode(), errorBody);
            
            String cleanMessage = "Gateway Error";
            try {
                // Quick extraction of message from JSON if it exists
                if (errorBody.contains("\"message\"")) {
                    int start = errorBody.indexOf("\"message\":\"") + 11;
                    int end = errorBody.indexOf("\"", start);
                    if (start > 10 && end > start) {
                        cleanMessage = errorBody.substring(start, end);
                    }
                }
            } catch (Exception ex) {}
            
            throw new RuntimeException("Cashfree rejection: " + cleanMessage + " (HTTP " + e.getStatusCode() + ")");
        } catch (Exception e) {
            log.error("Networking/Unexpected Error communicating with Cashfree: {}", e.getMessage(), e);
            throw new RuntimeException("Gateway Connectivity Error: " + e.getMessage());
        }
    }

    public CashfreeOrderResponse getOrder(String orderId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", "2023-08-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<CashfreeOrderResponse> response = restTemplate.exchange(
                getBaseUrl() + "/" + orderId,
                HttpMethod.GET,
                entity,
                CashfreeOrderResponse.class);
            CashfreeOrderResponse body = response.getBody();
            if (body != null) {
                log.info("Cashfree Order Fetched: ID={}, Status={}, SessionID={}", body.getOrder_id(), body.getOrder_status(), body.getPayment_session_id());
            }
            return body;
        } catch (Exception e) {
            log.error("Error fetching Cashfree order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to fetch payment details from Cashfree");
        }
    }
}
