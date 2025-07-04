package com.zn.entity;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Accommodation { @Id
	
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int nights;

    private int guests;

    @Column(nullable = false)
    private BigDecimal price;}


