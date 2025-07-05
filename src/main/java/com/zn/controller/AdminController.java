package com.zn.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.dto.AdminLoginRequestDTO;
import com.zn.dto.AdminResponseDTO;
import com.zn.dto.InterestedInOptionDTO;
import com.zn.dto.SessionOptionDTO;
import com.zn.entity.Accommodation;
import com.zn.entity.Admin;
import com.zn.entity.PresentationType;
import com.zn.entity.PricingConfig;
import com.zn.exception.AdminAuthenticationException;
import com.zn.exception.DataProcessingException;
import com.zn.exception.ResourceNotFoundException;
import com.zn.repository.IAccommodationRepo;
import com.zn.repository.IPresentationTypeRepo;
import com.zn.security.JwtUtil;
import com.zn.service.AdminService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/admin")
public class AdminController {	@Autowired
	private AdminService adminService;
	
	@Autowired
	private JwtUtil jwtUtil;
	
	@Autowired
	private IPresentationTypeRepo presentationTypeRepository;
	@Autowired
	private IAccommodationRepo accommodationRepository;
	
	@Autowired
	private com.zn.repository.IPricingConfigRepository pricingConfigRepository;	
	// login admin	
	@PostMapping("/api/admin/login")
	public ResponseEntity<?> loginAdmin(@RequestBody AdminLoginRequestDTO loginRequest, HttpServletResponse response) {
		try {
			if (loginRequest == null || loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
				throw new IllegalArgumentException("Email and password are required");
			}

			if (loginRequest.getEmail().trim().isEmpty() || loginRequest.getPassword().trim().isEmpty()) {
				throw new IllegalArgumentException("Email and password cannot be empty");
			}

			// Create admin object for authentication
			Admin adminCredentials = new Admin();
			adminCredentials.setEmail(loginRequest.getEmail());
			adminCredentials.setPassword(loginRequest.getPassword());

			Admin admin = adminService.loginAdmin(adminCredentials);
			if (admin == null) {
				throw new AdminAuthenticationException("Invalid email or password");
			}			// Generate JWT token with role
			String token = jwtUtil.generateToken(admin.getEmail(), admin.getRole());			// Set JWT as HttpOnly cookie with production-ready settings
			ResponseCookie cookie = ResponseCookie.from("admin_jwt", token)
				.httpOnly(false)
				.secure(true) // Always true for production HTTPS
				.path("/")
				.maxAge(24 * 60 * 60) // 1 day
				.sameSite("None") // Required for cross-origin cookies
				.build();
			response.addHeader("Set-Cookie", cookie.toString());

			// Create response DTO with user info and token for production compatibility
			AdminResponseDTO adminResponse = new AdminResponseDTO(
				admin.getId().longValue(),
				admin.getEmail(),
				admin.getName(),
				admin.getRole()
			);
			
			// Create response with both user data and token for production
			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("user", adminResponse);
			responseBody.put("token", token); // Include token for production use
			
			return ResponseEntity.ok(responseBody);
		} catch (Exception e) {
			throw new AdminAuthenticationException("Login failed: " + e.getMessage(), e);
		}
	}
		// insert Sessions in SessionOption table
	 @PostMapping("/sessions")
	 @PreAuthorize("hasRole('ADMIN')")
	    public ResponseEntity<String> insertSession(@RequestBody SessionOptionDTO dto) {
	        try {
	            if (dto == null || dto.getSessionOption() == null || dto.getSessionOption().trim().isEmpty()) {
	                throw new IllegalArgumentException("Session option is required and cannot be empty");
	            }

	            String result = adminService.insertSession(dto.getSessionOption().trim());
	            return ResponseEntity.ok(result);
	        } catch (Exception e) {
	            throw new DataProcessingException("Failed to insert session: " + e.getMessage(), e);
	        }
	    }	    @PostMapping("/interested-in")
	    public ResponseEntity<String> insertInterestedInOption(@RequestBody InterestedInOptionDTO dto) {
	        try {
	            if (dto == null || dto.getInterestedInOption() == null || dto.getInterestedInOption().trim().isEmpty()) {
	                throw new IllegalArgumentException("Interested In option is required and cannot be empty");
	            }

	            adminService.insertInterestedInOption(dto.getInterestedInOption().trim());
	            return ResponseEntity.ok("Interested In option inserted successfully.");
	        } catch (Exception e) {
	            throw new DataProcessingException("Failed to insert interested in option: " + e.getMessage(), e);
	        }
	    }
		// insert acoommodation in Accommodation table
	@PostMapping("/api/admin/accommodation")
	 @PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<String> insertAccommodation(@RequestBody Accommodation accommodation) {
		try {
			if (accommodation == null) {
				throw new IllegalArgumentException("Accommodation is required");
			}
			
			adminService.insertAccommodation(accommodation);
			return ResponseEntity.ok("Accommodation inserted successfully.");
		} catch (Exception e) {
			throw new DataProcessingException("Failed to insert accommodation: " + e.getMessage(), e);
		}
	}
	@PostMapping("/api/admin/pricing-config")
	 @PreAuthorize("hasRole('ADMIN')")
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
//	}	// get the prising config details by id
	@PostMapping("/api/admin/pricing-config/details/{id}")
    public ResponseEntity<?> getPricingConfigDetails(@RequestBody Long id) {
		try {
			if (id == null) {
				throw new IllegalArgumentException("Pricing config ID is required");
			}
			
			PricingConfig config = adminService.getPricingConfigById(id);
			if (config == null) {
				throw new ResourceNotFoundException("Pricing config not found with ID: " + id);
			}
			
			return ResponseEntity.ok(config);
		} catch (Exception e) {
			throw new DataProcessingException("Failed to retrieve pricing config: " + e.getMessage(), e);
		}
	}	// get all registration forms
	@PostMapping("/api/admin/registration-forms")
	 @PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<?> getAllRegistrationForms() {
		try {
			return ResponseEntity.ok(adminService.getAllRegistrationForms());
		} catch (Exception e) {
			throw new DataProcessingException("Failed to retrieve registration forms: " + e.getMessage(), e);
		}
	}
	
