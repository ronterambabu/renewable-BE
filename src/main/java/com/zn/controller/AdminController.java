package com.zn.controller;

import java.util.Optional;

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
	
	@Autowired
	private com.zn.repository.IPricingConfigRepository pricingConfigRepository;
	
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
	@PostMapping("/api/admin/pricing-config")
	public ResponseEntity<?> insertPricingConfig(@RequestBody PricingConfig config) {
	    try {
	        // Step 1: Validate PresentationType
	        PresentationType inputType = config.getPresentationType();
	        if (inputType == null || inputType.getType() == null) {
	            return ResponseEntity.badRequest().body("Presentation type is required.");
	        }

	        // Step 2: Try to fetch PresentationType
	        PresentationType savedPresentationType = presentationTypeRepository.findByType(inputType.getType())
	            .orElse(null);

	        if (savedPresentationType == null) {
	            try {
	                savedPresentationType = presentationTypeRepository.save(inputType);
	            } catch (Exception e) {
	                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                    .body("Failed to save PresentationType: " + e.getMessage());
	            }
	        }

	        // Step 3: Handle Accommodation
	        Accommodation savedAccommodation = null;
	        if (config.getAccommodationOption() != null) {
	            Accommodation acc = config.getAccommodationOption();
	            savedAccommodation = accommodationRepository.findByNightsAndGuests(acc.getNights(), acc.getGuests())
	                .orElse(null);
	            if (savedAccommodation == null) {
	                try {
	                    savedAccommodation = accommodationRepository.save(acc);
	                } catch (Exception e) {
	                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                        .body("Failed to save Accommodation: " + e.getMessage());
	                }
	            }
	        }

	        // Step 4: Check for PricingConfig WITH Accommodation
	        boolean insertedWithAccommodation = false;
	        if (savedAccommodation != null) {
	            Optional<PricingConfig> existingWithAcc = pricingConfigRepository
	                .findByPresentationTypeAndAccommodationOption(savedPresentationType, savedAccommodation);
	            if (existingWithAcc.isEmpty()) {
	                PricingConfig withAcc = new PricingConfig();
	                withAcc.setPresentationType(savedPresentationType);
	                withAcc.setAccommodationOption(savedAccommodation);
	                withAcc.setProcessingFeePercent(config.getProcessingFeePercent());
	                adminService.insertPricingConfig(withAcc);
	                insertedWithAccommodation = true;
	            }
	        }

	        // Step 5: Check for PricingConfig WITHOUT Accommodation
	        Optional<PricingConfig> existingWithoutAcc = pricingConfigRepository
	            .findByPresentationTypeAndAccommodationOption(savedPresentationType, null);

	        boolean insertedWithoutAccommodation = false;
	        if (existingWithoutAcc.isEmpty()) {
	            PricingConfig withoutAcc = new PricingConfig();
	            withoutAcc.setPresentationType(savedPresentationType);
	            withoutAcc.setAccommodationOption(null);
	            withoutAcc.setProcessingFeePercent(config.getProcessingFeePercent());
	            adminService.insertPricingConfig(withoutAcc);
	            insertedWithoutAccommodation = true;
	        }

	        // Step 6: Response
	        if (!insertedWithAccommodation && !insertedWithoutAccommodation) {
	            return ResponseEntity.status(HttpStatus.CONFLICT)
	                .body("Both pricing configurations already exist.");
	        }

	        return ResponseEntity.ok("Pricing configurations inserted:"
	            + (insertedWithAccommodation ? " with accommodation" : "")
	            + (insertedWithoutAccommodation ? " without accommodation" : ""));

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
