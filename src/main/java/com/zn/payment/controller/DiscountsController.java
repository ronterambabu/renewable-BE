package com.zn.payment.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.payment.dto.CreateDiscountSessionRequest;
import com.zn.payment.service.DiscountsService;



@RestController
@RequestMapping("/api/discounts")
public class DiscountsController {
    @Autowired
    private  DiscountsService discountsService;

    // create stripe session
    @PostMapping("/create-session")
    public String createSession(@RequestBody CreateDiscountSessionRequest request) { 

       

        if (request.getUnitAmount() == null || request.getUnitAmount() <= 0)
            return "Unit amount must be positive";
        //create stripe session 
        discountsService.createSession(request);
        return "Session created successfully";
    }

}



  