# Remove Region, Replace Ref Data with External Geo APIs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Region concept and local ref data tables (Region, Country, City). Replace with runtime lookups against REST Countries and CountriesNow APIs. Store country and city as plain strings on Architect and Customer.

**Architecture:** Backend entities switch from FK-based IDs to plain strings. The frontend fetches country lists from `restcountries.com` and city lists from `countriesnow.space` on demand. The backend no longer has any geographic reference data — it just stores/returns strings. A Liquibase migration handles the data conversion and table drops.

**Tech Stack:** Spring Boot (Java 17+), Liquibase, Vue 3 + Pinia + TypeScript, Vitest, axios, external APIs (REST Countries v3.1, CountriesNow v0.1)

---

## File Map

### Backend — Delete
- `backend/src/main/java/org/lolobored/tm/refdata/Region.java`
- `backend/src/main/java/org/lolobored/tm/refdata/RegionRepository.java`
- `backend/src/main/java/org/lolobored/tm/refdata/Country.java`
- `backend/src/main/java/org/lolobored/tm/refdata/CountryRepository.java`
- `backend/src/main/java/org/lolobored/tm/refdata/City.java`
- `backend/src/main/java/org/lolobored/tm/refdata/CityRepository.java`
- `backend/src/main/java/org/lolobored/tm/refdata/RefDataController.java`

### Backend — Modify
- `backend/src/main/java/org/lolobored/tm/architect/Architect.java` — remove `regionId`, replace `countryId`/`cityId` with `country`/`city` strings
- `backend/src/main/java/org/lolobored/tm/customer/Customer.java` — replace `countryId`/`cityId` with `country`/`city` strings
- `backend/src/main/java/org/lolobored/tm/usage/ArchitectUsageDto.java` — `region` -> `country`
- `backend/src/main/java/org/lolobored/tm/usage/UsageService.java` — remove RegionRepository, use string country
- `backend/src/main/java/org/lolobored/tm/usage/UsageController.java` — `regionId` param -> `country` string param

### Backend — Create
- `backend/src/main/resources/db/changelog/008-remove-region-string-geo.yaml` — migration

### Backend Tests — Modify
- `backend/src/test/java/org/lolobored/tm/architect/ArchitectControllerTest.java`
- `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`
- `backend/src/test/java/org/lolobored/tm/customer/CustomerControllerTest.java`
- `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java`

### Frontend — Delete
- `frontend/src/stores/refdata.ts`
- `frontend/src/views/AdminView.vue`

### Frontend — Create
- `frontend/src/stores/geo.ts` — new store calling external APIs

### Frontend — Modify
- `frontend/src/types/index.ts`
- `frontend/src/api/client.ts`
- `frontend/src/components/ArchitectForm.vue`
- `frontend/src/components/CustomerForm.vue`
- `frontend/src/views/ArchitectsView.vue`
- `frontend/src/views/UsageTimelineView.vue`
- `frontend/src/views/CustomersProjectsView.vue`
- `frontend/src/components/TimelineGrid.vue`
- `frontend/src/router/index.ts`
- `frontend/src/components/AppLayout.vue`
- `frontend/src/stores/usage.ts`

### Frontend Tests — Modify
- `frontend/src/__tests__/ArchitectsView.spec.ts`
- `frontend/src/__tests__/CustomersProjectsView.spec.ts`
- `frontend/src/__tests__/UsageTimelineView.spec.ts`

---

## Task 1: Database migration — add string columns, migrate data, drop old tables

**Files:**
- Create: `backend/src/main/resources/db/changelog/008-remove-region-string-geo.yaml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Create migration file**

Create `backend/src/main/resources/db/changelog/008-remove-region-string-geo.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 8a
      author: laurent
      comment: Add string country/city columns to architect
      changes:
        - addColumn:
            tableName: architect
            columns:
              - column:
                  name: country
                  type: varchar(255)
              - column:
                  name: city
                  type: varchar(255)
        - sql:
            sql: >
              UPDATE architect SET
                country = (SELECT c.name FROM country c WHERE c.id = architect.country_id),
                city = (SELECT ci.name FROM city ci WHERE ci.id = architect.city_id)
        - dropColumn:
            tableName: architect
            columnName: region_id
        - dropColumn:
            tableName: architect
            columnName: country_id
        - dropColumn:
            tableName: architect
            columnName: city_id

  - changeSet:
      id: 8b
      author: laurent
      comment: Add string country/city columns to customer
      changes:
        - addColumn:
            tableName: customer
            columns:
              - column:
                  name: country
                  type: varchar(255)
              - column:
                  name: city
                  type: varchar(255)
        - sql:
            sql: >
              UPDATE customer SET
                country = (SELECT c.name FROM country c WHERE c.id = customer.country_id),
                city = (SELECT ci.name FROM city ci WHERE ci.id = customer.city_id)
        - dropColumn:
            tableName: customer
            columnName: country_id
        - dropColumn:
            tableName: customer
            columnName: city_id

  - changeSet:
      id: 8c
      author: laurent
      comment: Drop reference data tables
      changes:
        - dropTable:
            tableName: city
        - dropTable:
            tableName: country
        - dropTable:
            tableName: region
```

- [ ] **Step 2: Register migration in changelog master**

In `backend/src/main/resources/db/changelog/db.changelog-master.yaml`, add at the end:

```yaml
  - include:
      file: db/changelog/008-remove-region-string-geo.yaml
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/changelog/008-remove-region-string-geo.yaml backend/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat: add migration to convert geo FKs to strings and drop ref data tables"
```

---

## Task 2: Update backend entities — Architect and Customer

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/architect/Architect.java`
- Modify: `backend/src/main/java/org/lolobored/tm/customer/Customer.java`

- [ ] **Step 1: Update Architect entity**

Replace the full contents of `backend/src/main/java/org/lolobored/tm/architect/Architect.java`:

```java
package org.lolobored.tm.architect;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Architect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String firstName;

    @NotBlank
    @Column(nullable = false)
    private String lastName;

    private String email;

    private String country;

    private String city;

    @JsonIgnore
    @Column(columnDefinition = "bytea")
    private byte[] photo;

    @JsonIgnore
    private String photoContentType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public byte[] getPhoto() { return photo; }
    public void setPhoto(byte[] photo) { this.photo = photo; }
    public String getPhotoContentType() { return photoContentType; }
    public void setPhotoContentType(String photoContentType) { this.photoContentType = photoContentType; }
    public boolean hasPhoto() { return photo != null && photo.length > 0; }
}
```

- [ ] **Step 2: Update Customer entity**

Replace the full contents of `backend/src/main/java/org/lolobored/tm/customer/Customer.java`:

