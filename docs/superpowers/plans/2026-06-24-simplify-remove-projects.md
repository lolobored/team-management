# Simplify App: Remove Projects, Add Customer Logos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Project concept so architects are assigned directly to customers; add customer logo upload and Google Image search.

**Architecture:** Flatten `Customer → Project → Assignment` to `Customer → Assignment`. Data migration sums per-project usage into per-customer usage. Logo storage mirrors the existing Architect photo pattern (bytea in DB). Google Custom Search JSON API proxied through a backend endpoint.

**Tech Stack:** Spring Boot 3.5, JPA/Hibernate, Liquibase, PostgreSQL, Vue 3 + Pinia + TypeScript, Vitest, Apache POI (existing), Google Custom Search JSON API.

## Global Constraints

- JDK 17 via mise (`export JAVA_HOME=$(mise where java)`)
- Gradle build: `./gradlew compileJava` / `./gradlew test`
- Frontend: `npm run build` / `npm run test` from `frontend/`
- All backend tests use H2 (`@ActiveProfiles("test")`)
- Unique constraints enforced at DB level, caught as `DataIntegrityViolationException` → 409
- Logo/photo storage: bytea column in entity, `@JsonIgnore`, served via dedicated GET endpoint

## File Map

### Delete
- `backend/src/main/java/org/lolobored/tm/project/Project.java`
- `backend/src/main/java/org/lolobored/tm/project/ProjectController.java`
- `backend/src/main/java/org/lolobored/tm/project/ProjectRepository.java`
- `backend/src/main/java/org/lolobored/tm/project/ProjectStatus.java`
- `backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java`
- `frontend/src/stores/projects.ts`
- `frontend/src/components/ProjectForm.vue`
- `frontend/src/components/ProjectSidebar.vue`
- `frontend/src/views/BacklogView.vue`
- `frontend/src/__tests__/BacklogView.spec.ts`
- `frontend/src/__tests__/CustomersProjectsView.spec.ts`

### Create
- `backend/src/main/resources/db/changelog/012-remove-projects.yaml`
- `backend/src/main/java/org/lolobored/tm/customer/LogoSearchController.java`
- `backend/src/main/java/org/lolobored/tm/customer/LogoSearchResult.java`
- `backend/src/main/java/org/lolobored/tm/customer/LogoSearchProperties.java`
- `frontend/src/components/CustomerSidebar.vue`
- `frontend/src/views/CustomersView.vue`
- `frontend/src/__tests__/CustomersView.spec.ts`

### Modify
- `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java`
- `backend/src/main/java/org/lolobored/tm/assignment/AssignmentController.java`
- `backend/src/main/java/org/lolobored/tm/assignment/AssignmentRepository.java`
- `backend/src/main/java/org/lolobored/tm/customer/Customer.java`
- `backend/src/main/java/org/lolobored/tm/customer/CustomerController.java`
- `backend/src/main/java/org/lolobored/tm/usage/AssignmentUsageDto.java`
- `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`
- `backend/src/main/java/org/lolobored/tm/usage/UsageExportService.java`
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`
- `frontend/src/types/index.ts`
- `frontend/src/api/client.ts`
- `frontend/src/router/index.ts`
- `frontend/src/components/AppLayout.vue`
- `frontend/src/components/CustomerForm.vue`
- `frontend/src/components/TimelineGrid.vue`
- `frontend/src/views/UsageTimelineView.vue`
- `frontend/src/stores/assignments.ts`

---

### Task 1: Database Migration — Remove Projects, Add Customer Logos

**Files:**
- Create: `backend/src/main/resources/db/changelog/012-remove-projects.yaml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

**Interfaces:**
- Consumes: existing `assignment`, `project`, `customer` tables
- Produces: `assignment` table with `customer_id` instead of `project_id`; `customer` table with `logo`/`logo_content_type` columns; `project` table dropped

- [ ] **Step 1: Write the migration changelog**

Create `backend/src/main/resources/db/changelog/012-remove-projects.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 012-1-add-customer-id-to-assignment
      author: claude
      changes:
        - addColumn:
            tableName: assignment
            columns:
              - column:
                  name: customer_id
                  type: bigint

  - changeSet:
      id: 012-2-populate-customer-id
      author: claude
      changes:
        - sql:
            sql: >
              UPDATE assignment a
              SET customer_id = p.customer_id
              FROM project p
              WHERE a.project_id = p.id

  - changeSet:
      id: 012-3-fill-null-usage-percent
      author: claude
      changes:
        - sql:
            sql: >
              UPDATE assignment a
              SET usage_percent = p.default_usage_percent
              FROM project p
              WHERE a.project_id = p.id
              AND a.usage_percent IS NULL

  - changeSet:
      id: 012-4-aggregate-duplicates
      author: claude
      changes:
        - sql:
            sql: >
              WITH ranked AS (
                SELECT id,
                       architect_id, customer_id, month,
                       SUM(usage_percent) OVER (PARTITION BY architect_id, customer_id, month) AS total_usage,
                       ROW_NUMBER() OVER (PARTITION BY architect_id, customer_id, month ORDER BY id) AS rn
                FROM assignment
              )
              UPDATE assignment
              SET usage_percent = ranked.total_usage,
                  tentative = false
              FROM ranked
              WHERE assignment.id = ranked.id AND ranked.rn = 1
        - sql:
            sql: >
              WITH ranked AS (
                SELECT id,
                       ROW_NUMBER() OVER (PARTITION BY architect_id, customer_id, month ORDER BY id) AS rn
                FROM assignment
              )
              DELETE FROM assignment
              WHERE id IN (SELECT id FROM ranked WHERE rn > 1)

  - changeSet:
      id: 012-5-make-customer-id-not-null
      author: claude
      changes:
        - addNotNullConstraint:
            tableName: assignment
            columnName: customer_id
            columnDataType: bigint

  - changeSet:
      id: 012-6-make-usage-percent-not-null
      author: claude
      changes:
        - addNotNullConstraint:
            tableName: assignment
            columnName: usage_percent
            columnDataType: int
        - sql:
            sql: >
              UPDATE assignment SET tentative = false WHERE tentative = true

  - changeSet:
      id: 012-7-drop-old-constraint-and-project-id
      author: claude
      changes:
        - dropUniqueConstraint:
            tableName: assignment
            constraintName: uk_assignment_architect_project_month
        - dropColumn:
            tableName: assignment
            columnName: project_id

  - changeSet:
      id: 012-8-add-new-unique-constraint
      author: claude
      changes:
        - addUniqueConstraint:
            tableName: assignment
            columnNames: architect_id, customer_id, month
            constraintName: uk_assignment_architect_customer_month

  - changeSet:
      id: 012-9-add-fk-customer-id
      author: claude
      changes:
        - addForeignKeyConstraint:
            baseTableName: assignment
            baseColumnNames: customer_id
            referencedTableName: customer
            referencedColumnNames: id
            constraintName: fk_assignment_customer

  - changeSet:
      id: 012-10-drop-project-table
      author: claude
      changes:
        - dropTable:
            tableName: project

  - changeSet:
      id: 012-11-add-logo-to-customer
      author: claude
      changes:
        - addColumn:
            tableName: customer
            columns:
              - column:
                  name: logo
                  type: bytea
              - column:
                  name: logo_content_type
                  type: varchar(100)
```

- [ ] **Step 2: Register in master changelog**

Add to `backend/src/main/resources/db/changelog/db.changelog-master.yaml` after the last entry:

```yaml
  - include:
      file: db/changelog/012-remove-projects.yaml
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/changelog/012-remove-projects.yaml backend/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat: add Liquibase migration to remove projects and add customer logos"
```

---

