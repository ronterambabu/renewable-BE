package com.zn.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.dto.AbstractSubmissionRequestDTO;
import com.zn.service.FormSubmissionService;

@RestController
@RequestMapping("/api/form-submission")
public class FormSubmission {
	
	
	
	@Autowired
	private FormSubmissionService formSubmissionService;
	
	
	
	
	// handle form submission logic here
	@PostMapping("/submit")
	public ResponseEntity<String> submitForm(@ModelAttribute AbstractSubmissionRequestDTO request) {

		// Validate and process the request
		if (request.getAbstractFile() == null || request.getAbstractFile().isEmpty()) {
			return ResponseEntity.badRequest().body("Abstract file is required.");
		}
		
		// Save the submission to the database or perform other business logic
		formSubmissionService.saveSubmission(request);
		
		return ResponseEntity.ok("Form submitted successfully.");
	}

	// get all interested in options find all interested in options
	@PostMapping("/get-interested-in-options")
	public ResponseEntity<?> getInterestedInOptions() {
		List<?> interestedInOptions = formSubmissionService.getInterestedInOptions();
		return ResponseEntity.ok(interestedInOptions);	
		
		
	}
	// get all session options
	@PostMapping("/get-session-options")
	public ResponseEntity<?> getSessionOptions() {
		List<?> sessionOptions = formSubmissionService.getSessionOptions();
		return ResponseEntity.ok(sessionOptions);	
	}

}
