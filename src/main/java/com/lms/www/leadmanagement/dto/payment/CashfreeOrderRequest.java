package com.lms.www.leadmanagement.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashfreeOrderRequest {
    @JsonProperty("order_id")
    private String order_id;
    
    @JsonProperty("order_amount")
    private BigDecimal order_amount;
    
    @JsonProperty("order_currency")
    private String order_currency;
    
    @JsonProperty("order_expiry_time")
    private String order_expiry_time;
    
    @JsonProperty("customer_details")
    private CustomerDetails customer_details;
    
    @JsonProperty("order_meta")
    private OrderMeta order_meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDetails {
        @JsonProperty("customer_id")
        private String customer_id;
        
        @JsonProperty("customer_name")
        private String customer_name;
        
        @JsonProperty("customer_email")
        private String customer_email;
        
        @JsonProperty("customer_phone")
        private String customer_phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderMeta {
        @JsonProperty("return_url")
        private String return_url;
        
        @JsonProperty("notify_url")
        private String notify_url;
        
        @JsonProperty("payment_methods")
        private String payment_methods;
    }
}
