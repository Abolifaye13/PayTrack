
CREATE TABLE organisations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organisations_name (name)
) ENGINE=InnoDB;

CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    organisation_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_users_username (username),
    CONSTRAINT fk_app_users_organisation
        FOREIGN KEY (organisation_id)
        REFERENCES organisations(id)
) ENGINE=InnoDB;

CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    billing_address VARCHAR(255) NOT NULL,
    gstin VARCHAR(15) NOT NULL,
    name VARCHAR(255) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    organisation_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_customers_organisation
        FOREIGN KEY (organisation_id)
        REFERENCES organisations(id)
) ENGINE=InnoDB;