	// get all abstract form submissions
	@GetMapping("/api/admin/abstract-submissions")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<?> getAllAbstractSubmissions() {
		try {
			return ResponseEntity.ok(adminService.getAllAbstractSubmissions());
		} catch (Exception e) {
			throw new DataProcessingException("Failed to retrieve abstract submissions: " + e.getMessage(), e);
		}
	}
	
	// logout admin
	@PostMapping("/api/admin/logout")
	public ResponseEntity<String> logoutAdmin(HttpServletResponse response) {
		try {
			// Clear the JWT cookie
			ResponseCookie cookie = ResponseCookie.from("admin_jwt", "")
				.httpOnly(true)
				.secure(false) // set to true in production
				.path("/")
				.maxAge(0) // immediately expire
				.sameSite("Lax")
				.build();
			response.addHeader("Set-Cookie", cookie.toString());
			
			return ResponseEntity.ok("Logged out successfully");
		} catch (Exception e) {
			return ResponseEntity.status(500).body("Logout failed");
		}
	}
	// write a method to edit accomidation combo 
	 

	
	// Edit accommodation combo
    @PostMapping("/api/admin/accommodation/edit/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> editAccommodation(@PathVariable Long id, @RequestBody Accommodation updatedAccommodation) {
        try {
            Optional<Accommodation> optionalAccommodation = accommodationRepository.findById(id);
            if (optionalAccommodation.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Accommodation not found with ID: " + id);
            }
            Accommodation accommodation = optionalAccommodation.get();
            // Update fields
            accommodation.setNights(updatedAccommodation.getNights());
            accommodation.setGuests(updatedAccommodation.getGuests());
            accommodation.setPrice(updatedAccommodation.getPrice());
            accommodationRepository.save(accommodation);
            return ResponseEntity.ok("Accommodation updated successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update accommodation: " + e.getMessage());
        }
    }

    // Delete accommodation combo
    @PostMapping("/api/admin/accommodation/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAccommodation(@PathVariable Long id) {
        try {
            Optional<Accommodation> optionalAccommodation = accommodationRepository.findById(id);
            if (optionalAccommodation.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Accommodation not found with ID: " + id);
            }
            accommodationRepository.deleteById(id);
            return ResponseEntity.ok("Accommodation deleted successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete accommodation: " + e.getMessage());
        }
    }
}
