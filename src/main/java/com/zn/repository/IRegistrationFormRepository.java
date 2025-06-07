package com.zn.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zn.entity.RegistrationForm;

public interface IRegistrationFormRepository extends JpaRepository<RegistrationForm, Long> {
	
}
