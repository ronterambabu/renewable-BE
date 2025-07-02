package com.zn.controller;


import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zn.dto.PriceCalculationRequestDTO;
import com.zn.dto.PricingConfigResponseDTO;
import com.zn.entity.PresentationType;
import com.zn.entity.PricingConfig;
import com.zn.entity.RegistrationForm;
import com.zn.repository.IAccommodationRepo;
import com.zn.repository.IPresentationTypeRepo;
import com.zn.repository.IPricingConfigRepository;
import com.zn.repository.IRegistrationFormRepository;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/registration")
@Slf4j
public class RegistrationController {

    @Autowired
    private IPricingConfigRepository pricingConfigRepo;
    
    @Autowired
    private IPresentationTypeRepo presentationTypeRepository;

    @Autowired
    private IRegistrationFormRepository registrationFormRepository;
    
    
    
    @Autowired
    private IAccommodationRepo accommodationRepository;
    
    
//    @PostMapping("/get-pricing-config")
//    public ResponseEntity<?> getPricingConfig(@RequestBody PriceCalculationRequestDTO request) {
//        if (request == null) {
//            return ResponseEntity.badRequest().body("Price calculation request is required.");
//        }
//
//        Optional<PresentationType> ptOpt = presentationTypeRepository.findByType(request.getPresentationType());
//        if (ptOpt.isEmpty()) {
//            return ResponseEntity.badRequest().body("Invalid presentation type: " + request.getPresentationType());
//        }
//        PresentationType ptEntity = ptOpt.get();
//
//        switch (request.getRegistrationType()) {
//            case "REGISTRATION_ONLY":
//                return pricingConfigRepo
//                        .findByPresentationTypeAndNoAccommodation(ptEntity)
//                        .map(PricingConfig::getTotalPrice)
//                        .<ResponseEntity<?>>map(ResponseEntity::ok)
//                        .orElse(ResponseEntity.status(404).body("No pricing config found for registration only."));
//
//            case "REGISTRATION_AND_ACCOMMODATION":
//                return pricingConfigRepo
//                        .findByPresentationTypeAndAccommodationDetails(ptEntity,
//                                                                       request.getNumberOfNights(),
//                                                                       request.getNumberOfGuests())
//                        .map(PricingConfig::getTotalPrice)
//                        .<ResponseEntity<?>>map(ResponseEntity::ok)
//                        .orElse(ResponseEntity.status(404).body("No pricing config found for registration with accommodation."));
//
//            default:
//                return ResponseEntity.badRequest().body("Invalid registration type.");
//        }
//    }
    
    @PostMapping("/get-pricing-config")
    public ResponseEntity<?> getPricingConfigs(@RequestBody PriceCalculationRequestDTO request) {
        if (request == null) {
            log.warn("Received null price calculation request.");
            return ResponseEntity.badRequest().body("Price calculation request is required.");
        }

        log.info("Received request: {}", request);

        Optional<PresentationType> ptOpt = presentationTypeRepository.findByType(request.getPresentationType());
        if (ptOpt.isEmpty()) {
            log.warn("Invalid presentation type: {}", request.getPresentationType());
            return ResponseEntity.badRequest().body("Invalid presentation type: " + request.getPresentationType());
        }

        PresentationType ptEntity = ptOpt.get();
        log.info("Resolved PresentationType entity: {}", ptEntity);

        List<PricingConfig> results;

        switch (request.getRegistrationType()) {
            case "REGISTRATION_ONLY":
                log.info("Fetching pricing config with NO accommodation for presentation type: {}", ptEntity.getType());
                results = pricingConfigRepo.findAllByPresentationTypeAndNoAccommodation(ptEntity);
                break;

            case "REGISTRATION_AND_ACCOMMODATION":
                log.info("Fetching pricing config WITH accommodation: nights={}, guests={}, type={}",
                        request.getNumberOfNights(), request.getNumberOfGuests(), ptEntity.getType());
                results = pricingConfigRepo.findAllByPresentationTypeAndAccommodationDetails(
                        ptEntity, request.getNumberOfNights(), request.getNumberOfGuests());
                break;

            default:
                log.warn("Invalid registration type: {}", request.getRegistrationType());
                return ResponseEntity.badRequest().body("Invalid registration type.");
        }

        if (results.isEmpty()) {
            log.warn("No pricing configurations found for type={}, registrationType={}, nights={}, guests={}",
                    request.getPresentationType(), request.getRegistrationType(),
                    request.getNumberOfNights(), request.getNumberOfGuests());
            return ResponseEntity.status(404).body("No pricing configurations found for the provided criteria.");
        }

        List<PricingConfigResponseDTO> dtoList = results.stream().map(p -> {
            PricingConfigResponseDTO dto = new PricingConfigResponseDTO();
            dto.setId(p.getId());
            dto.setTotalPrice(p.getTotalPrice());
            dto.setProcessingFeePercent(p.getProcessingFeePercent());
            dto.setPresentationType(p.getPresentationType());
            dto.setAccommodationOption(p.getAccommodationOption());
            return dto;
        }).collect(Collectors.toList());

        log.info("Returning {} pricing config(s).", dtoList.size());

        return ResponseEntity.ok(dtoList);
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

    // get all accommodation options
    @GetMapping("/get-all-accommodation-options")
    public ResponseEntity<?> getAllRegistrationForms() {
		return ResponseEntity.ok(accommodationRepository.findAll());
	}
}
