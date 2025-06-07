package com.zn.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class InterestedInOption {

    public InterestedInOption(String interestedInOption) {
		this.option_name = interestedInOption;
	}

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String option_name; // e.g., "Speaker", "Poster", "Student"


}
