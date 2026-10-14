package com.vpf.service;

import com.vpf.dto.ExpenseRequest;
import com.vpf.dto.ExpenseResponse;
import com.vpf.entity.Expense;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseResponse create(ExpenseRequest req) {
        Expense e = new Expense();
        apply(e, req);
        expenseRepository.save(e);
        return toResponse(e);
    }

    public ExpenseResponse update(Long id, ExpenseRequest req) {
        Expense e = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
        apply(e, req);
        expenseRepository.save(e);
        return toResponse(e);
    }

    public void delete(Long id) {
        expenseRepository.deleteById(id);
    }

    public List<ExpenseResponse> findAll() {
        return expenseRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<ExpenseResponse> findByDateRange(LocalDate from, LocalDate to) {
        return expenseRepository.findByExpenseDateBetweenOrderByExpenseDateAsc(from, to).stream().map(this::toResponse).toList();
    }

    private void apply(Expense e, ExpenseRequest req) {
        e.setExpenseDate(req.getExpenseDate());
        e.setCategory(req.getCategory());
        e.setAmount(req.getAmount());
        e.setDescription(req.getDescription());
        e.setNotes(req.getNotes());
        if (e.getCreatedBy() == null) {
            e.setCreatedBy(req.getCreatedBy());
        }
    }

    private ExpenseResponse toResponse(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .expenseDate(e.getExpenseDate())
                .category(e.getCategory())
                .amount(e.getAmount())
                .description(e.getDescription())
                .notes(e.getNotes())
                .createdBy(e.getCreatedBy())
                .createdDate(e.getCreatedDate())
                .build();
    }
}
