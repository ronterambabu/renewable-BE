package com.zn.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.zn.entity.Accommodation;
import com.zn.entity.Admin;
import com.zn.entity.Form;
import com.zn.entity.InterestedInOption;
import com.zn.entity.PresentationType;
import com.zn.entity.PricingConfig;
import com.zn.entity.RegistrationForm;
import com.zn.entity.SessionOption;
import com.zn.repository.IAccommodationRepo;
import com.zn.repository.IAdminRepo;
import com.zn.repository.IFromSubmissionRepo;
import com.zn.repository.IIntrestedInOptionsRepo;
import com.zn.repository.IPresentationTypeRepo;
import com.zn.repository.IPricingConfigRepository;
import com.zn.repository.IRegistrationFormRepository;
import com.zn.repository.ISessionOption;

@Service
public class AdminService {
	   @Value("${supabase.url}")
	    private String SUPABASE_URL;

	    @Value("${supabase.bucket}")
	    private String BUCKET_NAME;

	    @Value("${supabase.api.key}")
	    private String SUPABASE_API_KEY;
	@Autowired
	private ISessionOption sessionOptionRepo;
	@Autowired
	private IIntrestedInOptionsRepo interestedInOptionsRepo;
	@Autowired
	private IFromSubmissionRepo fromSubmissionRepo;
	
	@Autowired
	private IAccommodationRepo accommodationRepo;
	@Autowired
	private IPresentationTypeRepo presentationTypeRepo;
	
	@Autowired
	private IRegistrationFormRepository registrationFormRepo;
	
	@Autowired
	private IPricingConfigRepository pricingConfigRepo;
	
	@Autowired
	private IAdminRepo adminRepo;
	
	  private final RestTemplate restTemplate = new RestTemplate();


	public String insertSession(String sessionOption) {
		if (sessionOption == null || sessionOption.isEmpty()) {
			return "Session option is required.";
		}
		
		// Assuming SessionOption is an entity class with a constructor that accepts a String
		SessionOption option = new SessionOption(sessionOption);
		sessionOptionRepo.save(option);
		
		return "Session option inserted successfully.";
		
		
	}


	public String insertInterestedInOption(String interestedInOption) {

		if (interestedInOption == null || interestedInOption.isEmpty()) {
			return "Interested In option is required.";
		}
		
		// Assuming InterestedInOption is an entity class with a constructor that accepts a String
		InterestedInOption option = new InterestedInOption(interestedInOption);
		interestedInOptionsRepo.save(option);
		
		return "Interested In option inserted successfully.";
	}
	public List<Form> getAllFormSubmissions() {
		try {
			return fromSubmissionRepo.findAll();
		} catch (Exception e) {
			e.printStackTrace();
			return null; // or handle the error appropriately
		}
		
	}


	public String insertAccommodation(Accommodation accommodation) {
		if (accommodation == null) {
			return "Accommodation name is required.";
		}
		accommodationRepo.save(accommodation);
		return "Accommodation inserted successfully.";
		
		
	}


	public String insertPresentationType(PresentationType presentationType) {
		if (presentationType == null || presentationType.getType() == null) {
			return "Presentation type is required.";
		}
		
		// Assuming PresentationType is an entity class with a constructor that accepts a String
		presentationTypeRepo.save(presentationType);
		
		return "Presentation type inserted successfully.";		
	}


	public PricingConfig insertPricingConfig(PricingConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("PricingConfig cannot be null.");
		}
		if (config.getPresentationType() == null) {
			throw new IllegalArgumentException("Presentation type is required.");
		}
		if (config.getProcessingFeePercent() < 0) {
			throw new IllegalArgumentException("Processing fee percent cannot be negative.");
		}
		if (config.getPresentationType().getPrice() == null) {
			throw new IllegalStateException("Presentation type price cannot be null.");
		}
		config.calculateTotalPrice(); // Ensure total price is calculated before saving
		return pricingConfigRepo.save(config); // ✅ returning saved entity
		
		
		
		
		
		
		
		/* PresentationType presentationType = presentationTypeRepo.findById(presentationTypeId)
		    .orElseThrow(() -> new IllegalArgumentException("Invalid presentation type ID"));
		
		if (presentationType.getPrice() == null) {
		    throw new IllegalStateException("Presentation type price cannot be null.");
		}
		
		config.setPresentationType(presentationType);
		
		if (accommodationId != null) {
		    Accommodation accommodation = accommodationRepo.findById(accommodationId)
		        .orElseThrow(() -> new IllegalArgumentException("Invalid accommodation ID"));
		
		    if (accommodation.getPrice() == null) {
		        throw new IllegalStateException("Accommodation price cannot be null.");
		    }
		
		    config.setAccommodationOption(accommodation);
		}
		
		return pricingConfigRepo.save(config); // ✅ returning saved entity
		*/	}


	public PricingConfig getPricingConfigById(Long id) {
		
		if (id == null) {
			return null;
		}
		return pricingConfigRepo.findById(id)
				.orElse(null); // Return null if not found, or handle as needed		
		
		
		
	}


	public List<RegistrationForm> getAllRegistrationForms() {
		
		try {
			return registrationFormRepo.findAll();
		} catch (Exception e) {
			e.printStackTrace();
			return null; // or handle the error appropriately
		}
		
		
	}


	public String loginAdmin(Admin adminCredentials) {
		if (adminCredentials == null || adminCredentials.getEmail() == null || adminCredentials.getPassword() == null) {
			return "Username and password are required.";
		}
		Admin admin = adminRepo.findByEmailAndPassword(adminCredentials.getEmail(),adminCredentials.getPassword());
		if (admin == null) {
			return "Invalid username or password.";
		}
		return "Login successful.";
		
		
	}

	
	
	
}
