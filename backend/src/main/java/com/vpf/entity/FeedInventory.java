package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "feed_inventory")
public class FeedInventory {
    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private Integer stockBags = 0;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bagPrice = BigDecimal.ZERO;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() { updatedAt = LocalDateTime.now(); }
}
