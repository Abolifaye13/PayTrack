
package com.example.customer_crud.repository;

import com.example.customer_crud.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByOrganisationId(Long organisationId);

    Optional<Customer> findByIdAndOrganisationId(
            Long id, Long organisationId);

}
