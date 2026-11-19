# PayTrack Phase 1 — Technical Design (Draft)

**Project:** PayTrack — Invoicing and Collections Platform  
**Requirements:** LV-JAVA-PAYTRACK-P1 v1.0 (12 November 2026)  
**Status:** Proposed design for mentor review; not a statement of completed implementation  
**Milestone:** M1.1

## 1. Purpose and scope

Deliver a multi-tenant invoicing application in which distributor users manage business customers, prepare and issue tax-correct invoices, generate PDFs, and correct issued invoices using linked credit notes. Phase 1 includes client-specific requirements and change request CR-1 (monthly recurring invoice templates). General ledger, GST filing, inventory and payroll are excluded.

## 2. Architecture

### 2.1 Logical components

- **Frontend:** React 18 + TypeScript (Vite), using TanStack Query, React Hook Form, Zod and Tailwind CSS.
- **API:** Java 21 / Spring Boot 3 with controller → service → repository layers; Spring Security, bean validation and OpenAPI.
- **Identity and tenancy:** Users belong to an organisation; authorization combines role permissions and server-derived organisation context. Cross-tenant resources return 404 without revealing their existence.
- **Domain services:** Customer, invoice, tax, numbering, credit note, recurring invoice and document services.
- **Data:** PostgreSQL with Spring Data JPA / Hibernate and Flyway; Redis for caching and rate limits.
- **Background jobs:** Spring Scheduler/Quartz for recurring issuance; RabbitMQ for async processing, retries and dead-letter handling.
- **Documents:** Server-side PDF generation; S3-compatible object storage; short-lived signed download URLs.
- **Delivery and operations:** Docker Compose, GitHub Actions, Render staging/production, Spring Actuator health/readiness, monitoring and logs.

**Request flow:** React client → versioned REST API → authentication/authorization and tenant context → service transaction → tenant-scoped repository → PostgreSQL. Document and asynchronous operations use storage and messaging components.

### 2.2 Current implementation versus target

The merged PayTrack PR #1 introduces Spring Security login, organisation-scoped customer CRUD, MySQL Flyway migrations, MySQL Testcontainers integration tests and GitHub Actions CI. These are **existing building blocks**, not evidence that the complete Phase 1 invoice system or staging deployment is finished.

**Open deviation:** The required standard is PostgreSQL (with PostgreSQL Testcontainers) and Java 21. Current merged code uses MySQL; obtain written mentor approval with an ADR, or plan a migration to PostgreSQL. Confirm the Java version used in the actual repository before release.

## 3. Data model

Every tenant-owned table includes `organisation_id`, indexed where relevant; all foreign keys and queries must prevent cross-organisation associations.

| Entity | Main fields / relationships | Important constraints |
|---|---|---|
| Organisation | id, name, registered_state, settings | Tenant root |
| AppUser | id, organisation_id, username/email, password_hash, role, status | Unique login; password hash only |
| Customer | id, organisation_id, name, GSTIN, billing_address, shipping_address, payment_terms_days, credit_limit, currency | GSTIN validation; tenant-scoped lookup |
| Invoice | id, organisation_id, customer_id, financial_year, series_id, invoice_number, status, issue_date, place_of_supply, subtotal, discount_total, tax_total, grand_total, currency, version | Unique (organisation_id, financial_year, series_id, invoice_number) for issued invoices |
| InvoiceLine | id, organisation_id, invoice_id, description, quantity, unit_price, discount, gst_rate, taxable_amount, cgst, sgst, igst, line_total, currency | Monetary amounts use DECIMAL/BigDecimal |
| InvoiceSeries | id, organisation_id, financial_year, prefix, next_number | Unique (organisation_id, financial_year, prefix); locked during issue |
| CreditNote | id, organisation_id, invoice_id, reason, amount, status, document_key | References immutable issued invoice |
| Payment / Allocation | id, organisation_id, invoice_id, amount, currency, status | Needed for paid/partially-paid states and reconciliation; detailed payment processing may evolve in later phases |
| RecurringInvoiceTemplate | id, organisation_id, customer_id, scheduled_day, line_template, active, next_run | CR-1; protect against duplicate issuance |
| Document | id, organisation_id, invoice_id/credit_note_id, object_key, mime_type, size | Private storage; signed access |
| AuditEvent | id, organisation_id, actor_id, entity_type, entity_id, action, timestamp, before/after metadata | Append-only audit history |
| IdempotencyRecord | id, organisation_id, key, operation, request_hash, response_reference | Unique key per operation/tenant |

**Design notes:** Prefer tenant-qualified joins and service/repository-level tenant filters; never accept an arbitrary organisation ID from the caller as authorization. Schema changes are made through Flyway. Exact fields and relationships are subject to team review.

## 4. Permission matrix (proposed)

The brief defines five roles but does **not** prescribe every individual role/action permission. The matrix below is a proposed policy for mentor approval, not a quotation from the brief.

