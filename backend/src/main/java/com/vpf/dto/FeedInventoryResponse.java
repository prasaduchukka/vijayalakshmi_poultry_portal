package com.vpf.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FeedInventoryResponse {
    private Integer stockBags;
    private BigDecimal bagPrice;
    private LocalDateTime updatedAt;
}
