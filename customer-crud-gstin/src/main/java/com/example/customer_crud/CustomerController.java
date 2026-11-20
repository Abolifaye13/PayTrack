
package com.example.customer_crud;

import com.example.customer_crud.repository.AppUserRepository;
import com.example.customer_crud.repository.CustomerRepository;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;

    public CustomerController(
            CustomerRepository customerRepository,
            AppUserRepository appUserRepository) {

        this.customerRepository = customerRepository;
        this.appUserRepository = appUserRepository;
    }

    // Get the logged-in user's organisation ID
    private Long getOrganisationId(Authentication authentication) {

        AppUser user = appUserRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));

        return user.getOrganisation().getId();
    }

    // Create customer
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Customer createCustomer(
            @Valid @RequestBody Customer customer,
            Authentication authentication) {

        Long organisationId = getOrganisationId(authentication);

        customer.setId(null);

        Organisation organisation = new Organisation();
        organisation.setId(organisationId);

        customer.setOrganisation(organisation);

        return customerRepository.save(customer);
    }

    // Get all customers of logged-in user's organisation
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public List<Customer> getAllCustomers(
            Authentication authentication) {

        Long organisationId = getOrganisationId(authentication);

        return customerRepository.findByOrganisationId(organisationId);
    }

    // Get customer by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public Customer getCustomerById(
            @PathVariable Long id,
            Authentication authentication) {

        Long organisationId = getOrganisationId(authentication);

        return customerRepository
                .findByIdAndOrganisationId(id, organisationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));
    }

    // Update customer
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Customer updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody Customer customer,
            Authentication authentication) {

        Long organisationId = getOrganisationId(authentication);

        Customer existingCustomer = customerRepository
                .findByIdAndOrganisationId(id, organisationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));

        existingCustomer.setName(customer.getName());
        existingCustomer.setGstin(customer.getGstin());
        existingCustomer.setBillingAddress(customer.getBillingAddress());
        existingCustomer.setShippingAddress(customer.getShippingAddress());

        return customerRepository.save(existingCustomer);
    }

    // Delete customer
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(
            @PathVariable Long id,
            Authentication authentication) {

        Long organisationId = getOrganisationId(authentication);

        Customer customer = customerRepository
                .findByIdAndOrganisationId(id, organisationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));

        customerRepository.delete(customer);
    }
}
