package com.zn.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zn.payment.entity.Discounts;
public interface DiscountsRepository extends JpaRepository<Discounts, Long> {
    // Custom query methods can be defined here if needed
    
}