### Task 2: Backend — Rewire Assignment Entity and Repository

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java`
- Modify: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentRepository.java`
- Modify: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentController.java`
- Delete: all files in `backend/src/main/java/org/lolobored/tm/project/`
- Modify: `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`
- Delete: `backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java`

**Interfaces:**
- Consumes: `Customer` entity (existing)
- Produces: `Assignment` entity with `customerId` field; `AssignmentRepository.findByCustomerId()`; updated controller using `customerId`

- [ ] **Step 1: Update Assignment entity**

Replace the full content of `Assignment.java`:

```java
package org.lolobored.tm.assignment;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_assignment_architect_customer_month",
        columnNames = {"architect_id", "customer_id", "month"}))
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "architect_id", nullable = false)
    private Long architectId;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotNull
    @Column(name = "usage_percent", nullable = false)
    private int usagePercent;

    @Column(nullable = false)
    private boolean tentative;

    @NotNull
    @Column(nullable = false, length = 7)
    private String month;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArchitectId() { return architectId; }
    public void setArchitectId(Long architectId) { this.architectId = architectId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public int getUsagePercent() { return usagePercent; }
    public void setUsagePercent(int usagePercent) { this.usagePercent = usagePercent; }
    public boolean isTentative() { return tentative; }
    public void setTentative(boolean tentative) { this.tentative = tentative; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
}
```

- [ ] **Step 2: Update AssignmentRepository**

Replace `AssignmentRepository.java`:

```java
package org.lolobored.tm.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByArchitectId(Long architectId);
    List<Assignment> findByCustomerId(Long customerId);
}
```

- [ ] **Step 3: Update AssignmentController**

Replace `AssignmentController.java`:

```java
package org.lolobored.tm.assignment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {
    private final AssignmentRepository repository;

    public AssignmentController(AssignmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Assignment> list(
            @RequestParam(required = false) Long architectId,
            @RequestParam(required = false) Long customerId) {
        if (architectId != null) return repository.findByArchitectId(architectId);
        else if (customerId != null) return repository.findByCustomerId(customerId);
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Assignment> create(@Valid @RequestBody Assignment assignment) {
        assignment.setId(null);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(assignment));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assignment already exists for this architect/customer/month");
        }
    }

    @GetMapping("/{id}")
    public Assignment get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Assignment update(@PathVariable Long id, @Valid @RequestBody Assignment assignment) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        assignment.setId(id);
        try {
            return repository.save(assignment);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assignment already exists for this architect/customer/month");
        }
    }

    @PatchMapping("/{id}")
    public Assignment patch(@PathVariable Long id, @RequestBody java.util.Map<String, Object> updates) {
        Assignment existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (updates.containsKey("usagePercent")) {
            existing.setUsagePercent(((Number) updates.get("usagePercent")).intValue());
        }
        if (updates.containsKey("tentative")) {
            existing.setTentative((Boolean) updates.get("tentative"));
        }
        try {
            return repository.save(existing);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment conflict");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repository.deleteById(id);
    }
}
```

- [ ] **Step 4: Delete project package and test**

```bash
rm -rf backend/src/main/java/org/lolobored/tm/project/
rm backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java
```

- [ ] **Step 5: Rewrite AssignmentControllerTest**

Replace `AssignmentControllerTest.java`:

```java
package org.lolobored.tm.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AssignmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ArchitectRepository architectRepository;
    @Autowired private CustomerRepository customerRepository;

    private Long architectId;
    private Long customerId;

    @BeforeEach
    void setUp() {
        Architect architect = new Architect();
        architect.setFirstName("Alice");
        architect.setLastName("Smith");
        architect.setCountry("Australia");
        architectId = architectRepository.save(architect).getId();

        Customer customer = new Customer();
        customer.setName("Acme");
        customerId = customerRepository.save(customer).getId();
    }

    @Test
    void createAndGetAssignment() throws Exception {
        String json = """
                {"architectId": %d, "customerId": %d, "usagePercent": 25, "tentative": false, "month": "2026-06"}
                """.formatted(architectId, customerId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.architectId").value(architectId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.usagePercent").value(25))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/assignments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usagePercent").value(25));
    }

    @Test
    void listAssignments_filterByArchitect() throws Exception {
        String json = """
                {"architectId": %d, "customerId": %d, "usagePercent": 20, "tentative": false, "month": "2026-06"}
                """.formatted(architectId, customerId);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json));

        mockMvc.perform(get("/api/assignments?architectId=" + architectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/assignments?architectId=9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteAssignment() throws Exception {
        String json = """
                {"architectId": %d, "customerId": %d, "usagePercent": 20, "tentative": false, "month": "2026-06"}
                """.formatted(architectId, customerId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(delete("/api/assignments/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/assignments/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void patchAssignment_updatesUsageOnly() throws Exception {
        String json = """
                {"architectId": %d, "customerId": %d, "usagePercent": 25, "tentative": false, "month": "2026-06"}
                """.formatted(architectId, customerId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/assignments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usagePercent\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usagePercent").value(5))
                .andExpect(jsonPath("$.architectId").value(architectId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.month").value("2026-06"));
    }

    @Test
    void createAssignment_duplicateMonthReturns409() throws Exception {
        String json = """
                {"architectId": %d, "customerId": %d, "usagePercent": 20, "tentative": false, "month": "2026-06"}
                """.formatted(architectId, customerId);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 6: Verify backend compiles and tests pass**

```bash
export JAVA_HOME=$(mise where java) && export PATH="$JAVA_HOME/bin:$PATH"
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A backend/src/
git commit -m "feat: rewire Assignment to use customerId, remove Project entity"
```

---

### Task 3: Backend — Customer Logo Endpoints

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/customer/Customer.java`
- Modify: `backend/src/main/java/org/lolobored/tm/customer/CustomerController.java`

**Interfaces:**
- Consumes: `Customer` entity, `CustomerRepository`
- Produces: `POST /api/customers/{id}/logo`, `GET /api/customers/{id}/logo`, `DELETE /api/customers/{id}/logo`

- [ ] **Step 1: Add logo fields to Customer entity**

Add these fields and methods to `Customer.java` (after existing fields, before getters):

```java
import com.fasterxml.jackson.annotation.JsonIgnore;
```

Add fields:

```java
    @JsonIgnore
    @Column(columnDefinition = "bytea")
    private byte[] logo;

    @JsonIgnore
    private String logoContentType;
```

Add getters/setters:

```java
    public byte[] getLogo() { return logo; }
    public void setLogo(byte[] logo) { this.logo = logo; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String logoContentType) { this.logoContentType = logoContentType; }
    public boolean hasLogo() { return logo != null && logo.length > 0; }
```

- [ ] **Step 2: Add logo endpoints to CustomerController**

Add these imports to `CustomerController.java`:

```java
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
```

Preserve logo on update — modify `update()` method:

```java
    @PutMapping("/{id}")
    public Customer update(@PathVariable Long id, @Valid @RequestBody Customer customer) {
        Customer existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        customer.setId(id);
        customer.setLogo(existing.getLogo());
        customer.setLogoContentType(existing.getLogoContentType());
        return repository.save(customer);
    }
```

Add these endpoints:

```java
    @PostMapping("/{id}/logo")
    public ResponseEntity<Void> uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Customer customer = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be an image");
        }
        try {
            customer.setLogo(file.getBytes());
            customer.setLogoContentType(contentType);
            repository.save(customer);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> getLogo(@PathVariable Long id) {
        Customer customer = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!customer.hasLogo()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(customer.getLogoContentType()))
                .body(customer.getLogo());
    }

    @DeleteMapping("/{id}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLogo(@PathVariable Long id) {
        Customer customer = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        customer.setLogo(null);
        customer.setLogoContentType(null);
        repository.save(customer);
    }
```

- [ ] **Step 3: Verify compilation**

```bash
export JAVA_HOME=$(mise where java) && export PATH="$JAVA_HOME/bin:$PATH"
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/customer/Customer.java backend/src/main/java/org/lolobored/tm/customer/CustomerController.java
git commit -m "feat: add customer logo upload/serve/delete endpoints"
```

---

### Task 4: Backend — Logo Search Proxy and Usage Service Updates

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/customer/LogoSearchController.java`
- Create: `backend/src/main/java/org/lolobored/tm/customer/LogoSearchResult.java`
- Create: `backend/src/main/java/org/lolobored/tm/customer/LogoSearchProperties.java`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/AssignmentUsageDto.java`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/UsageExportService.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: Google Custom Search JSON API, `CustomerRepository`, `AssignmentRepository`
- Produces: `GET /api/logo-search?q=...`, `POST /api/customers/{id}/logo-from-url`; updated `AssignmentUsageDto` with `customerId` instead of `projectId`/`projectName`

- [ ] **Step 1: Create LogoSearchResult record**

Create `backend/src/main/java/org/lolobored/tm/customer/LogoSearchResult.java`:

```java
package org.lolobored.tm.customer;

public record LogoSearchResult(String url, String thumbnailUrl) {}
```

- [ ] **Step 2: Create LogoSearchProperties**

Create `backend/src/main/java/org/lolobored/tm/customer/LogoSearchProperties.java`:

```java
package org.lolobored.tm.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "logo-search")
public class LogoSearchProperties {
    private String googleApiKey = "";
    private String googleCseId = "";

    public String getGoogleApiKey() { return googleApiKey; }
    public void setGoogleApiKey(String googleApiKey) { this.googleApiKey = googleApiKey; }
    public String getGoogleCseId() { return googleCseId; }
    public void setGoogleCseId(String googleCseId) { this.googleCseId = googleCseId; }
    public boolean isConfigured() { return !googleApiKey.isBlank() && !googleCseId.isBlank(); }
}
```

- [ ] **Step 3: Create LogoSearchController**

Create `backend/src/main/java/org/lolobored/tm/customer/LogoSearchController.java`:

```java
package org.lolobored.tm.customer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class LogoSearchController {

    private final LogoSearchProperties properties;
    private final CustomerRepository customerRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public LogoSearchController(LogoSearchProperties properties,
                                 CustomerRepository customerRepository) {
        this.properties = properties;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/api/logo-search")
    public List<LogoSearchResult> search(@RequestParam String q) {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Logo search not configured. Set GOOGLE_API_KEY and GOOGLE_CSE_ID.");
        }

        String url = UriComponentsBuilder
                .fromUriString("https://www.googleapis.com/customsearch/v1")
                .queryParam("key", properties.getGoogleApiKey())
                .queryParam("cx", properties.getGoogleCseId())
                .queryParam("q", q + " logo")
                .queryParam("searchType", "image")
                .queryParam("num", 10)
                .toUriString();

        JsonNode response = restTemplate.getForObject(url, JsonNode.class);
        List<LogoSearchResult> results = new ArrayList<>();
        if (response != null && response.has("items")) {
            for (JsonNode item : response.get("items")) {
                String imageUrl = item.get("link").asText();
                String thumbnail = item.has("image") && item.get("image").has("thumbnailLink")
                        ? item.get("image").get("thumbnailLink").asText()
                        : imageUrl;
                results.add(new LogoSearchResult(imageUrl, thumbnail));
            }
        }
        return results;
    }

    @PostMapping("/api/customers/{id}/logo-from-url")
    public void setLogoFromUrl(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String imageUrl = body.get("url");
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            var response = restTemplate.getForEntity(imageUrl, byte[].class);
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString() : "";

            if (!contentType.startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "URL does not point to an image");
            }

            byte[] imageBytes = response.getBody();
            if (imageBytes == null || imageBytes.length > 2 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image too large (max 2MB)");
            }

            customer.setLogo(imageBytes);
            customer.setLogoContentType(contentType);
            customerRepository.save(customer);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to download image from URL");
        }
    }
}
```

- [ ] **Step 4: Add config to application.yml**

Append to `backend/src/main/resources/application.yml`:

```yaml

logo-search:
  google-api-key: ${GOOGLE_API_KEY:}
  google-cse-id: ${GOOGLE_CSE_ID:}
```

- [ ] **Step 5: Update AssignmentUsageDto**

Replace `backend/src/main/java/org/lolobored/tm/usage/AssignmentUsageDto.java`:

```java
package org.lolobored.tm.usage;

public record AssignmentUsageDto(
        Long assignmentId, Long customerId, String customerName,
        int usage, boolean tentative
) {}
```

- [ ] **Step 6: Update UsageService**

Replace `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`:

```java
package org.lolobored.tm.usage;

import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.assignment.Assignment;
import org.lolobored.tm.assignment.AssignmentRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;

@Service
public class UsageService {

    private final ArchitectRepository architectRepository;
    private final AssignmentRepository assignmentRepository;
    private final CustomerRepository customerRepository;

    public UsageService(ArchitectRepository architectRepository,
                        AssignmentRepository assignmentRepository,
                        CustomerRepository customerRepository) {
        this.architectRepository = architectRepository;
        this.assignmentRepository = assignmentRepository;
        this.customerRepository = customerRepository;
    }

    public List<ArchitectUsageDto> computeUsage(YearMonth from, YearMonth to,
                                                 String country, Long architectId) {
        List<Architect> architects;
        if (architectId != null) {
            architects = architectRepository.findById(architectId)
                    .map(List::of).orElse(List.of());
        } else {
            architects = architectRepository.findAll();
        }

        if (country != null && !country.isBlank()) {
            architects = architects.stream()
                    .filter(a -> country.equals(a.getCountry())).toList();
        }

        Map<Long, Customer> customerCache = new HashMap<>();
        customerRepository.findAll().forEach(c -> customerCache.put(c.getId(), c));

        List<ArchitectUsageDto> result = new ArrayList<>();

        for (Architect architect : architects) {
            List<Assignment> assignments = assignmentRepository.findByArchitectId(architect.getId());
            Map<YearMonth, MonthUsageDto> months = new LinkedHashMap<>();

            for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
                String monthStr = month.toString();
                List<AssignmentUsageDto> monthAssignments = new ArrayList<>();

                for (Assignment assignment : assignments) {
                    if (!monthStr.equals(assignment.getMonth())) continue;

                    Customer customer = customerCache.get(assignment.getCustomerId());
                    String customerName = customer != null ? customer.getName() : "";

                    monthAssignments.add(new AssignmentUsageDto(
                            assignment.getId(), assignment.getCustomerId(), customerName,
                            assignment.getUsagePercent(), assignment.isTentative()));
                }

                monthAssignments.sort(Comparator.comparing(AssignmentUsageDto::customerName));
                int total = monthAssignments.stream().mapToInt(AssignmentUsageDto::usage).sum();
                months.put(month, new MonthUsageDto(total, monthAssignments));
            }

            String architectCountry = architect.getCountry() != null ? architect.getCountry() : "";
            result.add(new ArchitectUsageDto(
                    architect.getId(), architect.getFirstName() + " " + architect.getLastName(),
                    architectCountry, months));
        }

        return result;
    }
}
```

- [ ] **Step 7: Update UsageExportService**

In `UsageExportService.java`, find the `exportToExcel` method's assignment-to-string mapping (the `.map(a -> { ... })` lambda). Replace it:

```java
                    String content = monthData.assignments().stream()
                            .map(a -> {
                                String prefix = a.tentative() ? "(T) " : "";
                                return prefix + a.customerName() + " " + a.usage() + "%";
                            })
                            .collect(Collectors.joining("\n"));
```

- [ ] **Step 8: Verify backend compiles and tests pass**

```bash
export JAVA_HOME=$(mise where java) && export PATH="$JAVA_HOME/bin:$PATH"
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add -A backend/src/
git commit -m "feat: add logo search proxy, update usage service for customer-based assignments"
```

---

### Task 5: Frontend — Types, API Client, Stores, Router

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/stores/assignments.ts`
- Delete: `frontend/src/stores/projects.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Consumes: backend API endpoints
- Produces: updated TypeScript types, API client methods, stores for the rest of frontend tasks

- [ ] **Step 1: Update types**

Replace `frontend/src/types/index.ts`:

```typescript
export interface Architect {
  id: number
  firstName: string
  lastName: string
  email?: string
  country?: string
  city?: string
}

export interface Customer {
  id: number
  name: string
  country?: string
  city?: string
}

export interface Assignment {
  id: number
  architectId: number
  customerId: number
  usagePercent: number
  tentative: boolean
  month: string
}

export interface AssignmentUsage {
  assignmentId: number
  customerId: number
  customerName: string
  usage: number
  tentative: boolean
}

export interface MonthUsage {
  total: number
  assignments: AssignmentUsage[]
}

export interface ArchitectUsage {
  architectId: number
  architectName: string
  country: string
  months: Record<string, MonthUsage>
}

export interface LogoSearchResult {
  url: string
  thumbnailUrl: string
}
```

- [ ] **Step 2: Update API client**

Replace `frontend/src/api/client.ts`:

```typescript
import axios from 'axios'
import type { Architect, Customer, Assignment, ArchitectUsage, LogoSearchResult } from '@/types'

const api = axios.create({ baseURL: '/api' })

export const architectApi = {
  list: () => api.get<Architect[]>('/architects').then(r => r.data),
  get: (id: number) => api.get<Architect>(`/architects/${id}`).then(r => r.data),
  create: (data: Omit<Architect, 'id'>) => api.post<Architect>('/architects', data).then(r => r.data),
  update: (id: number, data: Omit<Architect, 'id'>) => api.put<Architect>(`/architects/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/architects/${id}`),
  uploadPhoto: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post(`/architects/${id}/photo`, form)
  },
  deletePhoto: (id: number) => api.delete(`/architects/${id}/photo`),
  photoUrl: (id: number) => `/api/architects/${id}/photo`,
}

export const customerApi = {
  list: () => api.get<Customer[]>('/customers').then(r => r.data),
  get: (id: number) => api.get<Customer>(`/customers/${id}`).then(r => r.data),
  create: (data: Omit<Customer, 'id'>) => api.post<Customer>('/customers', data).then(r => r.data),
  update: (id: number, data: Omit<Customer, 'id'>) => api.put<Customer>(`/customers/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/customers/${id}`),
  uploadLogo: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post(`/customers/${id}/logo`, form)
  },
  deleteLogo: (id: number) => api.delete(`/customers/${id}/logo`),
  logoUrl: (id: number) => `/api/customers/${id}/logo`,
  setLogoFromUrl: (id: number, url: string) => api.post(`/customers/${id}/logo-from-url`, { url }),
}

export const assignmentApi = {
  list: (params?: { architectId?: number; customerId?: number }) =>
    api.get<Assignment[]>('/assignments', { params }).then(r => r.data),
  create: (data: Omit<Assignment, 'id'>) => api.post<Assignment>('/assignments', data).then(r => r.data),
  update: (id: number, data: Partial<Assignment>) => api.patch<Assignment>(`/assignments/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/assignments/${id}`),
}

export const usageApi = {
  get: (from: string, to: string, country?: string, architectId?: number) =>
    api.get<ArchitectUsage[]>('/usage', { params: { from, to, country, architectId } }).then(r => r.data),
  exportExcel: (from: string, to: string, country?: string) =>
    api.get('/usage/export', { params: { from, to, country }, responseType: 'blob' }).then(r => {
      const url = window.URL.createObjectURL(r.data)
      const a = document.createElement('a')
      a.href = url
      a.download = r.headers['content-disposition']?.match(/filename="(.+)"/)?.[1]
          ?? `usage-${from}-to-${to}.xlsx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    }),
}

export const logoSearchApi = {
  search: (q: string) => api.get<LogoSearchResult[]>('/logo-search', { params: { q } }).then(r => r.data),
}
```

- [ ] **Step 3: Delete projects store**

```bash
rm frontend/src/stores/projects.ts
```

- [ ] **Step 4: Update assignments store**

Replace `frontend/src/stores/assignments.ts`:

```typescript
import { defineStore } from 'pinia'
import { assignmentApi } from '@/api/client'
import type { Assignment } from '@/types'

export const useAssignmentsStore = defineStore('assignments', () => {
  async function create(data: Omit<Assignment, 'id'>) {
    return await assignmentApi.create(data)
  }

  async function remove(id: number) {
    await assignmentApi.delete(id)
  }

  return { create, remove }
})
```

- [ ] **Step 5: Update router**

Replace `frontend/src/router/index.ts`:

```typescript
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/timeline' },
    { path: '/architects', name: 'architects', component: () => import('@/views/ArchitectsView.vue') },
    { path: '/customers', name: 'customers', component: () => import('@/views/CustomersView.vue') },
    { path: '/timeline', name: 'timeline', component: () => import('@/views/UsageTimelineView.vue') },
  ],
})

export default router
```

- [ ] **Step 6: Commit**

```bash
git add -A frontend/src/types/ frontend/src/api/ frontend/src/stores/ frontend/src/router/
git commit -m "feat: update frontend types, API client, stores for customer-based model"
```

---

### Task 6: Frontend — CustomersView, CustomerForm with Logo, AppLayout

**Files:**
- Create: `frontend/src/views/CustomersView.vue`
- Delete: `frontend/src/views/CustomersProjectsView.vue`
- Delete: `frontend/src/views/BacklogView.vue`
- Delete: `frontend/src/components/ProjectForm.vue`
- Delete: `frontend/src/__tests__/BacklogView.spec.ts`
- Delete: `frontend/src/__tests__/CustomersProjectsView.spec.ts`
- Modify: `frontend/src/components/CustomerForm.vue`
- Modify: `frontend/src/components/AppLayout.vue`

**Interfaces:**
- Consumes: `customerApi`, `logoSearchApi`, `useCustomersStore`, `useGeoStore`
- Produces: `CustomersView.vue` (flat list), `CustomerForm.vue` (with logo upload/search)

- [ ] **Step 1: Delete old views and components**

```bash
rm frontend/src/views/CustomersProjectsView.vue
rm frontend/src/views/BacklogView.vue
rm frontend/src/components/ProjectForm.vue
rm frontend/src/__tests__/BacklogView.spec.ts
rm frontend/src/__tests__/CustomersProjectsView.spec.ts
```

- [ ] **Step 2: Create CustomersView.vue**

Create `frontend/src/views/CustomersView.vue`:

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useCustomersStore } from '@/stores/customers'
import { customerApi } from '@/api/client'
import CustomerForm from '@/components/CustomerForm.vue'
import type { Customer } from '@/types'

const customerStore = useCustomersStore()

const filterText = ref('')
const sortBy = ref<'name' | 'country'>('name')
const showForm = ref(false)
const editingCustomer = ref<Customer | undefined>()

const sortedCustomers = computed(() => {
  const search = filterText.value.toLowerCase()
  let list = [...customerStore.customers]
  if (search) {
    list = list.filter(c =>
      c.name.toLowerCase().includes(search) ||
      c.country?.toLowerCase().includes(search) ||
      c.city?.toLowerCase().includes(search)
    )
  }
  return list.sort((a, b) => {
    if (sortBy.value === 'country') {
      return (a.country ?? '').localeCompare(b.country ?? '') || a.name.localeCompare(b.name)
    }
    return a.name.localeCompare(b.name)
  })
})

onMounted(() => customerStore.fetchAll())

function openAdd() { editingCustomer.value = undefined; showForm.value = true }
function openEdit(c: Customer) { editingCustomer.value = c; showForm.value = true }

async function onSubmit(data: Omit<Customer, 'id'>) {
  if (editingCustomer.value) await customerStore.update(editingCustomer.value.id, data)
  else await customerStore.create(data)
  showForm.value = false
}

async function onDelete(id: number) {
  if (confirm('Delete this customer?')) await customerStore.remove(id)
}
</script>

<template>
  <div>
    <div class="header">
      <h1>Customers</h1>
      <div class="header-controls">
        <input v-model="filterText" placeholder="Filter by name, country, city..." class="filter-input" />
        <label class="sort-label">Sort by:
          <select v-model="sortBy">
            <option value="name">Name</option>
            <option value="country">Country</option>
          </select>
        </label>
        <button class="primary" @click="openAdd">+ Add Customer</button>
      </div>
    </div>

    <div v-if="showForm" class="form-overlay">
      <div class="form-panel">
        <h2>{{ editingCustomer ? 'Edit' : 'Add' }} Customer</h2>
        <CustomerForm :customer="editingCustomer" @submit="onSubmit" @cancel="showForm = false" />
      </div>
    </div>

    <div class="customer-grid">
      <div v-for="customer in sortedCustomers" :key="customer.id" class="customer-card">
        <img :src="customerApi.logoUrl(customer.id)" class="customer-logo"
             @error="($event.target as HTMLImageElement).style.display = 'none'" />
        <div class="customer-info">
          <strong>{{ customer.name }}</strong>
          <span v-if="customer.country || customer.city" class="location">
            {{ [customer.city, customer.country].filter(Boolean).join(', ') }}
          </span>
        </div>
        <div class="customer-actions">
          <button @click="openEdit(customer)">Edit</button>
          <button class="danger" @click="onDelete(customer.id)">Delete</button>
        </div>
      </div>
    </div>

    <div v-if="sortedCustomers.length === 0 && filterText" class="empty">No matching customers.</div>
    <div v-else-if="customerStore.customers.length === 0" class="empty">No customers yet. Add one to get started.</div>
  </div>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.header-controls { display: flex; align-items: center; gap: 0.75rem; }
.filter-input { padding: 0.4rem 0.6rem; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 0.85rem; width: 250px; }
.filter-input:focus { outline: none; border-color: #3b82f6; }
.sort-label { display: flex; align-items: center; gap: 0.25rem; font-size: 0.85rem; color: #64748b; }
.customer-grid { display: flex; flex-direction: column; gap: 0.5rem; }
.customer-card { display: flex; align-items: center; gap: 1rem; padding: 0.75rem 1rem; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; }
.customer-logo { width: 40px; height: 40px; border-radius: 6px; object-fit: contain; flex-shrink: 0; background: #f1f5f9; }
.customer-info { flex: 1; }
.location { color: #64748b; font-size: 0.85rem; margin-left: 0.5rem; }
.customer-actions { display: flex; gap: 0.25rem; }
.form-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; z-index: 100; }
.form-panel { background: #fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); min-width: 450px; }
.form-panel h2 { margin-bottom: 1rem; }
.empty { text-align: center; color: #94a3b8; padding: 3rem; }
</style>
```

- [ ] **Step 3: Update CustomerForm with logo section**

Replace `frontend/src/components/CustomerForm.vue`:

```vue
<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useGeoStore } from '@/stores/geo'
import { customerApi, logoSearchApi } from '@/api/client'
import AutocompleteInput from '@/components/AutocompleteInput.vue'
import type { Customer, LogoSearchResult } from '@/types'

const props = defineProps<{ customer?: Customer }>()
const emit = defineEmits<{ submit: [data: Omit<Customer, 'id'>]; cancel: [] }>()

const geo = useGeoStore()

const form = reactive({
  name: props.customer?.name ?? '',
  country: props.customer?.country ?? '',
  city: props.customer?.city ?? '',
})

const logoPreview = ref<string | null>(null)
const showLogoSearch = ref(false)
const logoQuery = ref('')
const logoResults = ref<LogoSearchResult[]>([])
const logoSearching = ref(false)
const dragOver = ref(false)

const availableCities = computed(() =>
  form.country ? geo.getCities(form.country) : []
)

watch(() => form.country, (newCountry) => {
  if (newCountry && geo.countries.includes(newCountry)) {
    geo.fetchCities(newCountry)
  }
  if (!newCountry) form.city = ''
})

onMounted(() => {
  geo.fetchCountries()
  if (form.country) geo.fetchCities(form.country)
  if (props.customer) {
    logoPreview.value = customerApi.logoUrl(props.customer.id)
  }
})

function onFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files?.[0]) uploadFile(input.files[0])
}

function onDrop(event: DragEvent) {
  dragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) uploadFile(file)
}

async function uploadFile(file: File) {
  if (!props.customer) return
  await customerApi.uploadLogo(props.customer.id, file)
  logoPreview.value = customerApi.logoUrl(props.customer.id) + '?t=' + Date.now()
}

async function searchLogos() {
  if (!logoQuery.value.trim()) return
  logoSearching.value = true
  try {
    logoResults.value = await logoSearchApi.search(logoQuery.value)
  } finally {
    logoSearching.value = false
  }
}

async function selectLogo(result: LogoSearchResult) {
  if (!props.customer) return
  await customerApi.setLogoFromUrl(props.customer.id, result.url)
  logoPreview.value = customerApi.logoUrl(props.customer.id) + '?t=' + Date.now()
  showLogoSearch.value = false
  logoResults.value = []
}

async function removeLogo() {
  if (!props.customer) return
  await customerApi.deleteLogo(props.customer.id)
  logoPreview.value = null
}

function onSubmit() {
  emit('submit', {
    name: form.name,
    country: form.country || undefined,
    city: form.city || undefined,
  })
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="form">
    <div v-if="customer" class="logo-section">
      <div class="logo-row">
        <img v-if="logoPreview" :src="logoPreview" class="logo-preview"
             @error="logoPreview = null" />
        <div v-else class="logo-placeholder">No logo</div>
        <div class="logo-actions">
          <label class="btn-small">
            Browse
            <input type="file" accept="image/*" @change="onFileSelect" hidden />
          </label>
          <button type="button" class="btn-small" @click="showLogoSearch = !showLogoSearch">Search</button>
          <button v-if="logoPreview" type="button" class="btn-small danger" @click="removeLogo">Remove</button>
        </div>
      </div>
      <div class="drop-zone" :class="{ active: dragOver }"
           @dragover.prevent="dragOver = true" @dragleave="dragOver = false" @drop.prevent="onDrop">
        Drop an image here
      </div>
      <div v-if="showLogoSearch" class="logo-search-panel">
        <div class="search-row">
          <input v-model="logoQuery" placeholder="Search logos..." @keyup.enter="searchLogos" />
          <button type="button" @click="searchLogos" :disabled="logoSearching">
            {{ logoSearching ? 'Searching...' : 'Go' }}
          </button>
        </div>
        <div class="logo-grid">
          <img v-for="(result, i) in logoResults" :key="i" :src="result.thumbnailUrl"
               class="logo-result" @click="selectLogo(result)" title="Click to use this logo" />
        </div>
        <div v-if="logoResults.length === 0 && !logoSearching" class="empty-search">
          Type a company name and click Go
        </div>
      </div>
    </div>
    <div v-else class="logo-note">Save the customer first, then edit to add a logo.</div>

    <div class="form-row"><label>Name</label><input v-model="form.name" required /></div>
    <div class="form-row">
      <label>Country</label>
      <AutocompleteInput v-model="form.country" :suggestions="geo.countries" placeholder="Type a country..." :loading="geo.loadingCountries" />
    </div>
    <div class="form-row">
      <label>City</label>
      <AutocompleteInput v-model="form.city" :suggestions="availableCities" placeholder="Type a city..." :disabled="!form.country" :loading="geo.loadingCities" />
    </div>
    <div class="form-actions">
      <button type="submit" class="primary">Save</button>
      <button type="button" @click="emit('cancel')">Cancel</button>
    </div>
  </form>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 0.75rem; max-width: 450px; }
.form-row { display: flex; flex-direction: column; gap: 0.25rem; }
.form-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
.logo-section { border: 1px solid #e2e8f0; border-radius: 6px; padding: 0.75rem; background: #f8fafc; }
.logo-row { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem; }
.logo-preview { width: 64px; height: 64px; border-radius: 6px; object-fit: contain; background: #fff; border: 1px solid #e2e8f0; }
.logo-placeholder { width: 64px; height: 64px; border-radius: 6px; background: #e2e8f0; display: flex; align-items: center; justify-content: center; font-size: 0.7rem; color: #94a3b8; }
.logo-actions { display: flex; gap: 0.25rem; flex-wrap: wrap; }
.btn-small { padding: 0.25rem 0.5rem; font-size: 0.8rem; border: 1px solid #cbd5e1; border-radius: 4px; background: #fff; cursor: pointer; }
.btn-small:hover { background: #f1f5f9; }
.btn-small.danger { color: #dc2626; border-color: #fca5a5; }
.btn-small.danger:hover { background: #fef2f2; }
.drop-zone { border: 2px dashed #cbd5e1; border-radius: 6px; padding: 0.5rem; text-align: center; font-size: 0.8rem; color: #94a3b8; transition: all 0.2s; }
.drop-zone.active { border-color: #3b82f6; background: #eff6ff; color: #3b82f6; }
.logo-search-panel { margin-top: 0.5rem; border-top: 1px solid #e2e8f0; padding-top: 0.5rem; }
.search-row { display: flex; gap: 0.25rem; margin-bottom: 0.5rem; }
.search-row input { flex: 1; padding: 0.3rem 0.5rem; font-size: 0.85rem; border: 1px solid #cbd5e1; border-radius: 4px; }
.logo-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 0.5rem; }
.logo-result { width: 100%; aspect-ratio: 1; object-fit: contain; border: 1px solid #e2e8f0; border-radius: 4px; cursor: pointer; background: #fff; padding: 4px; }
.logo-result:hover { border-color: #3b82f6; box-shadow: 0 0 0 2px #bfdbfe; }
.empty-search { text-align: center; font-size: 0.8rem; color: #94a3b8; padding: 1rem; }
.logo-note { font-size: 0.8rem; color: #94a3b8; font-style: italic; }
</style>
```

- [ ] **Step 4: Update AppLayout.vue**

In `frontend/src/components/AppLayout.vue`, replace the nav `<ul>` section (lines 22-34):

```vue
      <ul>
        <li><RouterLink to="/timeline" :title="collapsed ? 'Usage Timeline' : undefined">
          <span class="nav-icon">📊</span><span v-show="!collapsed" class="nav-label">Usage Timeline</span>
        </RouterLink></li>
        <li><RouterLink to="/architects" :title="collapsed ? 'Architects' : undefined">
          <span class="nav-icon">👥</span><span v-show="!collapsed" class="nav-label">Architects</span>
        </RouterLink></li>
        <li><RouterLink to="/customers" :title="collapsed ? 'Customers' : undefined">
          <span class="nav-icon">🏢</span><span v-show="!collapsed" class="nav-label">Customers</span>
        </RouterLink></li>
      </ul>
```

- [ ] **Step 5: Commit**

```bash
git add -A frontend/src/views/ frontend/src/components/ frontend/src/__tests__/
git commit -m "feat: replace CustomersProjectsView with flat CustomersView, add logo to CustomerForm, remove BacklogView"
```

---

### Task 7: Frontend — CustomerSidebar, TimelineGrid, UsageTimelineView

**Files:**
- Create: `frontend/src/components/CustomerSidebar.vue`
- Delete: `frontend/src/components/ProjectSidebar.vue`
- Modify: `frontend/src/components/TimelineGrid.vue`
- Modify: `frontend/src/views/UsageTimelineView.vue`

**Interfaces:**
- Consumes: `customerApi`, `useCustomersStore`, `useAssignmentsStore`, `usageApi`
- Produces: working timeline with customer-based drag-and-drop, customer sidebar

- [ ] **Step 1: Delete ProjectSidebar**

```bash
rm frontend/src/components/ProjectSidebar.vue
```

- [ ] **Step 2: Create CustomerSidebar.vue**

Create `frontend/src/components/CustomerSidebar.vue`:

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import { customerApi } from '@/api/client'
import type { Customer } from '@/types'

const props = defineProps<{ customers: Customer[] }>()

const collapsed = ref(false)
const filter = ref('')

const filteredCustomers = computed(() => {
  let list = [...props.customers]
  if (filter.value) {
    const search = filter.value.toLowerCase()
    list = list.filter(c =>
      c.name.toLowerCase().includes(search) ||
      c.country?.toLowerCase().includes(search)
    )
  }
  return list.sort((a, b) => a.name.localeCompare(b.name))
})

function onDragStart(event: DragEvent, customer: Customer) {
  event.dataTransfer!.setData('application/json', JSON.stringify({
    type: 'customer',
    customerId: customer.id,
    tentative: false,
    defaultUsagePercent: 20,
  }))
  event.dataTransfer!.effectAllowed = 'copy'
}
</script>

<template>
  <div class="sidebar-panel" :class="{ collapsed }">
    <button class="toggle-btn" @click="collapsed = !collapsed" :title="collapsed ? 'Show customers' : 'Hide customers'">
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <line x1="4" y1="5" x2="16" y2="5" />
        <line x1="8" y1="10" x2="16" y2="10" />
        <line x1="4" y1="15" x2="16" y2="15" />
      </svg>
    </button>
    <template v-if="!collapsed">
      <h3>Customers</h3>
      <input v-model="filter" placeholder="Filter customers..." class="customer-filter" />
      <div>
        <div v-for="customer in filteredCustomers" :key="customer.id"
          class="customer-item"
          draggable="true" @dragstart="onDragStart($event, customer)">
          <img :src="customerApi.logoUrl(customer.id)" class="customer-logo"
               @error="($event.target as HTMLImageElement).style.display = 'none'" />
          <div class="customer-name">{{ customer.name }}</div>
        </div>
      </div>
      <div v-if="filteredCustomers.length === 0" class="empty">{{ filter ? 'No matching customers' : 'No customers' }}</div>
    </template>
  </div>
</template>

<style scoped>
.sidebar-panel { width: 200px; border-left: 1px solid #e2e8f0; padding: 1rem; background: #fff; overflow-y: auto; flex-shrink: 0; transition: width 0.2s ease; }
.sidebar-panel.collapsed { width: 44px; padding: 0.5rem; overflow: hidden; }
.toggle-btn { background: none; border: none; color: #94a3b8; cursor: pointer; padding: 6px; border-radius: 6px; display: flex; align-items: center; justify-content: center; transition: color 0.2s, background 0.2s; margin-bottom: 0.5rem; }
.toggle-btn:hover { color: #1e293b; background: #f1f5f9; }
.sidebar-panel h3 { margin-bottom: 0.5rem; font-size: 0.9rem; text-transform: uppercase; color: #64748b; }
.customer-filter { width: 100%; padding: 4px 8px; font-size: 0.8rem; border: 1px solid #e2e8f0; border-radius: 4px; margin-bottom: 0.5rem; box-sizing: border-box; }
.customer-filter:focus { outline: none; border-color: #3b82f6; }
.customer-item { display: flex; align-items: center; gap: 8px; padding: 0.5rem; border: 1px solid #e2e8f0; border-radius: 4px; margin-bottom: 0.5rem; cursor: grab; background: #fff; transition: border-color 0.2s, background 0.2s; }
.customer-item:hover { border-color: #3b82f6; background: #eff6ff; }
.customer-logo { width: 24px; height: 24px; border-radius: 4px; object-fit: contain; flex-shrink: 0; }
.customer-name { font-weight: 600; font-size: 0.85rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { color: #94a3b8; font-size: 0.85rem; text-align: center; padding: 1rem; }
</style>
```

- [ ] **Step 3: Update TimelineGrid.vue**

Replace the full content of `frontend/src/components/TimelineGrid.vue`. Key changes from old version:
- `ClipboardItem` uses `customerId` instead of `projectId`
- `isInClipboard`/`toggleClipboardItem` match on `customerId`
- Assignment block label: `"CustomerName XX%"` (no project name)
- Drop handler data expects `customerId`

```vue
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { architectApi } from '@/api/client'
import type { ArchitectUsage, MonthUsage, AssignmentUsage } from '@/types'

defineProps<{ usageData: ArchitectUsage[]; months: string[] }>()

const emit = defineEmits<{
  drop: [architectId: number, month: string, data: { customerId: number; tentative: boolean; defaultUsagePercent: number; usagePercent?: number }]
  unassign: [assignmentId: number]
  editUsage: [assignmentId: number, usage: number, month: string]
}>()

const editingAssignment = ref<{ assignmentId: number; usage: number; month: string; el: HTMLElement } | null>(null)
const hoverPhoto = ref<{ src: string; top: number; left: number } | null>(null)

interface ClipboardItem {
  customerId: number
  tentative: boolean
  usagePercent: number
}

const clipboard = ref<ClipboardItem[]>([])
const pasteCount = ref(0)

function isInClipboard(customerId: number): boolean {
  return clipboard.value.some(c => c.customerId === customerId)
}

function toggleClipboardItem(event: MouseEvent, assignment: AssignmentUsage) {
  if (!event.metaKey && !event.ctrlKey) return false
  event.stopPropagation()
  event.preventDefault()

  const idx = clipboard.value.findIndex(c => c.customerId === assignment.customerId)
  if (idx >= 0) {
    clipboard.value.splice(idx, 1)
  } else {
    clipboard.value.push({
      customerId: assignment.customerId,
      tentative: assignment.tentative,
      usagePercent: assignment.usage,
    })
  }
  pasteCount.value = 0
  return true
}

function clearClipboard() {
  clipboard.value = []
  pasteCount.value = 0
}

function onAvatarEnter(event: MouseEvent, architectId: number) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  hoverPhoto.value = {
    src: architectApi.photoUrl(architectId),
    top: rect.top - 20,
    left: rect.right + 8,
  }
}

function onAvatarLeave() {
  hoverPhoto.value = null
}

function openUsageEditor(event: MouseEvent, assignmentId: number, currentUsage: number, month: string) {
  event.stopPropagation()
  editingAssignment.value = {
    assignmentId,
    usage: currentUsage,
    month,
    el: event.currentTarget as HTMLElement,
  }
}

function onSliderChange(value: number) {
  if (!editingAssignment.value) return
  editingAssignment.value.usage = value
}

function saveUsage() {
  if (!editingAssignment.value) return
  emit('editUsage', editingAssignment.value.assignmentId, editingAssignment.value.usage, editingAssignment.value.month)
  editingAssignment.value = null
}

function closeEditor() {
  if (editingAssignment.value) saveUsage()
}

function usageColor(total: number): string {
  if (total === 0) return 'transparent'
  if (total >= 50 && total <= 70) return '#dcfce7'
  if (total > 30 && total < 50) return '#fef3c7'
  return '#fecaca'
}

function usageLabelColor(total: number): string {
  if (total === 0) return '#94a3b8'
  if (total >= 50 && total <= 70) return '#16a34a'
  if (total > 30 && total < 50) return '#d97706'
  return '#dc2626'
}

function formatMonth(ym: string): string {
  const [year, month] = ym.split('-')
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${months[parseInt(month) - 1]} ${year.slice(2)}`
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  event.dataTransfer!.dropEffect = 'copy'
}

function onDrop(event: DragEvent, architectId: number, month: string) {
  event.preventDefault()
  const raw = event.dataTransfer!.getData('application/json')
  if (!raw) return
  const data = JSON.parse(raw)
  if (data.type === 'customer') {
    emit('drop', architectId, month, {
      customerId: data.customerId,
      tentative: data.tentative,
      defaultUsagePercent: data.defaultUsagePercent,
    })
  }
}

function onCellClick(architectId: number, month: string, monthData: MonthUsage) {
  if (clipboard.value.length === 0) return

  const existingCustomerIds = new Set(monthData.assignments.map(a => a.customerId))
  const toPaste = clipboard.value.filter(c => !existingCustomerIds.has(c.customerId))

  for (const item of toPaste) {
    emit('drop', architectId, month, {
      customerId: item.customerId,
      tentative: item.tentative,
      defaultUsagePercent: item.usagePercent,
      usagePercent: item.usagePercent,
    })
  }

  if (toPaste.length > 0) pasteCount.value++
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && clipboard.value.length > 0) clearClipboard()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))

const isMac = /Mac|iPhone|iPad/.test(window.navigator.platform || '') || window.navigator.userAgent.includes('Mac')

function getMonthData(architect: ArchitectUsage, month: string): MonthUsage {
  return architect.months[month] ?? { total: 0, assignments: [] }
}
</script>

<template>
  <div class="timeline-grid" @click="closeEditor">
    <table>
      <thead>
        <tr>
          <th class="architect-col">Architect</th>
          <th v-for="month in months" :key="month" class="month-col">{{ formatMonth(month) }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="architect in usageData" :key="architect.architectId">
          <td class="architect-col">
            <div class="architect-info">
              <img :src="architectApi.photoUrl(architect.architectId)"
                class="architect-avatar"
                @mouseenter="onAvatarEnter($event, architect.architectId)"
                @mouseleave="onAvatarLeave"
                @error="($event.target as HTMLImageElement).style.display = 'none'" />
              <div>
                <div class="architect-name">{{ architect.architectName.split(' ')[0] }}</div>
                <div class="architect-name">{{ architect.architectName.split(' ').slice(1).join(' ') }}</div>
                <div class="architect-country">{{ architect.country }}</div>
              </div>
            </div>
          </td>
          <td v-for="month in months" :key="month" class="month-cell"
            :style="{ background: usageColor(getMonthData(architect, month).total) }"
            :class="{ 'paste-target': clipboard.length > 0 }"
            @dragover="onDragOver" @drop="onDrop($event, architect.architectId, month)"
            @click="onCellClick(architect.architectId, month, getMonthData(architect, month))">
            <div class="cell-content">
              <div v-for="assignment in getMonthData(architect, month).assignments" :key="assignment.assignmentId"
                class="assignment-block" :class="{ tentative: assignment.tentative, selected: isInClipboard(assignment.customerId) }"
                :title="`${assignment.customerName} - ${assignment.usage}%`"
                @click.stop="toggleClipboardItem($event, assignment) || openUsageEditor($event, assignment.assignmentId, assignment.usage, month)">
                <span class="assignment-label">{{ isInClipboard(assignment.customerId) ? '✓ ' : '' }}{{ assignment.customerName }} {{ assignment.usage }}%</span>
                <button class="unassign-btn" @click.stop="emit('unassign', assignment.assignmentId)" title="Unassign">&minus;</button>
              </div>
              <div v-if="getMonthData(architect, month).total > 0" class="total-label"
                :style="{ color: usageLabelColor(getMonthData(architect, month).total) }">
                {{ getMonthData(architect, month).total }}%
              </div>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="clipboard.length === 0" class="copy-hint">
      💡 <strong>Tip:</strong> {{ isMac ? '⌘' : 'Ctrl' }}+Click an assignment to copy it
    </div>

    <div v-if="clipboard.length > 0" class="clipboard-bar" data-testid="clipboard-bar">
      <span>📋 <strong>{{ clipboard.length }} assignment{{ clipboard.length > 1 ? 's' : '' }}</strong>
        {{ pasteCount > 0 ? `— pasted to ${pasteCount} cell${pasteCount > 1 ? 's' : ''} so far` : '— click cells to paste' }}
        &nbsp;•&nbsp; <strong>Esc</strong> to {{ pasteCount > 0 ? 'finish' : 'cancel' }}
      </span>
      <button class="clipboard-clear" @click="clearClipboard">✕ {{ pasteCount > 0 ? 'Done' : 'Clear' }}</button>
    </div>

    <Teleport to="body">
      <div v-if="editingAssignment" class="usage-editor-overlay" @click="closeEditor">
        <div class="usage-editor" @click.stop
          :style="{
            top: (editingAssignment.el.getBoundingClientRect().bottom + 4) + 'px',
            left: editingAssignment.el.getBoundingClientRect().left + 'px',
          }">
          <div class="usage-editor-value">{{ editingAssignment.usage }}%</div>
          <input type="range" min="0" max="100" step="5"
            :value="editingAssignment.usage"
            @input="onSliderChange(Number(($event.target as HTMLInputElement).value))" />
          <div class="usage-editor-labels">
            <span>0%</span><span>50%</span><span>100%</span>
          </div>
          <button class="usage-editor-save" @click="saveUsage">Apply</button>
        </div>
      </div>
    </Teleport>
    <Teleport to="body">
      <div v-if="hoverPhoto" class="avatar-popup"
        :style="{ top: hoverPhoto.top + 'px', left: hoverPhoto.left + 'px' }">
        <img :src="hoverPhoto.src" />
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.timeline-grid { overflow-x: auto; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; flex: 1; min-height: 0; }
table { width: 100%; table-layout: fixed; }
.architect-col { position: sticky; left: 0; background: #f8fafc; z-index: 1; width: 160px; }
.architect-info { display: flex; align-items: center; gap: 8px; }
.architect-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; border: 2px solid #e2e8f0; cursor: pointer; }
.architect-name { font-weight: 600; font-size: 0.85rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.architect-country { font-size: 0.75rem; color: #64748b; }
.month-col { text-align: center; font-size: 0.8rem; }
.month-cell { vertical-align: top; min-height: 60px; transition: background 0.15s; }
.month-cell:hover { outline: 2px dashed #3b82f6; outline-offset: -2px; }
.month-cell.paste-target { outline: 2px dashed #93c5fd; outline-offset: -2px; cursor: pointer; }
.month-cell.paste-target:hover { outline-color: #3b82f6; background: #eff6ff !important; }
.cell-content { display: flex; flex-direction: column; gap: 2px; min-height: 50px; }
.assignment-block { font-size: 0.7rem; padding: 2px 4px; border-radius: 3px; background: #bfdbfe; border: 1px solid #93c5fd; white-space: nowrap; overflow: hidden; display: flex; align-items: center; gap: 2px; cursor: pointer; }
.assignment-block:hover { background: #93c5fd; }
.assignment-block.tentative { background: #fef9c3; border: 1px dashed #fbbf24; }
.assignment-block.tentative:hover { background: #fde68a; }
.assignment-block.selected { background: #60a5fa; border: 2px solid #2563eb; color: #fff; font-weight: 600; }
.assignment-block.selected:hover { background: #3b82f6; }
.assignment-block.selected .unassign-btn { color: rgba(255,255,255,0.6); }
.assignment-block.selected .unassign-btn:hover { color: #fff; background: rgba(255,255,255,0.2); }
.assignment-label { overflow: hidden; text-overflow: ellipsis; flex: 1; }
.unassign-btn { background: none; border: none; color: #94a3b8; cursor: pointer; font-size: 0.85rem; font-weight: 700; line-height: 1; padding: 0 2px; flex-shrink: 0; border-radius: 3px; }
.unassign-btn:hover { color: #dc2626; background: rgba(220,38,38,0.1); }
.total-label { font-size: 0.75rem; font-weight: 700; text-align: right; margin-top: auto; padding-top: 2px; }
.copy-hint { text-align: center; font-size: 0.75rem; color: #94a3b8; padding: 6px; }
.clipboard-bar { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 6px; margin: 8px; font-size: 0.85rem; }
.clipboard-clear { background: none; border: none; color: #3b82f6; cursor: pointer; font-weight: 600; font-size: 0.85rem; padding: 2px 6px; border-radius: 4px; }
.clipboard-clear:hover { background: #dbeafe; }
.usage-editor-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 200; }
.usage-editor { position: fixed; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); width: 180px; z-index: 201; }
.usage-editor-value { text-align: center; font-size: 1.1rem; font-weight: 700; margin-bottom: 6px; color: #0f172a; }
.usage-editor input[type="range"] { width: 100%; accent-color: #3b82f6; }
.usage-editor-labels { display: flex; justify-content: space-between; font-size: 0.7rem; color: #94a3b8; margin-top: 2px; }
.usage-editor-save { width: 100%; margin-top: 8px; padding: 4px; font-size: 0.8rem; background: #3b82f6; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.usage-editor-save:hover { background: #2563eb; }
</style>

<style>
.avatar-popup { position: fixed; z-index: 300; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.2); padding: 4px; pointer-events: none; }
.avatar-popup img { width: 200px; height: 200px; object-fit: cover; border-radius: 6px; display: block; }
</style>
```

- [ ] **Step 4: Update UsageTimelineView.vue**

Replace the full content of `frontend/src/views/UsageTimelineView.vue`:

```vue
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUsageStore } from '@/stores/usage'
import { useCustomersStore } from '@/stores/customers'
import { useAssignmentsStore } from '@/stores/assignments'
import { useArchitectsStore } from '@/stores/architects'
import { useGeoStore } from '@/stores/geo'
import { assignmentApi, usageApi } from '@/api/client'
import TimelineGrid from '@/components/TimelineGrid.vue'
import WorldMapView from '@/components/WorldMapView.vue'
import CustomerSidebar from '@/components/CustomerSidebar.vue'

const usageStore = useUsageStore()
const customerStore = useCustomersStore()
const assignmentStore = useAssignmentsStore()
const architectStore = useArchitectsStore()
const geo = useGeoStore()

const countryFilter = ref('')
const toast = ref('')

const architectCountries = computed(() => {
  const countries = new Set<string>()
  for (const a of architectStore.architects) {
    if (a.country) countries.add(a.country)
  }
  return [...countries].sort()
})
const nameFilter = ref('')
const activeTab = ref<'timeline' | 'map'>('timeline')

function currentYearMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function addMonths(ym: string, n: number): string {
  const [y, m] = ym.split('-').map(Number)
  const date = new Date(y, m - 1 + n)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

const fromMonth = ref(currentYearMonth())
const monthCount = ref(6)
const toMonth = computed(() => addMonths(fromMonth.value, monthCount.value - 1))

const months = computed(() => {
  const result: string[] = []
  let current = fromMonth.value
  while (current <= toMonth.value) {
    result.push(current)
    current = addMonths(current, 1)
  }
  return result
})

const filteredUsage = computed(() => {
  let data = [...usageStore.usageData].sort((a, b) =>
    a.architectName.localeCompare(b.architectName)
  )
  if (nameFilter.value) {
    const search = nameFilter.value.toLowerCase()
    data = data.filter(a => a.architectName.toLowerCase().includes(search))
  }
  return data
})

async function loadData() {
  await Promise.all([
    usageStore.fetchUsage(fromMonth.value, toMonth.value, countryFilter.value || undefined),
    customerStore.fetchAll(),
    architectStore.fetchAll(),
    geo.fetchCountries(),
  ])
}

onMounted(loadData)
watch([countryFilter, fromMonth, monthCount], loadData)

function showToast(message: string) {
  toast.value = message
  setTimeout(() => { toast.value = '' }, 3000)
}

async function onDrop(
  architectId: number,
  month: string,
  data: { customerId: number; tentative: boolean; defaultUsagePercent: number; usagePercent?: number }
) {
  try {
    await assignmentStore.create({
      architectId,
      customerId: data.customerId,
      usagePercent: data.usagePercent ?? data.defaultUsagePercent,
      tentative: data.tentative,
      month,
    })
  } catch (e: any) {
    if (e.response?.status === 409) {
      showToast('Already assigned for this month')
      return
    }
    throw e
  }
  await loadData()
}

async function onUnassign(assignmentId: number) {
  await assignmentApi.delete(assignmentId)
  await loadData()
}

async function onEditUsage(assignmentId: number, usage: number, _month: string) {
  await assignmentApi.update(assignmentId, { usagePercent: usage })
  await loadData()
}

const exporting = ref(false)

async function exportToExcel() {
  exporting.value = true
  try {
    await usageApi.exportExcel(fromMonth.value, toMonth.value, countryFilter.value || undefined)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="timeline-view">
    <div class="main-area">
      <div class="header">
        <h1>Usage Timeline</h1>
        <div class="controls">
          <input v-model="nameFilter" placeholder="Filter by name..." data-testid="name-filter" />
          <select v-model="countryFilter" data-testid="country-filter">
            <option value="">All countries</option>
            <option v-for="c in architectCountries" :key="c" :value="c">{{ c }}</option>
          </select>
          <template v-if="activeTab === 'timeline'">
            <label>From: <input v-model="fromMonth" type="month" /></label>
            <label>Months:
              <select v-model.number="monthCount">
                <option :value="3">3</option>
                <option :value="6">6</option>
                <option :value="9">9</option>
                <option :value="12">12</option>
              </select>
            </label>
            <button class="export-btn" @click="exportToExcel" :disabled="exporting">
              {{ exporting ? 'Exporting...' : 'Export Excel' }}
            </button>
          </template>
          <template v-else>
            <div class="month-nav">
              <button class="month-nav-btn" @click="fromMonth = addMonths(fromMonth, -1)" data-testid="month-prev">&laquo;</button>
              <input v-model="fromMonth" type="month" data-testid="month-picker" />
              <button class="month-nav-btn" @click="fromMonth = addMonths(fromMonth, 1)" data-testid="month-next">&raquo;</button>
            </div>
          </template>
        </div>
      </div>
      <div class="tab-bar">
        <button
          v-for="tab in (['timeline', 'map'] as const)"
          :key="tab"
          class="tab-btn"
          :class="{ active: activeTab === tab }"
          data-testid="view-tab"
          @click="activeTab = tab"
        >
          {{ tab === 'timeline' ? 'Timeline' : 'Map' }}
        </button>
      </div>
      <TimelineGrid
        v-if="activeTab === 'timeline'"
        :usage-data="filteredUsage"
        :months="months"
        @drop="onDrop"
        @unassign="onUnassign"
        @edit-usage="(id: number, usage: number, month: string) => onEditUsage(id, usage, month)"
      />
      <WorldMapView
        v-else
        :usage-data="filteredUsage"
        :month="fromMonth"
      />
    </div>
    <CustomerSidebar
      v-if="activeTab === 'timeline'"
      :customers="customerStore.customers"
    />
    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<style scoped>
.timeline-view { display: flex; gap: 0; height: calc(100vh - 3rem); position: relative; }
.main-area { flex: 1; min-width: 0; overflow: hidden; display: flex; flex-direction: column; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; flex-shrink: 0; }
.controls { display: flex; gap: 0.5rem; align-items: center; }
.controls label { display: flex; align-items: center; gap: 0.25rem; font-size: 0.85rem; }
.tab-bar { display: flex; gap: 0; margin-bottom: 0.75rem; border-bottom: 2px solid #e2e8f0; flex-shrink: 0; }
.tab-btn { padding: 0.5rem 1.25rem; border: none; background: none; cursor: pointer; font-size: 0.9rem; color: #64748b; border-bottom: 2px solid transparent; margin-bottom: -2px; transition: color 0.15s, border-color 0.15s; }
.tab-btn:hover { color: #334155; }
.tab-btn.active { color: #0f172a; border-bottom-color: #3b82f6; font-weight: 600; }
.month-nav { display: flex; align-items: center; gap: 0.25rem; }
.month-nav-btn { padding: 0.25rem 0.5rem; background: #e2e8f0; border: 1px solid #cbd5e1; border-radius: 4px; cursor: pointer; font-size: 1rem; line-height: 1; }
.month-nav-btn:hover { background: #cbd5e1; }
.export-btn { padding: 0.35rem 0.75rem; background: #059669; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 0.8rem; font-weight: 600; }
.export-btn:hover { background: #047857; }
.export-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.toast { position: fixed; bottom: 1.5rem; left: 50%; transform: translateX(-50%); background: #334155; color: #fff; padding: 0.5rem 1.25rem; border-radius: 6px; font-size: 0.85rem; z-index: 300; box-shadow: 0 4px 12px rgba(0,0,0,0.2); }
</style>
```

- [ ] **Step 5: Verify frontend builds**

```bash
cd frontend && npm run build
```

Expected: build succeeds with no errors.

- [ ] **Step 6: Commit**

```bash
git add -A frontend/src/
git commit -m "feat: replace ProjectSidebar with CustomerSidebar, update timeline for customer-based assignments"
```

---

### Task 8: Frontend Tests and Final Verification

**Files:**
- Create: `frontend/src/__tests__/CustomersView.spec.ts`
- Modify: existing test files as needed

**Interfaces:**
- Consumes: all frontend components
- Produces: passing test suite

- [ ] **Step 1: Create CustomersView test**

Create `frontend/src/__tests__/CustomersView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CustomersView from '@/views/CustomersView.vue'

vi.mock('@/api/client', () => ({
  customerApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, name: 'Acme Corp', country: 'Australia', city: 'Sydney' },
      { id: 2, name: 'Beta Inc', country: 'Singapore' },
    ]),
    create: vi.fn().mockResolvedValue({ id: 3, name: 'New Co' }),
    delete: vi.fn().mockResolvedValue(undefined),
    logoUrl: vi.fn((id: number) => `/api/customers/${id}/logo`),
  },
}))

describe('CustomersView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders customers with location', async () => {
    const wrapper = mount(CustomersView)
    await flushPromises()

    expect(wrapper.text()).toContain('Acme Corp')
    expect(wrapper.text()).toContain('Sydney')
    expect(wrapper.text()).toContain('Australia')
    expect(wrapper.text()).toContain('Beta Inc')
  })

  it('filters customers by text', async () => {
    const wrapper = mount(CustomersView)
    await flushPromises()

    await wrapper.find('.filter-input').setValue('beta')
    expect(wrapper.text()).toContain('Beta Inc')
    expect(wrapper.text()).not.toContain('Acme Corp')
  })
})
```

- [ ] **Step 2: Run frontend tests**

```bash
cd frontend && npm run test
```

Fix any test failures. The `geo.spec.ts`, `ArchitectsView.spec.ts`, `WorldMapView.spec.ts`, and `UsageTimelineView.spec.ts` tests may need minor updates if they reference project types. Fix imports that reference deleted modules.

- [ ] **Step 3: Run backend tests**

```bash
export JAVA_HOME=$(mise where java) && export PATH="$JAVA_HOME/bin:$PATH"
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A frontend/src/__tests__/
git commit -m "test: add CustomersView tests, verify all tests pass"
```
