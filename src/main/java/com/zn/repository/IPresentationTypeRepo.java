package com.zn.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zn.entity.PresentationType;

public interface IPresentationTypeRepo extends JpaRepository<PresentationType,Long> {
	  Optional<PresentationType> findByType(String type);


}
