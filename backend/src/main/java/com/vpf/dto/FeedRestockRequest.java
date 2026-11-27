package com.vpf.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class FeedRestockRequest {
    @NotNull @Positive
    private Integer bags;
    @NotNull @Positive
    private BigDecimal bagPrice;
}
