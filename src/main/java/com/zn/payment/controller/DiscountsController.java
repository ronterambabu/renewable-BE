package com.zn.payment.controller;


import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.payment.dto.CreateDiscountSessionRequest;
import com.zn.payment.service.DiscountsService;

import jakarta.servlet.http.HttpServletRequest;



@RestController
@RequestMapping("/api/discounts")
public class DiscountsController {
    @Autowired
    private  DiscountsService discountsService;

    // create stripe session
    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(@RequestBody CreateDiscountSessionRequest request) {
        Object result = discountsService.createSession(request);
        return ResponseEntity.ok(result);
    }
    // handle stripe webhook
  @PostMapping("/webhook")
    public ResponseEntity<?> handleStripeWebhook(HttpServletRequest request) throws IOException {
      
        Object result = discountsService.handleStripeWebhook(request);
        return ResponseEntity.ok(result);
    }



}