```java
package org.lolobored.tm.customer;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String country;
    private String city;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/architect/Architect.java backend/src/main/java/org/lolobored/tm/customer/Customer.java
git commit -m "feat: replace FK-based geo fields with plain strings on Architect and Customer"
```

---

## Task 3: Update UsageService, UsageController, and ArchitectUsageDto

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/usage/ArchitectUsageDto.java`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/UsageController.java`

- [ ] **Step 1: Update ArchitectUsageDto**

Replace the full contents of `backend/src/main/java/org/lolobored/tm/usage/ArchitectUsageDto.java`:

```java
package org.lolobored.tm.usage;

import java.time.YearMonth;
import java.util.Map;

public record ArchitectUsageDto(
        Long architectId, String architectName, String country,
        Map<YearMonth, MonthUsageDto> months
) {}
```

- [ ] **Step 2: Update UsageService**

Replace the full contents of `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`:

```java
package org.lolobored.tm.usage;

import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.assignment.Assignment;
import org.lolobored.tm.assignment.AssignmentRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.lolobored.tm.project.Project;
import org.lolobored.tm.project.ProjectRepository;
import org.lolobored.tm.project.ProjectStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class UsageService {

    private final ArchitectRepository architectRepository;
    private final AssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final CustomerRepository customerRepository;

    public UsageService(ArchitectRepository architectRepository,
                        AssignmentRepository assignmentRepository,
                        ProjectRepository projectRepository,
                        CustomerRepository customerRepository) {
        this.architectRepository = architectRepository;
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
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

        Map<Long, Project> projectCache = new HashMap<>();
        projectRepository.findAll().forEach(p -> projectCache.put(p.getId(), p));

        Map<Long, Customer> customerCache = new HashMap<>();
        customerRepository.findAll().forEach(c -> customerCache.put(c.getId(), c));

        List<ArchitectUsageDto> result = new ArrayList<>();

        for (Architect architect : architects) {
            List<Assignment> assignments = assignmentRepository.findByArchitectId(architect.getId());
            Map<YearMonth, MonthUsageDto> months = new LinkedHashMap<>();

            for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
                List<AssignmentUsageDto> monthAssignments = new ArrayList<>();

                for (Assignment assignment : assignments) {
                    Project project = projectCache.get(assignment.getProjectId());
                    if (project == null) continue;

                    LocalDate effectiveStart = getEffectiveStartDate(project);
                    if (effectiveStart == null) continue;

                    YearMonth projectStart = YearMonth.from(effectiveStart);
                    YearMonth projectEnd = projectStart.plusMonths(project.getDurationMonths() - 1);

                    if (assignment.getStartMonth() != null) {
                        YearMonth assignmentStart = YearMonth.parse(assignment.getStartMonth());
                        if (assignmentStart.isAfter(projectStart)) {
                            projectStart = assignmentStart;
                        }
                    }

                    if (assignment.getEndMonth() != null) {
                        YearMonth assignmentEnd = YearMonth.parse(assignment.getEndMonth());
                        if (assignmentEnd.isBefore(projectEnd)) {
                            projectEnd = assignmentEnd;
                        }
                    }

                    if (month.isBefore(projectStart) || month.isAfter(projectEnd)) continue;

                    int usage = assignment.getUsagePercent() != null
                            ? assignment.getUsagePercent()
                            : project.getDefaultUsagePercent();

                    Customer customer = customerCache.get(project.getCustomerId());
                    String customerName = customer != null ? customer.getName() : "";

                    monthAssignments.add(new AssignmentUsageDto(
                            project.getId(), project.getName(), customerName,
                            usage, assignment.isTentative()));
                }

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

    private LocalDate getEffectiveStartDate(Project project) {
        if (project.getStatus() == ProjectStatus.ACTIVE) return project.getStartDate();
        if (project.getPlausibleDate() != null) return project.getPlausibleDate();
        return project.getOfficialDate();
    }
}
```

- [ ] **Step 3: Update UsageController**

Replace the full contents of `backend/src/main/java/org/lolobored/tm/usage/UsageController.java`:

```java
package org.lolobored.tm.usage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/usage")
public class UsageController {
    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping
    public List<ArchitectUsageDto> getUsage(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Long architectId) {
        try {
            return usageService.computeUsage(
                    YearMonth.parse(from), YearMonth.parse(to), country, architectId);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM.");
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/usage/ArchitectUsageDto.java backend/src/main/java/org/lolobored/tm/usage/UsageService.java backend/src/main/java/org/lolobored/tm/usage/UsageController.java
git commit -m "feat: replace region with country string in usage service and controller"
```

---

## Task 4: Delete refdata package

**Files:**
- Delete: `backend/src/main/java/org/lolobored/tm/refdata/Region.java`
- Delete: `backend/src/main/java/org/lolobored/tm/refdata/RegionRepository.java`
- Delete: `backend/src/main/java/org/lolobored/tm/refdata/Country.java`
- Delete: `backend/src/main/java/org/lolobored/tm/refdata/CountryRepository.java`
- Delete: `backend/src/main/java/org/lolobored/tm/refdata/City.java`
- Delete: `backend/src/main/java/org/lolobored/tm/refdata/CityRepository.java`
- Delete: `backend/src/main/java/org/lolobored/tm/refdata/RefDataController.java`

- [ ] **Step 1: Delete the entire refdata package**

```bash
rm -rf backend/src/main/java/org/lolobored/tm/refdata
```

- [ ] **Step 2: Commit**

```bash
git add -A backend/src/main/java/org/lolobored/tm/refdata
git commit -m "feat: delete refdata package (Region, Country, City entities and controller)"
```

---

## Task 5: Update backend tests

**Files:**
- Modify: `backend/src/test/java/org/lolobored/tm/architect/ArchitectControllerTest.java`
- Modify: `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`
- Modify: `backend/src/test/java/org/lolobored/tm/customer/CustomerControllerTest.java`
- Modify: `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java`

- [ ] **Step 1: Update ArchitectControllerTest**

Replace the full contents of `backend/src/test/java/org/lolobored/tm/architect/ArchitectControllerTest.java`:

