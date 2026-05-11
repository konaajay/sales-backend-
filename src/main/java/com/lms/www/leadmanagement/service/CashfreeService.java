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
            return response.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Cashfree API Error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Cashfree Error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unexpected Error creating Cashfree order: {}", e.getMessage());
            throw new RuntimeException("Internal Error: Failed to connect to payment gateway");
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
            return response.getBody();
        } catch (Exception e) {
            log.error("Error fetching Cashfree order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to fetch payment details from Cashfree");
        }
    }
}