**Legend:** Y = permitted; N = denied; Own = restricted to records assigned to the collector.

| Action | Owner | Finance Manager | Accountant | Collector | Read-only |
|---|:---:|:---:|:---:|:---:|:---:|
| Manage organisation settings and users | Y | N | N | N | N |
| View customers and invoices | Y | Y | Y | Own | Y |
| Create/update customers | Y | Y | Y | N | N |
| Create/edit draft invoices | Y | Y | Y | N | N |
| Issue invoices | Y | Y | Y | N | N |
| Create/issue credit notes | Y | Y | N | N | N |
| Record/view collections and payment allocations | Y | Y | Y | Own | N |
| Manage recurring invoice templates | Y | Y | Y | N | N |
| Download permitted invoice PDFs | Y | Y | Y | Own | Y |
| View audit history | Y | Y | N | N | N |

Implement server-side checks with Spring Security plus tenant-scoped resource authorization. For each protected endpoint, test one allowed role and one denied role. Existing ADMIN/USER roles must be mapped/refactored to the required five-role model; do not claim this matrix is already enforced.

## 5. API contract (proposed)

Base path: `/api/v1`. JSON request/response bodies, bean validation, consistent error payloads, pagination for lists, and idempotency keys for create/payment operations. Publish OpenAPI at `/docs`.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/auth/login` | Authenticate user |
| POST | `/api/v1/auth/refresh` | Rotate refresh token |
| GET/POST | `/api/v1/customers` | List/create customers |
| GET/PUT | `/api/v1/customers/{id}` | Read/update customer |
| GET/POST | `/api/v1/invoices` | List/create draft invoices |
| GET/PUT | `/api/v1/invoices/{id}` | Read/update draft |
| POST | `/api/v1/invoices/{id}/issue` | Atomically issue and number invoice |
| GET | `/api/v1/invoices/{id}/document` | Return authorized short-lived signed URL |
| POST | `/api/v1/invoices/{id}/credit-notes` | Create linked correction |
| GET/POST | `/api/v1/recurring-templates` | List/create monthly templates (CR-1) |
| GET | `/api/v1/audit-events` | Tenant-scoped audit history for authorized roles |
| GET | `/actuator/health` | Health/readiness integration |

**Example validation/error response (proposed):**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid invoice request",
  "details": [{"field": "customerId", "message": "must reference a customer in your organisation"}],
  "traceId": "example-trace-id"
}
```

Use `400` for invalid requests, `401` for unauthenticated requests, `403` for forbidden actions, `404` for nonexistent or other-tenant records, and `409` for state/concurrency conflicts. The actual API signatures and authentication mechanism must be checked against implementation before publishing OpenAPI.

## 6. GST calculation and rounding rule (proposed)

The requirements specify GST per line based on place of supply: **CGST + SGST for within-state supply**, **IGST for interstate supply**. They require a documented rounding rule but do not mandate one specific algorithm. The team proposes:

1. Use `BigDecimal` for all amounts and store currency alongside amounts. No floating-point money.
2. For each line, calculate `gross = quantity × unit_price`; subtract the line discount to obtain the taxable base (nonnegative).
3. Calculate tax from the unrounded taxable base. For an intrastate supply, split the GST rate equally into CGST and SGST; for interstate supply, apply the full rate as IGST.
4. Round **each tax component on each line** to two decimal places using `RoundingMode.HALF_UP`. Round the line taxable amount to two decimals before computing tax; define and test the resulting behavior for fractional quantities.
5. `line_total = taxable_amount + cgst + sgst + igst`; `invoice_total = sum(line_total)`; store and reconcile the component sums. Do not independently recompute rounded totals from aggregated unrounded taxes.
6. Test same-state, other-state and mixed-rate invoices against hand calculations, including half-cent edge cases. Confirm the rule with the mentor before implementation.

**Illustrative calculation:** A taxable line of ₹1,000.00 at 18% GST produces CGST ₹90.00 + SGST ₹90.00 intrastate, or IGST ₹180.00 interstate; total ₹1,180.00 in either case. This is a design example, not an actual invoice.

## 7. Invoice numbering and concurrency rule

- Maintain a configurable `InvoiceSeries` per tenant and financial year, e.g. a reviewed prefix plus sequential counter. The exact displayed format is configurable and requires team agreement.
- Drafts have internal IDs but **no final invoice number**.
- On `issue`, start a database transaction; verify invoice is still a draft and all fields are valid; lock the relevant series row (`SELECT ... FOR UPDATE` or equivalent); allocate `next_number`; persist issued invoice and increment series counter **in the same transaction**; commit.
- If the transaction rolls back, both issuance and counter increment roll back, avoiding a consumed number. A unique database constraint prevents duplicates.
- Use idempotency keys and state checks so retries do not issue or number an invoice twice. Do not delete or renumber issued invoices; correct them using credit notes.
- Test 20 concurrent issue requests for 20 unique, consecutive numbers, and test rollback and retry behavior.

