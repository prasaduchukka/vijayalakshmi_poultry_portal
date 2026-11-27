package com.vpf.service;

import com.vpf.dto.FeedInventoryResponse;
import com.vpf.dto.FeedRestockRequest;
import com.vpf.entity.FeedInventory;
import com.vpf.exception.BusinessRuleException;
import com.vpf.repository.FeedInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FeedInventoryService {
    private final FeedInventoryRepository repository;

    @Transactional
    public FeedInventory getLockedInventory() {
        return repository.findSingletonForUpdate().orElseGet(() -> {
            FeedInventory inventory = new FeedInventory();
            inventory.setId(1L);
            inventory.setStockBags(0);
            inventory.setBagPrice(BigDecimal.ZERO);
            return repository.saveAndFlush(inventory);
        });
    }

    @Transactional(readOnly = true)
    public FeedInventoryResponse get() {
        FeedInventory i = repository.findById(1L).orElse(null);
        return response(i == null ? defaultInventory() : i);
    }

    @Transactional
    public FeedInventoryResponse restock(FeedRestockRequest req) {
        FeedInventory i = getLockedInventory();
        i.setStockBags(Math.addExact(i.getStockBags(), req.getBags()));
        i.setBagPrice(req.getBagPrice());
        return response(repository.save(i));
    }

    private FeedInventory defaultInventory() {
        FeedInventory i = new FeedInventory();
        i.setStockBags(0);
        i.setBagPrice(BigDecimal.ZERO);
        return i;
    }

    public FeedInventoryResponse response(FeedInventory i) {
        return FeedInventoryResponse.builder()
                .stockBags(i.getStockBags()).bagPrice(i.getBagPrice()).updatedAt(i.getUpdatedAt()).build();
    }

    public void ensureAvailable(FeedInventory i, int bags) {
        if (bags <= 0) throw new BusinessRuleException("Enter at least one feed bag.");
        if (i.getStockBags() < bags) throw new BusinessRuleException("Insufficient feed stock. Available: " + i.getStockBags() + " bags.");
        if (i.getBagPrice() == null || i.getBagPrice().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessRuleException("Admin must set the feed bag price in the Feed tab before recording sales.");
    }
}
