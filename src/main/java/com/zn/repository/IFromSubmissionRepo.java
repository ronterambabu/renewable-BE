package com.zn.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zn.entity.Form;

public interface IFromSubmissionRepo extends JpaRepository<Form,Long> {

}
