package com.zn.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zn.entity.PresentationType;
import com.zn.entity.PricingConfig;

public interface IPricingConfigRepository extends JpaRepository<PricingConfig, Long> {
	 // Valid custom query for registration only (no accommodation)
	  @Query("SELECT p FROM PricingConfig p WHERE p.presentationType = :presentationType AND p.accommodationOption IS NULL")
	    Optional<PricingConfig> findByPresentationTypeAndNoAccommodation(@Param("presentationType") PresentationType presentationType);

	    // Query for PricingConfig with accommodation matching nights and guests
	  @Query("SELECT pc FROM PricingConfig pc " +
		       "JOIN pc.accommodationOption a " +
		       "WHERE pc.presentationType = :presentationType " +
		       "AND a.nights = :nights AND a.guests = :guests")
		Optional<PricingConfig> findByPresentationTypeAndAccommodationDetails(
		        @Param("presentationType") PresentationType presentationType,
		        @Param("nights") int nights,
		        @Param("guests") int guests);
}
