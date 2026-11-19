package com.example.customer_crud.repository;

import com.example.customer_crud.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrganisationRepository
        extends JpaRepository<Organisation, Long> {

    Optional<Organisation> findByName(String name);
}