package com.lms.www.leadmanagement.dto;

import java.math.BigDecimal;
import java.util.Date;

public interface TrendProjection {
    Date getDate();
    BigDecimal getAmount();
    Long getCount();
}
