package com.lms.www.leadmanagement.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashfreeOrderResponse {
    @JsonProperty("cf_order_id")
    private String cf_order_id;
    
    @JsonProperty("order_id")
    private String order_id;
    
    @JsonProperty("payment_session_id")
    private String payment_session_id;
    
    @JsonProperty("order_status")
    private String order_status;
}
