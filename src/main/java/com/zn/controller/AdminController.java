package com.zn.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.entity.Accommodation;
import com.zn.entity.Admin;
import com.zn.entity.PresentationType;
import com.zn.entity.PricingConfig;
import com.zn.repository.IAccommodationRepo;
import com.zn.repository.IPresentationTypeRepo;
import com.zn.service.AdminService;

@RestController
@RequestMapping("/admin")
public class AdminController {
	@Autowired
	private AdminService adminService;
	
	@Autowired
	private IPresentationTypeRepo presentationTypeRepository;
	@Autowired
	private IAccommodationRepo accommodationRepository;
	
	
	// login admin
	@PostMapping("/api/admin/login")
	public ResponseEntity<String> loginAdmin(@RequestBody Admin adminCredentials) {
		if (adminCredentials == null || adminCredentials.getEmail() == null || adminCredentials.getPassword() == null) {
			return ResponseEntity.badRequest().body("Admin credentials are required.");
		}
		
		String response = adminService.loginAdmin(adminCredentials);
		if (response == "Invalid username or password.") {
			return ResponseEntity.status(401).body("Invalid credentials.");
		}
		else if (response.equals("Admin not found")) {
			return ResponseEntity.status(404).body("Admin not found.");
			
		}
		else if (response.equals("Login successful.")) {
			return ResponseEntity.ok("Login successful.");
		}
		else {
			return ResponseEntity.status(500).body("An error occurred during login.");
		}
	}
	
	// insert Sessions in SessionOption table
	@PostMapping("/api/admin/sessions")
	public ResponseEntity<String> insertSessions(@RequestBody String sessionOption) {
		if (sessionOption == null || sessionOption.isEmpty()) {
			return ResponseEntity.badRequest().body("Session option is required.");
		}
		
		String response = adminService.insertSession(sessionOption);
		return ResponseEntity.ok(response);
	}
	
	// inseet InterestedInOptions in InterestedInOptions table
	@PostMapping("/api/admin/interested-in")
	public ResponseEntity<String> insertInterestedInOptions(@RequestBody String interestedInOption) {
		if (interestedInOption == null || interestedInOption.isEmpty()) {
			return ResponseEntity.badRequest().body("Interested In option is required.");
		}
		
		adminService.insertInterestedInOption(interestedInOption);
		return ResponseEntity.ok("Interested In option inserted successfully.");
	}
	
	
	// insert acoommodation in Accommodation table
	@PostMapping("/api/admin/accommodation")
	public ResponseEntity<String> insertAccommodation(@RequestBody Accommodation accommodation) {
		if (accommodation == null) {
			return ResponseEntity.badRequest().body("Accommodation  is required.");
		}
		
		adminService.insertAccommodation(accommodation);
		return ResponseEntity.ok("Accommodation inserted successfully.");
	}
	// insert presenttation type in PresentationType table
	@PostMapping("/api/admin/presentation-type")
	public ResponseEntity<String> insertPresentationType(@RequestBody PresentationType presentationType) {
		if (presentationType == null || presentationType.getType() == null ) {
			return ResponseEntity.badRequest().body("Presentation type is required.");
		}
		
		adminService.insertPresentationType(presentationType);
		return ResponseEntity.ok("Presentation type inserted successfully.");
	}
	
	// insert pricing config in PricingConfig table
	// note : insert accommodation and presentation type first before inserting pricing config
	@PostMapping("/api/admin/pricing-config")
	public ResponseEntity<?> insertPricingConfig(@RequestBody PricingConfig config) {
	    try {
	        // Step 1: Extract and save PresentationType
	        PresentationType presentationType = config.getPresentationType();
	        if (presentationType == null || presentationType.getType() == null) {
	            return ResponseEntity.badRequest().body("Presentation type is required.");
	        }
	        PresentationType savedPresentationType = presentationTypeRepository.save(presentationType);

	        // Step 2: Extract and save AccommodationOption (optional)
	        Accommodation savedAccommodation = null;
	        if (config.getAccommodationOption() != null) {
	            Accommodation accommodation = config.getAccommodationOption();
	            savedAccommodation = accommodationRepository.save(accommodation);
	        }

	        // Step 3: Set saved PresentationType and Accommodation back to config
	        config.setPresentationType(savedPresentationType);
	        config.setAccommodationOption(savedAccommodation);

	        // Step 4: Save PricingConfig
	        PricingConfig savedConfig = adminService.insertPricingConfig(config);

	        return ResponseEntity.ok(savedConfig);

	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("Failed to insert pricing config: " + e.getMessage());
	    }
	}

	
		
		
		
		
		
		
		
		
		
		
		
		
		
		
//	    Long presentationTypeId = config.getPresentationType().getId();
//	    Long accommodationId = config.getAccommodationOption() != null
//	        ? config.getAccommodationOption().getId()
//	        : null;
//
//	    PricingConfig saved = adminService.insertPricingConfig(config, presentationTypeId, accommodationId);
//	    return ResponseEntity.ok(saved);
//	}
	// get the prising config details by id
	@PostMapping("/api/admin/pricing-config/details/{id}")
    	public ResponseEntity<?> getPricingConfigDetails(@RequestBody Long id) {
		if (id == null) {
			return ResponseEntity.badRequest().body("Pricing config ID is required.");
		}
		
		PricingConfig config = adminService.getPricingConfigById(id);
		if (config == null) {
			return ResponseEntity.notFound().build();
		}
		
		return ResponseEntity.ok(config);
	}	
	// get all registration forms
	@PostMapping("/api/admin/registration-forms")
	public ResponseEntity<?> getAllRegistrationForms() {
		return ResponseEntity.ok(adminService.getAllRegistrationForms());
	}

}
