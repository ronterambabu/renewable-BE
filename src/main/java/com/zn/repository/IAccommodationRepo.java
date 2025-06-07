package com.zn.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zn.entity.Accommodation;

public interface IAccommodationRepo extends JpaRepository<Accommodation, Long>{

	 Optional<Accommodation> findByNightsAndGuests(int nights, int guests);
}
