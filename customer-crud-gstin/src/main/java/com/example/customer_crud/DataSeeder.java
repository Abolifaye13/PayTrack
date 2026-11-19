package com.example.customer_crud;

import com.example.customer_crud.repository.AppUserRepository;
import com.example.customer_crud.repository.OrganisationRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(
            OrganisationRepository organisationRepository,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            Organisation orgA = organisationRepository
                    .findByName("Organisation A")
                    .orElseGet(() -> organisationRepository
                            .save(new Organisation("Organisation A")));

            Organisation orgB = organisationRepository
                    .findByName("Organisation B")
                    .orElseGet(() -> organisationRepository
                            .save(new Organisation("Organisation B")));

            if (appUserRepository.findByUsername("adminA").isEmpty()) {
                AppUser userA = new AppUser();
                userA.setUsername("adminA");
                userA.setPassword(passwordEncoder.encode(
                        System.getenv("SEED_ADMIN_A_PASSWORD")));
                userA.setRole("ADMIN");
                userA.setOrganisation(orgA);
                appUserRepository.save(userA);
            }

            if (appUserRepository.findByUsername("adminB").isEmpty()) {
                AppUser userB = new AppUser();
                userB.setUsername("adminB");
                userB.setPassword(passwordEncoder.encode(
                        System.getenv("SEED_ADMIN_B_PASSWORD")));
                userB.setRole("ADMIN");
                userB.setOrganisation(orgB);
                appUserRepository.save(userB);
            }
        };
    }
}