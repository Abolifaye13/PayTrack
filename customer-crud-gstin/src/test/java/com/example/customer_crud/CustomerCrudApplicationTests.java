
package com.example.customer_crud;

import com.example.customer_crud.repository.CustomerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import com.example.customer_crud.repository.AppUserRepository;
import com.example.customer_crud.repository.OrganisationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerCrudApplicationTests {

    static final MySQLContainer<?> mysql =
        new MySQLContainer<>("mysql:8.0");

    static {
        mysql.start();
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Organisation orgA;
    private Organisation orgB;

    @BeforeEach
void setUp() {

    customerRepository.deleteAll();
    appUserRepository.deleteAll();
    organisationRepository.deleteAll();

    // Create two organisations
    orgA = organisationRepository.save(
            new Organisation("Organisation A"));

    orgB = organisationRepository.save(
            new Organisation("Organisation B"));

    // Create admin for Organisation A
    AppUser adminA = new AppUser();
    adminA.setUsername("adminA");
    adminA.setPassword(passwordEncoder.encode("testAdminA123"));
    adminA.setRole("ADMIN");
    adminA.setOrganisation(orgA);
    appUserRepository.save(adminA);

    // Create admin for Organisation B
    AppUser adminB = new AppUser();
    adminB.setUsername("adminB");
    adminB.setPassword(passwordEncoder.encode("testAdminB123"));
    adminB.setRole("ADMIN");
    adminB.setOrganisation(orgB);
    appUserRepository.save(adminB);

    // Create a read-only user for Organisation A
    AppUser userA = new AppUser();
    userA.setUsername("userA");
    userA.setPassword(passwordEncoder.encode("testUserA123"));
    userA.setRole("USER");
    userA.setOrganisation(orgA);
    appUserRepository.save(userA);
}

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }

    private Customer createTestCustomer() {
        Customer customer = new Customer();
        customer.setName("Test Technologies");
        customer.setGstin("27ABCDE1234F1Z5");
        customer.setBillingAddress("Nagpur, Maharashtra");
        customer.setShippingAddress("Pune, Maharashtra");
        customer.setShippingAddress("Pune, Maharashtra");
        customer.setOrganisation(orgA);
        return customerRepository.save(customer);
    }

    @Test
    void adminCanCreateCustomer() throws Exception {
        String request = """
                {
                    "name": "ABC Technologies",
                    "gstin": "27ABCDE1234F1Z5",
                    "billingAddress": "Nagpur, Maharashtra",
                    "shippingAddress": "Pune, Maharashtra"
                }
                """;

        mockMvc.perform(post("/api/customers")
                .header("Authorization", basicAuth("adminA", "testAdminA123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isCreated());
    }

    @Test
    void userCannotCreateCustomer() throws Exception {
        String request = """
                {
                    "name": "ABC Technologies",
                    "gstin": "27ABCDE1234F1Z5",
                    "billingAddress": "Nagpur, Maharashtra",
                    "shippingAddress": "Pune, Maharashtra"
                }
                """;

        mockMvc.perform(post("/api/customers")
                .header("Authorization", basicAuth("userA", "testUserA123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanViewAllCustomers() throws Exception {
        mockMvc.perform(get("/api/customers")
                .header("Authorization", basicAuth("userA", "testUserA123")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanViewCustomerById() throws Exception {
        Customer customer = createTestCustomer();

        mockMvc.perform(get("/api/customers/" + customer.getId())
                .header("Authorization", basicAuth("adminA", "testAdminA123")))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotUpdateCustomer() throws Exception {
        Customer customer = createTestCustomer();

        String request = """
                {
                    "name": "Updated Technologies",
                    "gstin": "27ABCDE1234F1Z5",
                    "billingAddress": "Mumbai, Maharashtra",
                    "shippingAddress": "Pune, Maharashtra"
                }
                """;

        mockMvc.perform(put("/api/customers/" + customer.getId())
                .header("Authorization", basicAuth("userA", "testUserA123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateCustomer() throws Exception {
        Customer customer = createTestCustomer();

        String request = """
                {
                    "name": "Updated Technologies",
                    "gstin": "27ABCDE1234F1Z5",
                    "billingAddress": "Mumbai, Maharashtra",
                    "shippingAddress": "Pune, Maharashtra"
                }
                """;

        mockMvc.perform(put("/api/customers/" + customer.getId())
                .header("Authorization", basicAuth("adminA", "testAdminA123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotDeleteCustomer() throws Exception {
        Customer customer = createTestCustomer();

        mockMvc.perform(delete("/api/customers/" + customer.getId())
                .header("Authorization", basicAuth("userA", "testUserA123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteCustomer() throws Exception {
        Customer customer = createTestCustomer();

        mockMvc.perform(delete("/api/customers/" + customer.getId())
                .header("Authorization", basicAuth("adminA", "testAdminA123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidGstinReturns400ForAdmin() throws Exception {
        String request = """
                {
                    "name": "ABC Technologies",
                    "gstin": "INVALIDGSTIN",
                    "billingAddress": "Nagpur, Maharashtra",
                    "shippingAddress": "Pune, Maharashtra"
                }
                """;

        mockMvc.perform(post("/api/customers")
                .header("Authorization", basicAuth("adminA", "testAdminA123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminBCannotViewOrganisationACustomer() throws Exception {
        Customer customer = createTestCustomer();

        mockMvc.perform(get("/api/customers/" + customer.getId())
            .header("Authorization", basicAuth("adminB", "testAdminB123")))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminBCannotSeeOrganisationACustomersInList() throws Exception {
        createTestCustomer();

        mockMvc.perform(get("/api/customers")
            .header("Authorization", basicAuth("adminB", "testAdminB123")))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void adminBCannotUpdateOrganisationACustomer() throws Exception {

    Customer customer = createTestCustomer();

    String request = """
            {
                "name": "Updated By Admin B",
                "gstin": "27ABCDE1234F1Z5",
                "billingAddress": "Mumbai, Maharashtra",
                "shippingAddress": "Pune, Maharashtra"
            }
            """;

    mockMvc.perform(put("/api/customers/" + customer.getId())
            .header("Authorization", basicAuth("adminB", "testAdminB123"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminBCannotDeleteOrganisationACustomer() throws Exception {

    Customer customer = createTestCustomer();

    mockMvc.perform(delete("/api/customers/" + customer.getId())
            .header("Authorization", basicAuth("adminB", "testAdminB123")))
            .andExpect(status().isNotFound());

    org.junit.jupiter.api.Assertions.assertTrue(
            customerRepository.existsById(customer.getId()));
    }
}
