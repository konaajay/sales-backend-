package com.lms.www.leadmanagement.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashfreeOrderResponse {
    private String cf_order_id;
    private String order_id;
    private String payment_session_id;
    private String order_status;
}
