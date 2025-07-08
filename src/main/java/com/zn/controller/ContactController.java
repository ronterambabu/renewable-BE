package com.zn.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.dto.ContactFormDto;
import com.zn.service.EmailService;

@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "*") // Allow all origins for CORS
public class ContactController {

    @Autowired
    private EmailService emailService;

    @PostMapping
    public String sendContactMessage(@RequestBody ContactFormDto dto) {
        emailService.sendContactMessage(dto.getName(), dto.getEmail(), dto.getSubject(), dto.getMessage());
        return "Message sent successfully";
    }
}
