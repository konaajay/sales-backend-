package com.lms.www.leadmanagement.dto.payment;

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
    private String order_id;
    private BigDecimal order_amount;
    private String order_currency;
    private String order_expiry_time;
    private CustomerDetails customer_details;
    private OrderMeta order_meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDetails {
        private String customer_id;
        private String customer_name;
        private String customer_email;
        private String customer_phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderMeta {
        private String return_url;
        private String notify_url;
        private String payment_methods;
    }
}