## 8. Invoice lifecycle, documents and CR-1

- **Lifecycle:** `DRAFT → ISSUED → PARTIALLY_PAID → PAID`; `VOID` is a controlled terminal state according to an agreed policy. Issued invoice financial contents remain immutable.
- **Corrections:** Issue a linked credit note rather than editing an issued invoice; record actor and timestamp.
- **PDFs:** Generate server-side, store privately, and authorize downloads via short-lived signed links. Enforce file type/size checks.
- **CR-1:** A customer can have a monthly invoice template that issues automatically on a selected day. A scheduled worker checks due templates, creates/issues invoices transactionally, and uses a unique occurrence key to prevent duplicates on retries. Record failures and audit events; publish the impact estimate in release notes.
- **Client-specific requirement (FR-1.08):** Add the actual team-client rule from LogicVeda's “Your client” section, plus its schema/API impact and an automated test. **This information is not contained in the shared general requirements PDF.**

## 9. Security, operations and testing

- Spring Security enforces role and resource access; passwords are hashed, refresh tokens rotated, CORS restricted, and secrets read from environment variables.
- Tenant isolation is enforced centrally; every relevant table has `organisation_id`; cross-tenant invoice requests by ID return 404.
- Database transactions, optimistic locking and uniqueness constraints protect financial integrity.
- RabbitMQ consumers retry with backoff and send exhausted failures to a dead-letter queue with admin visibility; expose health/readiness endpoints.
- Audit all financial mutations and preserve immutable issued documents.
- JUnit 5, Mockito, MockMvc and PostgreSQL Testcontainers verify service/API behavior; target at least 70% service coverage. Include tenant-isolation, 20-way numbering concurrency, GST, reconciliation, permission and recurring-template tests.
- GitHub Actions should build/test on PRs and release tags; add secret scanning. Publish OpenAPI, ADRs, runbook and release notes. Performance goal: p95 below 300 ms for list/detail APIs at 50 virtual users (load test applies from Phase 2).

## 10. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Tax totals differ from line totals | One explicit `BigDecimal` rounding rule; hand-checked examples and reconciliation tests |
| Duplicate or skipped invoice numbers | Row lock and allocation within issuance transaction; unique constraint; 20-request concurrency test |
| Cross-tenant data leakage | Central tenant scoping, tenant-qualified queries, negative integration tests returning 404 |
| Unauthorized financial action | Reviewed five-role matrix and allowed/denied tests for each protected endpoint |
| Repeat invoice from job retry | Occurrence-level idempotency key and transactional state transition |
| Asynchronous document/job failures | Retry with backoff, dead-letter queue, monitoring and runbook |
| Technology mismatch: MySQL versus mandated PostgreSQL | Obtain written mentor approval and record ADR, or migrate and rerun PostgreSQL Testcontainers tests |
| Unspecified team-client rule | Obtain exact “Your client” requirement and update design, implementation and test |
| Incomplete staging/release evidence | Verify staging login, tenant isolation, signed PDFs, CI, OpenAPI and release checklist before claiming acceptance |

## 11. Open decisions for mentor/team

1. Confirm the exact five-role permission matrix and whether collector access is assignment-scoped.
2. Resolve MySQL versus PostgreSQL and confirm Java 21 alignment; record any approved deviations as ADRs.
3. Confirm GST rounding order for fractional quantities and discounts, and the invoice number prefix format.
4. Obtain the team's client-specific rule and document its model/flow/test.
5. Confirm void/credit-note rules, refresh-token strategy, and PDF storage provider.
6. Review proposed endpoints against the actual code and publish a matching OpenAPI contract.

## 12. Requirements traceability

| Requirement | Design section | Key verification |
|---|---|---|
| FR-1.01 / NFR-01 / NFR-02 | 2–4, 9 | Five-role access tests; cross-tenant 404 |
| FR-1.02 | 3, 5 | GSTIN and address validation |
| FR-1.03 / NFR-03 | 3, 5–6 | Server-side line totals and reconciliation |
| FR-1.04 / AC-1.3 | 6 | Three hand-checked GST cases |
| FR-1.05 / AC-1.2 | 7 | 20 concurrent invoices without gaps/duplicates |
| FR-1.06 | 8 | Issued immutability; linked credit note |
| FR-1.07 | 8 | Server PDFs and authorized signed download |
| FR-1.08 / AC-1.5 | 8, 11 | Client-specific test and demo |
| CR-1 / AC-1.4 | 8 | Monthly recurring issuance and release-note impact estimate |
| NFR-04 to NFR-11 | 5, 7, 9–10 | CI, API, audit, security, operations and docs |

---

**Source:** *PayTrack Phase 1: Invoicing Core — Project Requirements Document*, LV-JAVA-PAYTRACK-P1, version 1.0, 12 Nov 2026, sections 3–11. Proposed implementation details in this draft require review and may not match code until the team aligns them.
