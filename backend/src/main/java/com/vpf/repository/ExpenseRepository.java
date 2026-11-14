package com.vpf.repository;

import com.vpf.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateAsc(LocalDate from, LocalDate to);

    List<Expense> findByExpenseDate(LocalDate date);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate between :from and :to")
    BigDecimal sumExpensesBetween(LocalDate from, LocalDate to);
}
