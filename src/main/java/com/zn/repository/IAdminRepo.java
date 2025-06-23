package com.zn.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zn.entity.Admin;

public interface IAdminRepo extends JpaRepository<Admin, Integer> {
		
	
	Admin findByEmailAndPassword(String email, String password);
	Admin findByEmail(String email);
}
