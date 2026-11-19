INSERT INTO organisations (name)
VALUES ('Seeded Organisation');
INSERT INTO app_users (username, password, role, organisation_id)
SELECT
    'flywayAdmin',
    '$2a$10$.VqwUDUhjbb/IvQVP.crI.FKDzwJ9KXRf8gGX9r0MiByxMSvN4RF6',
    'ADMIN',
    id
FROM organisations
WHERE name = 'Seeded Organisation';