```java
package org.lolobored.tm.architect;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ArchitectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void listArchitects_empty() throws Exception {
        mockMvc.perform(get("/api/architects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createAndGetArchitect() throws Exception {
        String json = """
                {"firstName": "Alice", "lastName": "Smith", "email": "alice@example.com", "country": "Australia", "city": "Sydney"}
                """;

        String response = mockMvc.perform(post("/api/architects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.country").value("Australia"))
                .andExpect(jsonPath("$.city").value("Sydney"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/architects/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    @Test
    void updateArchitect() throws Exception {
        String json = """
                {"firstName": "Alice", "lastName": "Smith", "country": "Australia"}
                """;
        String response = mockMvc.perform(post("/api/architects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        String updateJson = """
                {"firstName": "Alice", "lastName": "Updated", "country": "New Zealand"}
                """;
        mockMvc.perform(put("/api/architects/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Updated"))
                .andExpect(jsonPath("$.country").value("New Zealand"));
    }

    @Test
    void deleteArchitect() throws Exception {
        String json = """
                {"firstName": "Alice", "lastName": "Smith"}
                """;
        String response = mockMvc.perform(post("/api/architects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/architects/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/architects/" + id))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Update AssignmentControllerTest**

Replace the full contents of `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`:

```java
package org.lolobored.tm.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.lolobored.tm.project.Project;
import org.lolobored.tm.project.ProjectRepository;
import org.lolobored.tm.project.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

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
    @Autowired private ProjectRepository projectRepository;

    private Long architectId;
    private Long projectId;

    @BeforeEach
    void setUp() {
        Architect architect = new Architect();
        architect.setFirstName("Alice");
        architect.setLastName("Smith");
        architect.setCountry("Australia");
        architectId = architectRepository.save(architect).getId();

        Customer customer = new Customer();
        customer.setName("Acme");
        Long customerId = customerRepository.save(customer).getId();

        Project project = new Project();
        project.setCustomerId(customerId);
        project.setName("KYC Platform");
        project.setStartDate(LocalDate.of(2026, 6, 1));
        project.setDurationMonths(6);
        project.setDefaultUsagePercent(20);
        project.setStatus(ProjectStatus.ACTIVE);
        projectId = projectRepository.save(project).getId();
    }

    @Test
    void createAndGetAssignment() throws Exception {
        String json = """
                {"architectId": %d, "projectId": %d, "usagePercent": 25, "tentative": false}
                """.formatted(architectId, projectId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.architectId").value(architectId))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.usagePercent").value(25))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/assignments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usagePercent").value(25));
    }

    @Test
    void createAssignment_nullUsagePercent_fallsBackToProjectDefault() throws Exception {
        String json = """
                {"architectId": %d, "projectId": %d, "tentative": false}
                """.formatted(architectId, projectId);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usagePercent").isEmpty());
    }

    @Test
    void listAssignments_filterByArchitect() throws Exception {
        String json = """
                {"architectId": %d, "projectId": %d, "tentative": false}
                """.formatted(architectId, projectId);
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
                {"architectId": %d, "projectId": %d, "tentative": false}
                """.formatted(architectId, projectId);

        String response = mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(delete("/api/assignments/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/assignments/" + id)).andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 3: Update CustomerControllerTest**

Replace the full contents of `backend/src/test/java/org/lolobored/tm/customer/CustomerControllerTest.java`:

```java
package org.lolobored.tm.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class CustomerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void listCustomers_empty() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createAndGetCustomer() throws Exception {
        String json = """
                {"name": "Acme Corp", "country": "Australia", "city": "Melbourne"}
                """;
        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.country").value("Australia"))
                .andExpect(jsonPath("$.city").value("Melbourne"))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void updateCustomer() throws Exception {
        String json = """
                {"name": "Acme Corp"}
                """;
        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();
        String updateJson = """
                {"name": "Acme Holdings", "country": "New Zealand"}
                """;
        mockMvc.perform(put("/api/customers/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Holdings"))
                .andExpect(jsonPath("$.country").value("New Zealand"));
    }

    @Test
    void deleteCustomer() throws Exception {
        String json = """
                {"name": "Acme Corp"}
                """;
        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(delete("/api/customers/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 4: Update UsageServiceTest**

Replace the full contents of `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java`:

```java
package org.lolobored.tm.usage;

import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.assignment.Assignment;
import org.lolobored.tm.assignment.AssignmentRepository;
import org.lolobored.tm.customer.Customer;
import org.lolobored.tm.customer.CustomerRepository;
import org.lolobored.tm.project.Project;
import org.lolobored.tm.project.ProjectRepository;
import org.lolobored.tm.project.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class UsageServiceTest {

    @Autowired private UsageService usageService;
    @Autowired private ArchitectRepository architectRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AssignmentRepository assignmentRepository;

    private Long architectId;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        projectRepository.deleteAll();
        customerRepository.deleteAll();
        architectRepository.deleteAll();

        Architect architect = new Architect();
        architect.setFirstName("Alice");
        architect.setLastName("Smith");
        architect.setCountry("Australia");
        architectId = architectRepository.save(architect).getId();

        Customer customer = new Customer();
        customer.setName("Acme");
        Long customerId = customerRepository.save(customer).getId();

        Project activeProject = new Project();
        activeProject.setCustomerId(customerId);
        activeProject.setName("KYC Platform");
        activeProject.setStartDate(LocalDate.of(2026, 6, 1));
        activeProject.setDurationMonths(3);
        activeProject.setDefaultUsagePercent(20);
        activeProject.setStatus(ProjectStatus.ACTIVE);
        Long activeProjectId = projectRepository.save(activeProject).getId();

        Assignment a1 = new Assignment();
        a1.setArchitectId(architectId);
        a1.setProjectId(activeProjectId);
        a1.setUsagePercent(25);
        a1.setTentative(false);
        assignmentRepository.save(a1);

        Project potentialProject = new Project();
        potentialProject.setCustomerId(customerId);
        potentialProject.setName("Future Work");
        potentialProject.setDurationMonths(2);
        potentialProject.setDefaultUsagePercent(15);
        potentialProject.setStatus(ProjectStatus.POTENTIAL);
        potentialProject.setOfficialDate(LocalDate.of(2026, 8, 1));
        potentialProject.setPlausibleDate(LocalDate.of(2026, 9, 1));
        Long potentialProjectId = projectRepository.save(potentialProject).getId();

        Assignment a2 = new Assignment();
        a2.setArchitectId(architectId);
        a2.setProjectId(potentialProjectId);
        a2.setTentative(true);
        assignmentRepository.save(a2);
    }

    @Test
    void computeUsage_activeProject_spansCorrectMonths() {
        YearMonth from = YearMonth.of(2026, 6);
        YearMonth to = YearMonth.of(2026, 8);

        List<ArchitectUsageDto> result = usageService.computeUsage(from, to, null, null);

        assertEquals(1, result.size());
        ArchitectUsageDto dto = result.get(0);
        assertEquals("Alice Smith", dto.architectName());
        assertEquals("Australia", dto.country());

        MonthUsageDto june = dto.months().get(YearMonth.of(2026, 6));
        assertNotNull(june);
        assertEquals(25, june.total());
        assertEquals(1, june.assignments().size());
        assertFalse(june.assignments().get(0).tentative());

        MonthUsageDto aug = dto.months().get(YearMonth.of(2026, 8));
        assertNotNull(aug);
        assertEquals(25, aug.total());
    }

    @Test
    void computeUsage_potentialProject_usesPlausibleDate() {
        YearMonth from = YearMonth.of(2026, 9);
        YearMonth to = YearMonth.of(2026, 10);

        List<ArchitectUsageDto> result = usageService.computeUsage(from, to, null, null);

        ArchitectUsageDto dto = result.get(0);

        MonthUsageDto sept = dto.months().get(YearMonth.of(2026, 9));
        assertNotNull(sept);
        assertEquals(15, sept.total());
        assertTrue(sept.assignments().get(0).tentative());

        MonthUsageDto oct = dto.months().get(YearMonth.of(2026, 10));
        assertNotNull(oct);
        assertEquals(15, oct.total());
    }

    @Test
    void computeUsage_overlapMonth_sumsUsage() {
        Customer c = new Customer();
        c.setName("Beta");
        Long cId = customerRepository.save(c).getId();

        Project p2 = new Project();
        p2.setCustomerId(cId);
        p2.setName("Beta Project");
        p2.setStartDate(LocalDate.of(2026, 7, 1));
        p2.setDurationMonths(2);
        p2.setDefaultUsagePercent(20);
        p2.setStatus(ProjectStatus.ACTIVE);
        Long p2Id = projectRepository.save(p2).getId();

        Assignment a = new Assignment();
        a.setArchitectId(architectId);
        a.setProjectId(p2Id);
        a.setUsagePercent(30);
        a.setTentative(false);
        assignmentRepository.save(a);

        YearMonth from = YearMonth.of(2026, 7);
        YearMonth to = YearMonth.of(2026, 7);

        List<ArchitectUsageDto> result = usageService.computeUsage(from, to, null, null);
        MonthUsageDto july = result.get(0).months().get(YearMonth.of(2026, 7));

        assertEquals(55, july.total());
        assertEquals(2, july.assignments().size());
    }

    @Test
    void computeUsage_filterByCountry() {
        Architect nzArchitect = new Architect();
        nzArchitect.setFirstName("Bob");
        nzArchitect.setLastName("Jones");
        nzArchitect.setCountry("New Zealand");
        architectRepository.save(nzArchitect);

        YearMonth from = YearMonth.of(2026, 6);
        YearMonth to = YearMonth.of(2026, 6);

        List<ArchitectUsageDto> auOnly = usageService.computeUsage(from, to, "Australia", null);
        assertEquals(1, auOnly.size());
        assertEquals("Alice Smith", auOnly.get(0).architectName());

        List<ArchitectUsageDto> nzOnly = usageService.computeUsage(from, to, "New Zealand", null);
        assertEquals(1, nzOnly.size());
        assertEquals("Bob Jones", nzOnly.get(0).architectName());
    }
}
```

- [ ] **Step 5: Run backend tests**

```bash
cd backend && ./gradlew test
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/org/lolobored/tm/architect/ArchitectControllerTest.java backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java backend/src/test/java/org/lolobored/tm/customer/CustomerControllerTest.java backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java
git commit -m "test: update all backend tests to use string country/city instead of region FK"
```

---

## Task 6: Update frontend types and API client

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1: Update types**

Replace the full contents of `frontend/src/types/index.ts`:

```typescript
export type ProjectStatus = 'ACTIVE' | 'POTENTIAL'

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

export interface Project {
  id: number
  customerId: number
  name: string
  startDate?: string
  durationMonths: number
  defaultUsagePercent: number
  status: ProjectStatus
  officialDate?: string
  plausibleDate?: string
}

export interface Assignment {
  id: number
  architectId: number
  projectId: number
  usagePercent?: number
  tentative: boolean
  startMonth?: string
  endMonth?: string
}

export interface AssignmentUsage {
  projectId: number
  projectName: string
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
```

- [ ] **Step 2: Update API client**

Replace the full contents of `frontend/src/api/client.ts`:

```typescript
import axios from 'axios'
import type { Architect, Customer, Project, Assignment, ArchitectUsage } from '@/types'

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
}

export const projectApi = {
  list: (params?: { customerId?: number; status?: string }) =>
    api.get<Project[]>('/projects', { params }).then(r => r.data),
  get: (id: number) => api.get<Project>(`/projects/${id}`).then(r => r.data),
  create: (data: Omit<Project, 'id'>) => api.post<Project>('/projects', data).then(r => r.data),
  update: (id: number, data: Omit<Project, 'id'>) => api.put<Project>(`/projects/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/projects/${id}`),
  activate: (id: number) => api.patch<Project>(`/projects/${id}/activate`).then(r => r.data),
}

export const assignmentApi = {
  list: (params?: { architectId?: number; projectId?: number }) =>
    api.get<Assignment[]>('/assignments', { params }).then(r => r.data),
  create: (data: Omit<Assignment, 'id'>) => api.post<Assignment>('/assignments', data).then(r => r.data),
  update: (id: number, data: Omit<Assignment, 'id'>) => api.put<Assignment>(`/assignments/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/assignments/${id}`),
}

export const usageApi = {
  get: (from: string, to: string, country?: string, architectId?: number) =>
    api.get<ArchitectUsage[]>('/usage', { params: { from, to, country, architectId } }).then(r => r.data),
}
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/types/index.ts src/api/client.ts
git commit -m "feat: update frontend types and API client — remove region, use string country/city"
```

---

## Task 7: Create geo store and delete refdata store

**Files:**
- Create: `frontend/src/stores/geo.ts`
- Delete: `frontend/src/stores/refdata.ts`

- [ ] **Step 1: Create geo store**

Create `frontend/src/stores/geo.ts`:

```typescript
import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

export const useGeoStore = defineStore('geo', () => {
  const countries = ref<string[]>([])
  const citiesByCountry = ref<Record<string, string[]>>({})
  const loadingCountries = ref(false)
  const loadingCities = ref(false)

  async function fetchCountries() {
    if (countries.value.length > 0) return
    loadingCountries.value = true
    try {
      const response = await axios.get<Array<{ name: { common: string } }>>(
        'https://restcountries.com/v3.1/all?fields=name'
      )
      countries.value = response.data
        .map(c => c.name.common)
        .sort((a, b) => a.localeCompare(b))
    } catch {
      countries.value = []
    } finally {
      loadingCountries.value = false
    }
  }

  async function fetchCities(country: string) {
    if (citiesByCountry.value[country]) return
    loadingCities.value = true
    try {
      const response = await axios.post<{ data: string[] }>(
        'https://countriesnow.space/api/v0.1/countries/cities',
        { country }
      )
      citiesByCountry.value[country] = response.data.data.sort((a, b) => a.localeCompare(b))
    } catch {
      citiesByCountry.value[country] = []
    } finally {
      loadingCities.value = false
    }
  }

  function getCities(country: string): string[] {
    return citiesByCountry.value[country] ?? []
  }

  return { countries, citiesByCountry, loadingCountries, loadingCities, fetchCountries, fetchCities, getCities }
})
```

- [ ] **Step 2: Delete refdata store**

```bash
rm frontend/src/stores/refdata.ts
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/stores/geo.ts && git add -A src/stores/refdata.ts
git commit -m "feat: add geo store with external API lookups, remove refdata store"
```

---

## Task 8: Update usage store

**Files:**
- Modify: `frontend/src/stores/usage.ts`

- [ ] **Step 1: Update usage store**

Replace the full contents of `frontend/src/stores/usage.ts`:

```typescript
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { usageApi } from '@/api/client'
import type { ArchitectUsage } from '@/types'

export const useUsageStore = defineStore('usage', () => {
  const usageData = ref<ArchitectUsage[]>([])
  const loading = ref(false)

  async function fetchUsage(from: string, to: string, country?: string, architectId?: number) {
    loading.value = true
    try {
      usageData.value = await usageApi.get(from, to, country, architectId)
    } finally {
      loading.value = false
    }
  }

  return { usageData, loading, fetchUsage }
})
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/stores/usage.ts
git commit -m "feat: update usage store to accept country string instead of regionId"
```

---

## Task 9: Update ArchitectForm component

**Files:**
- Modify: `frontend/src/components/ArchitectForm.vue`

- [ ] **Step 1: Replace ArchitectForm**

Replace the full contents of `frontend/src/components/ArchitectForm.vue`:

```vue
<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useGeoStore } from '@/stores/geo'
import type { Architect } from '@/types'

const props = defineProps<{ architect?: Architect }>()
const emit = defineEmits<{
  submit: [data: Omit<Architect, 'id'>, photo?: File]
  cancel: []
}>()

const geo = useGeoStore()

const form = reactive({
  firstName: props.architect?.firstName ?? '',
  lastName: props.architect?.lastName ?? '',
  email: props.architect?.email ?? '',
  country: props.architect?.country ?? '',
  city: props.architect?.city ?? '',
})

const photoFile = ref<File | undefined>()

const availableCities = computed(() =>
  form.country ? geo.getCities(form.country) : []
)

watch(() => form.country, (newCountry) => {
  if (newCountry) {
    geo.fetchCities(newCountry)
  }
  if (form.city && !availableCities.value.includes(form.city)) {
    form.city = ''
  }
})

onMounted(() => {
  geo.fetchCountries()
  if (form.country) {
    geo.fetchCities(form.country)
  }
})

function onPhotoChange(event: Event) {
  const input = event.target as HTMLInputElement
  photoFile.value = input.files?.[0]
}

function onSubmit() {
  emit('submit', {
    firstName: form.firstName,
    lastName: form.lastName,
    email: form.email || undefined,
    country: form.country || undefined,
    city: form.city || undefined,
  }, photoFile.value)
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="architect-form">
    <div class="form-row">
      <label>First Name</label>
      <input v-model="form.firstName" required />
    </div>
    <div class="form-row">
      <label>Last Name</label>
      <input v-model="form.lastName" required />
    </div>
    <div class="form-row">
      <label>Email</label>
      <input v-model="form.email" type="email" />
    </div>
    <div class="form-row">
      <label>Country</label>
      <select v-model="form.country">
        <option value="">None</option>
        <option v-for="c in geo.countries" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>
    <div class="form-row">
      <label>City</label>
      <select v-model="form.city" :disabled="!form.country || geo.loadingCities">
        <option value="">{{ geo.loadingCities ? 'Loading...' : 'None' }}</option>
        <option v-for="c in availableCities" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>
    <div class="form-row">
      <label>Photo</label>
      <input type="file" accept="image/*" @change="onPhotoChange" />
    </div>
    <div class="form-actions">
      <button type="submit" class="primary">Save</button>
      <button type="button" @click="emit('cancel')">Cancel</button>
    </div>
  </form>
</template>

<style scoped>
.architect-form { display: flex; flex-direction: column; gap: 0.75rem; max-width: 400px; }
.form-row { display: flex; flex-direction: column; gap: 0.25rem; }
.form-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
</style>
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/components/ArchitectForm.vue
git commit -m "feat: update ArchitectForm to use geo store with external country/city APIs"
```

---

## Task 10: Update CustomerForm component

**Files:**
- Modify: `frontend/src/components/CustomerForm.vue`

- [ ] **Step 1: Replace CustomerForm**

Replace the full contents of `frontend/src/components/CustomerForm.vue`:

```vue
<script setup lang="ts">
import { reactive, computed, onMounted, watch } from 'vue'
import { useGeoStore } from '@/stores/geo'
import type { Customer } from '@/types'

const props = defineProps<{ customer?: Customer }>()
const emit = defineEmits<{ submit: [data: Omit<Customer, 'id'>]; cancel: [] }>()

const geo = useGeoStore()

const form = reactive({
  name: props.customer?.name ?? '',
  country: props.customer?.country ?? '',
  city: props.customer?.city ?? '',
})

const availableCities = computed(() =>
  form.country ? geo.getCities(form.country) : []
)

watch(() => form.country, (newCountry) => {
  if (newCountry) {
    geo.fetchCities(newCountry)
  }
  if (form.city && !availableCities.value.includes(form.city)) {
    form.city = ''
  }
})

onMounted(() => {
  geo.fetchCountries()
  if (form.country) {
    geo.fetchCities(form.country)
  }
})

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
    <div class="form-row"><label>Name</label><input v-model="form.name" required /></div>
    <div class="form-row">
      <label>Country</label>
      <select v-model="form.country">
        <option value="">None</option>
        <option v-for="c in geo.countries" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>
    <div class="form-row">
      <label>City</label>
      <select v-model="form.city" :disabled="!form.country || geo.loadingCities">
        <option value="">{{ geo.loadingCities ? 'Loading...' : 'None' }}</option>
        <option v-for="c in availableCities" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>
    <div class="form-actions">
      <button type="submit" class="primary">Save</button>
      <button type="button" @click="emit('cancel')">Cancel</button>
    </div>
  </form>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 0.75rem; max-width: 400px; }
.form-row { display: flex; flex-direction: column; gap: 0.25rem; }
.form-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
</style>
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/components/CustomerForm.vue
git commit -m "feat: update CustomerForm to use geo store with external country/city APIs"
```

---

## Task 11: Update ArchitectsView

**Files:**
- Modify: `frontend/src/views/ArchitectsView.vue`

- [ ] **Step 1: Replace ArchitectsView**

Replace the full contents of `frontend/src/views/ArchitectsView.vue`:

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useArchitectsStore } from '@/stores/architects'
import { useGeoStore } from '@/stores/geo'
import { architectApi } from '@/api/client'
import ArchitectForm from '@/components/ArchitectForm.vue'
import type { Architect } from '@/types'

const store = useArchitectsStore()
const geo = useGeoStore()
const countryFilter = ref('')
const showForm = ref(false)
const editingArchitect = ref<Architect | undefined>()
const photoVersion = ref(0)

const filtered = computed(() => {
  if (!countryFilter.value) return store.architects
  return store.architects.filter(a => a.country === countryFilter.value)
})

const usedCountries = computed(() => {
  const set = new Set(store.architects.map(a => a.country).filter(Boolean) as string[])
  return [...set].sort()
})

onMounted(() => {
  store.fetchAll()
  geo.fetchCountries()
})

function openCreate() {
  editingArchitect.value = undefined
  showForm.value = true
}

function openEdit(architect: Architect) {
  editingArchitect.value = architect
  showForm.value = true
}

async function onSubmit(data: Omit<Architect, 'id'>, photo?: File) {
  let architect: Architect
  if (editingArchitect.value) {
    architect = await store.update(editingArchitect.value.id, data)
  } else {
    architect = await store.create(data)
  }
  if (photo) {
    await architectApi.uploadPhoto(architect.id, photo)
    photoVersion.value++
  }
  showForm.value = false
}

async function onDelete(id: number) {
  if (confirm('Delete this architect?')) {
    await store.remove(id)
  }
}

function locationDisplay(a: Architect): string {
  const parts = [a.city, a.country].filter(Boolean)
  return parts.join(', ') || '-'
}
</script>

<template>
  <div>
    <div class="header">
      <h1>Architects</h1>
      <div class="controls">
        <select v-model="countryFilter" data-testid="country-filter">
          <option value="">All countries</option>
          <option v-for="c in usedCountries" :key="c" :value="c">{{ c }}</option>
        </select>
        <button class="primary" @click="openCreate">+ Add Architect</button>
      </div>
    </div>

    <div v-if="showForm" class="form-overlay">
      <div class="form-panel">
        <h2>{{ editingArchitect ? 'Edit' : 'Add' }} Architect</h2>
        <ArchitectForm :architect="editingArchitect" @submit="onSubmit" @cancel="showForm = false" />
      </div>
    </div>

    <table>
      <thead>
        <tr><th>Photo</th><th>Name</th><th>Email</th><th>Country</th><th>Location</th><th>Actions</th></tr>
      </thead>
      <tbody>
        <tr v-for="a in filtered" :key="a.id">
          <td class="photo-cell">
            <img :src="`${architectApi.photoUrl(a.id)}?v=${photoVersion}`"
              class="architect-photo"
              @error="($event.target as HTMLImageElement).style.display = 'none'" />
          </td>
          <td>{{ a.firstName }} {{ a.lastName }}</td>
          <td>{{ a.email ?? '-' }}</td>
          <td>{{ a.country ?? '-' }}</td>
          <td>{{ locationDisplay(a) }}</td>
          <td>
            <button @click="openEdit(a)">Edit</button>
            <button class="danger" @click="onDelete(a.id)">Delete</button>
          </td>
        </tr>
        <tr v-if="filtered.length === 0">
          <td colspan="6" style="text-align: center; color: #94a3b8;">No architects found</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.controls { display: flex; gap: 0.5rem; }
.form-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; z-index: 100; }
.form-panel { background: #fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); }
.form-panel h2 { margin-bottom: 1rem; }
td button + button { margin-left: 0.25rem; }
.photo-cell { width: 56px; }
.architect-photo { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; border: 2px solid #e2e8f0; }
</style>
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/views/ArchitectsView.vue
git commit -m "feat: update ArchitectsView — replace region filter with country filter"
```

---

## Task 12: Update UsageTimelineView

**Files:**
- Modify: `frontend/src/views/UsageTimelineView.vue`

- [ ] **Step 1: Replace UsageTimelineView**

Replace the full contents of `frontend/src/views/UsageTimelineView.vue`:

```vue
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUsageStore } from '@/stores/usage'
import { useProjectsStore } from '@/stores/projects'
import { useCustomersStore } from '@/stores/customers'
import { useAssignmentsStore } from '@/stores/assignments'
import { useGeoStore } from '@/stores/geo'
import { assignmentApi } from '@/api/client'
import TimelineGrid from '@/components/TimelineGrid.vue'
import ProjectSidebar from '@/components/ProjectSidebar.vue'

const usageStore = useUsageStore()
const projectStore = useProjectsStore()
const customerStore = useCustomersStore()
const assignmentStore = useAssignmentsStore()
const geo = useGeoStore()

const countryFilter = ref('')
const nameFilter = ref('')

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
  let data = usageStore.usageData
  if (nameFilter.value) {
    const search = nameFilter.value.toLowerCase()
    data = data.filter(a => a.architectName.toLowerCase().includes(search))
  }
  return data
})

async function loadData() {
  await Promise.all([
    usageStore.fetchUsage(fromMonth.value, toMonth.value, countryFilter.value || undefined),
    projectStore.fetchAll(),
    customerStore.fetchAll(),
    geo.fetchCountries(),
  ])
}

onMounted(loadData)
watch([countryFilter, fromMonth, monthCount], loadData)

async function onDrop(
  architectId: number,
  month: string,
  data: { projectId: number; tentative: boolean; defaultUsagePercent: number }
) {
  await assignmentStore.create({
    architectId,
    projectId: data.projectId,
    tentative: data.tentative,
    startMonth: month,
  })
  await loadData()
}

function subtractMonth(ym: string): string {
  return addMonths(ym, -1)
}

async function onUnassign(architectId: number, projectId: number, projectName: string, month: string) {
  if (!confirm(`Remove "${projectName}" from this architect starting ${month}? Previous months will be kept.`)) return
  const allForArchitect = await assignmentApi.list({ architectId })
  const matching = allForArchitect.filter(a => a.projectId === projectId)
  for (const a of matching) {
    const currentEnd = a.endMonth
    const newEnd = subtractMonth(month)
    if (currentEnd && currentEnd < newEnd) continue
    const usageData = usageStore.usageData.find(u => u.architectId === architectId)
    const firstMonth = months.value.find(m => {
      const md = usageData?.months[m]
      return md?.assignments.some(asg => asg.projectId === projectId)
    })
    if (firstMonth && month <= firstMonth) {
      await assignmentApi.delete(a.id)
    } else {
      await assignmentApi.update(a.id, { ...a, endMonth: newEnd })
    }
  }
  await loadData()
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
            <option v-for="c in geo.countries" :key="c" :value="c">{{ c }}</option>
          </select>
          <label>From: <input v-model="fromMonth" type="month" /></label>
          <label>Months:
            <select v-model.number="monthCount">
              <option :value="3">3</option>
              <option :value="6">6</option>
              <option :value="9">9</option>
              <option :value="12">12</option>
            </select>
          </label>
        </div>
      </div>
      <TimelineGrid :usage-data="filteredUsage" :months="months" @drop="onDrop"
        @unassign="onUnassign" />
    </div>
    <ProjectSidebar :projects="projectStore.projects" :customers="customerStore.customers" />
  </div>
</template>

<style scoped>
.timeline-view { display: flex; gap: 0; height: calc(100vh - 3rem); }
.main-area { flex: 1; min-width: 0; overflow: hidden; display: flex; flex-direction: column; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; flex-shrink: 0; }
.controls { display: flex; gap: 0.5rem; align-items: center; }
.controls label { display: flex; align-items: center; gap: 0.25rem; font-size: 0.85rem; }
</style>
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/views/UsageTimelineView.vue
git commit -m "feat: update UsageTimelineView — replace region filter with country filter"
```

---

## Task 13: Update CustomersProjectsView and TimelineGrid

**Files:**
- Modify: `frontend/src/views/CustomersProjectsView.vue`
- Modify: `frontend/src/components/TimelineGrid.vue`

- [ ] **Step 1: Update CustomersProjectsView**

In `frontend/src/views/CustomersProjectsView.vue`, make these changes:

Remove the refdata import and usage. Replace the script section with:

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useCustomersStore } from '@/stores/customers'
import { useProjectsStore } from '@/stores/projects'
import CustomerForm from '@/components/CustomerForm.vue'
import ProjectForm from '@/components/ProjectForm.vue'
import type { Customer, Project } from '@/types'

const customerStore = useCustomersStore()
const projectStore = useProjectsStore()

const expandedCustomers = ref<Set<number>>(new Set())
const showCustomerForm = ref(false)
const editingCustomer = ref<Customer | undefined>()
const showProjectForm = ref(false)
const projectFormCustomerId = ref<number>(0)
const editingProject = ref<Project | undefined>()

onMounted(async () => {
  await Promise.all([customerStore.fetchAll(), projectStore.fetchAll()])
})

function toggleExpand(id: number) {
  if (expandedCustomers.value.has(id)) expandedCustomers.value.delete(id)
  else expandedCustomers.value.add(id)
}

function openAddCustomer() { editingCustomer.value = undefined; showCustomerForm.value = true }
function openEditCustomer(c: Customer) { editingCustomer.value = c; showCustomerForm.value = true }

async function onCustomerSubmit(data: Omit<Customer, 'id'>) {
  if (editingCustomer.value) await customerStore.update(editingCustomer.value.id, data)
  else await customerStore.create(data)
  showCustomerForm.value = false
}

async function onDeleteCustomer(id: number) {
  if (confirm('Delete this customer and all its projects?')) await customerStore.remove(id)
}

function openAddProject(customerId: number) {
  projectFormCustomerId.value = customerId
  editingProject.value = undefined
  showProjectForm.value = true
}

function openEditProject(project: Project) {
  projectFormCustomerId.value = project.customerId
  editingProject.value = project
  showProjectForm.value = true
}

async function onProjectSubmit(data: Omit<Project, 'id'>) {
  if (editingProject.value) await projectStore.update(editingProject.value.id, data)
  else await projectStore.create(data)
  showProjectForm.value = false
}

async function onDeleteProject(id: number) {
  if (confirm('Delete this project?')) await projectStore.remove(id)
}
</script>
```

In the template, replace the location span to use direct string fields:

```html
          <span v-if="customer.country || customer.city" class="location">
            {{ [customer.city, customer.country].filter(Boolean).join(', ') }}
          </span>
```

- [ ] **Step 2: Update TimelineGrid**

In `frontend/src/components/TimelineGrid.vue`, change the line that displays `architect.region` (line 73) to show `architect.country`:

Change:
```html
                <div class="architect-region">{{ architect.region }}</div>
```
To:
```html
                <div class="architect-region">{{ architect.country }}</div>
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/views/CustomersProjectsView.vue src/components/TimelineGrid.vue
git commit -m "feat: update CustomersProjectsView and TimelineGrid — use string country/city"
```

---

## Task 14: Remove AdminView, route, and nav link

**Files:**
- Delete: `frontend/src/views/AdminView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/components/AppLayout.vue`

- [ ] **Step 1: Delete AdminView**

```bash
rm frontend/src/views/AdminView.vue
```

- [ ] **Step 2: Remove admin route from router**

In `frontend/src/router/index.ts`, remove the line:

```typescript
    { path: '/admin', name: 'admin', component: () => import('@/views/AdminView.vue') },
```

- [ ] **Step 3: Remove admin nav link from AppLayout**

In `frontend/src/components/AppLayout.vue`, remove this block from the template:

```html
        <li><RouterLink to="/admin" :title="collapsed ? 'Admin' : undefined">
          <span class="nav-icon">⚙️</span><span v-show="!collapsed" class="nav-label">Admin</span>
        </RouterLink></li>
```

- [ ] **Step 4: Commit**

```bash
cd frontend && git add -A src/views/AdminView.vue src/router/index.ts src/components/AppLayout.vue
git commit -m "feat: remove AdminView, admin route, and admin nav link"
```

---

## Task 15: Update frontend tests

**Files:**
- Modify: `frontend/src/__tests__/ArchitectsView.spec.ts`
- Modify: `frontend/src/__tests__/CustomersProjectsView.spec.ts`
- Modify: `frontend/src/__tests__/UsageTimelineView.spec.ts`

- [ ] **Step 1: Update ArchitectsView.spec.ts**

Replace the full contents of `frontend/src/__tests__/ArchitectsView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ArchitectsView from '@/views/ArchitectsView.vue'

vi.mock('@/api/client', () => ({
  architectApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, firstName: 'Alice', lastName: 'Smith', email: 'alice@test.com', country: 'Australia' },
      { id: 2, firstName: 'Bob', lastName: 'Jones', country: 'New Zealand' },
    ]),
    create: vi.fn().mockResolvedValue({ id: 3, firstName: 'Charlie', lastName: 'Brown', country: 'Australia' }),
    delete: vi.fn().mockResolvedValue(undefined),
    photoUrl: (id: number) => `/api/architects/${id}/photo`,
  },
  usageApi: {
    get: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('@/stores/geo', () => ({
  useGeoStore: () => ({
    countries: ['Australia', 'New Zealand'],
    fetchCountries: vi.fn(),
    fetchCities: vi.fn(),
    getCities: vi.fn().mockReturnValue([]),
    loadingCities: false,
  }),
}))

describe('ArchitectsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders architect list on mount', async () => {
    const wrapper = mount(ArchitectsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Smith')
    expect(wrapper.text()).toContain('Bob')
    expect(wrapper.text()).toContain('Jones')
  })

  it('filters by country', async () => {
    const wrapper = mount(ArchitectsView)
    await flushPromises()

    const select = wrapper.find('[data-testid="country-filter"]')
    await select.setValue('Australia')

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).not.toContain('Bob')
  })
})
```

- [ ] **Step 2: Update CustomersProjectsView.spec.ts**

Replace the full contents of `frontend/src/__tests__/CustomersProjectsView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CustomersProjectsView from '@/views/CustomersProjectsView.vue'

vi.mock('@/api/client', () => ({
  customerApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, name: 'Acme Corp', country: 'Australia', city: 'Sydney' },
    ]),
    create: vi.fn().mockResolvedValue({ id: 2, name: 'Beta Inc' }),
    delete: vi.fn().mockResolvedValue(undefined),
  },
  projectApi: {
    list: vi.fn().mockResolvedValue([
      {
        id: 10, customerId: 1, name: 'KYC Platform', startDate: '2026-06-01',
        durationMonths: 6, defaultUsagePercent: 20, status: 'ACTIVE',
      },
      {
        id: 11, customerId: 1, name: 'Future Work',
        durationMonths: 3, defaultUsagePercent: 15, status: 'POTENTIAL',
        officialDate: '2026-09-01', plausibleDate: '2026-10-01',
      },
    ]),
    create: vi.fn().mockResolvedValue({ id: 12, name: 'New Project' }),
    delete: vi.fn().mockResolvedValue(undefined),
  },
}))

vi.mock('@/stores/geo', () => ({
  useGeoStore: () => ({
    countries: ['Australia'],
    fetchCountries: vi.fn(),
    fetchCities: vi.fn(),
    getCities: vi.fn().mockReturnValue(['Sydney', 'Melbourne']),
    loadingCities: false,
  }),
}))

describe('CustomersProjectsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders customers with location from string fields', async () => {
    const wrapper = mount(CustomersProjectsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Acme Corp')
    expect(wrapper.text()).toContain('Sydney')
    expect(wrapper.text()).toContain('Australia')
  })

  it('shows project status badges', async () => {
    const wrapper = mount(CustomersProjectsView)
    await flushPromises()

    const expandBtn = wrapper.find('[data-testid="expand-1"]')
    if (expandBtn.exists()) await expandBtn.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('KYC Platform')
    expect(wrapper.text()).toContain('ACTIVE')
  })
})
```

- [ ] **Step 3: Update UsageTimelineView.spec.ts**

Replace the full contents of `frontend/src/__tests__/UsageTimelineView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UsageTimelineView from '@/views/UsageTimelineView.vue'

vi.mock('@/api/client', () => ({
  architectApi: {
    photoUrl: (id: number) => `/api/architects/${id}/photo`,
  },
  usageApi: {
    get: vi.fn().mockResolvedValue([
      {
        architectId: 1,
        architectName: 'Alice Smith',
        country: 'Australia',
        months: {
          '2026-06': {
            total: 40,
            assignments: [
              { projectId: 1, projectName: 'KYC', customerName: 'Acme', usage: 20, tentative: false },
              { projectId: 2, projectName: 'Beta', customerName: 'Beta Corp', usage: 20, tentative: true },
            ],
          },
          '2026-07': {
            total: 20,
            assignments: [
              { projectId: 1, projectName: 'KYC', customerName: 'Acme', usage: 20, tentative: false },
            ],
          },
        },
      },
    ]),
  },
  projectApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, customerId: 1, name: 'KYC', status: 'ACTIVE', durationMonths: 6, defaultUsagePercent: 20 },
      { id: 2, customerId: 2, name: 'Beta', status: 'POTENTIAL', durationMonths: 3, defaultUsagePercent: 20 },
    ]),
  },
  assignmentApi: {
    create: vi.fn().mockResolvedValue({ id: 99 }),
    delete: vi.fn().mockResolvedValue(undefined),
  },
  customerApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, name: 'Acme' },
      { id: 2, name: 'Beta Corp' },
    ]),
  },
}))

vi.mock('@/stores/geo', () => ({
  useGeoStore: () => ({
    countries: ['Australia', 'New Zealand'],
    fetchCountries: vi.fn(),
    fetchCities: vi.fn(),
    getCities: vi.fn().mockReturnValue([]),
    loadingCities: false,
  }),
}))

describe('UsageTimelineView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders architect rows with usage data', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice Smith')
    expect(wrapper.text()).toContain('KYC')
  })

  it('shows total usage in cells', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    expect(wrapper.text()).toContain('40%')
    expect(wrapper.text()).toContain('20%')
  })

  it('has country filter', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const select = wrapper.find('[data-testid="country-filter"]')
    expect(select.exists()).toBe(true)
  })
})
```

- [ ] **Step 4: Run frontend tests**

```bash
cd frontend && npx vitest run
```

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/__tests__/ArchitectsView.spec.ts src/__tests__/CustomersProjectsView.spec.ts src/__tests__/UsageTimelineView.spec.ts
git commit -m "test: update frontend tests — use country strings instead of region IDs, mock geo store"
```

---

## Task 16: Run full test suite and verify

- [ ] **Step 1: Run backend tests**

```bash
cd backend && ./gradlew test
```

Expected: All tests pass.

- [ ] **Step 2: Run frontend tests**

```bash
cd frontend && npx vitest run
```

Expected: All tests pass.

- [ ] **Step 3: Run frontend type check**

```bash
cd frontend && npx vue-tsc --build --force
```

Expected: No type errors.

- [ ] **Step 4: Start the app and verify in browser**

Restart the backend (it needs to pick up the new migration):
```bash
# Stop and restart the backend
cd backend && ./gradlew bootRun
```

Open http://localhost:5173 and verify:
1. Architects page: country filter works, country/city columns display correctly
2. Add/edit architect: country dropdown loads from REST Countries API, city dropdown loads on country selection
3. Customers page: country/city display as strings
4. Usage Timeline: country filter replaces old region filter, architect rows show country
5. Admin nav link is gone

- [ ] **Step 5: Commit any fixes if needed**
