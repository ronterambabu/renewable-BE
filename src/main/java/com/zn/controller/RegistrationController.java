package com.zn.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.dto.PriceCalculationRequestDTO;
import com.zn.entity.PresentationType;
import com.zn.entity.PricingConfig;
import com.zn.entity.RegistrationForm;
import com.zn.repository.IPresentationTypeRepo;
import com.zn.repository.IPricingConfigRepository;
import com.zn.repository.IRegistrationFormRepository;

@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

    @Autowired
    private IPricingConfigRepository pricingConfigRepo;
    
    @Autowired
    private IPresentationTypeRepo presentationTypeRepository;

    @Autowired
    private IRegistrationFormRepository registrationFormRepository;
    
    @PostMapping("/get-pricing-config")
    public ResponseEntity<?> getPricingConfig(@RequestBody PriceCalculationRequestDTO request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Price calculation request is required.");
        }

        Optional<PresentationType> ptOpt = presentationTypeRepository.findByType(request.getPresentationType());
        if (ptOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid presentation type: " + request.getPresentationType());
        }
        PresentationType ptEntity = ptOpt.get();

        switch (request.getRegistrationType()) {
            case "REGISTRATION_ONLY":
                return pricingConfigRepo
                        .findByPresentationTypeAndNoAccommodation(ptEntity)
                        .map(PricingConfig::getTotalPrice)
                        .<ResponseEntity<?>>map(ResponseEntity::ok)
                        .orElse(ResponseEntity.status(404).body("No pricing config found for registration only."));

            case "REGISTRATION_AND_ACCOMMODATION":
                return pricingConfigRepo
                        .findByPresentationTypeAndAccommodationDetails(ptEntity,
                                                                       request.getNumberOfNights(),
                                                                       request.getNumberOfGuests())
                        .map(PricingConfig::getTotalPrice)
                        .<ResponseEntity<?>>map(ResponseEntity::ok)
                        .orElse(ResponseEntity.status(404).body("No pricing config found for registration with accommodation."));

            default:
                return ResponseEntity.badRequest().body("Invalid registration type.");
        }
    }
    
    @GetMapping("/get-all-presentation-types")
    public ResponseEntity<?> getAllPresentationTypes() {
		return ResponseEntity.ok(presentationTypeRepository.findAll());
	}
    @GetMapping("/get-all-pricing-configs")
    public ResponseEntity<?> getAllPricingConfigs() {
		return ResponseEntity.ok(pricingConfigRepo.findAll());
		
	}
    
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegistrationForm request) {
        Optional<PricingConfig> pcOpt = pricingConfigRepo.findById(request.getPricingConfig().getId());

        if (pcOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid pricingConfig ID.");
        }

        PricingConfig pc = pcOpt.get();

        // Optional: Validate amount
        if (request.getAmountPaid() == null ||
            request.getAmountPaid().compareTo(pc.getTotalPrice()) != 0) {
            return ResponseEntity.badRequest().body("AmountPaid must match the PricingConfig totalPrice.");
        }

        request.setPricingConfig(pc);
        // Optionally set amountPaid from pricingConfig directly
        // request.setAmountPaid(pc.getTotalPrice());

        RegistrationForm saved = registrationFormRepository.save(request);
        return ResponseEntity.ok(saved);
        
    }

    
}
