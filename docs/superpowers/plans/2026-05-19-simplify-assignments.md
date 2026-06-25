# Simplify Assignments to Per-Month Model — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace range-based assignments with a per-month model, remove project duration/dates complexity, delete the usage override system, rename POTENTIAL→UPCOMING, and make the sidebar filter match customer names.

**Architecture:** Each assignment becomes a single row for one architect + one project + one month. Projects lose `durationMonths`, `officialDate`, `plausibleDate`; UPCOMING projects keep `startDate` (required), ACTIVE projects have null `startDate`. The `assignment_usage_override` table is removed entirely — per-month usage lives directly on each assignment's `usagePercent`. The sidebar project filter becomes a dual-field search against project name and customer name.

**Tech Stack:** Spring Boot 3 / Java 21 / JPA / Liquibase / PostgreSQL, Vue 3 / TypeScript / Pinia / Vite / Vitest

---

### Task 1: Database Migration — Simplify Assignment and Project Tables

**Files:**
- Create: `backend/src/main/resources/db/changelog/011-simplify-assignments.yaml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml:20-21`

- [ ] **Step 1: Create the migration file**

Create `backend/src/main/resources/db/changelog/011-simplify-assignments.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 11a
      author: laurent
      comment: "Add month column to assignment"
      changes:
        - addColumn:
            tableName: assignment
            columns:
              - column:
                  name: month
                  type: varchar(7)
                  constraints:
                    nullable: true
  - changeSet:
      id: 11b
      author: laurent
      comment: "Expand range-based assignments into per-month rows and populate month"
      changes:
        - sql:
            comment: "Insert expanded rows for assignments that span multiple months (end_month > start_month)"
            sql: >
              INSERT INTO assignment (architect_id, project_id, usage_percent, tentative, month)
              SELECT a.architect_id, a.project_id, a.usage_percent, a.tentative,
                     TO_CHAR(gs, 'YYYY-MM')
              FROM assignment a,
                   generate_series(
                     (a.start_month || '-01')::date + interval '1 month',
                     (a.end_month || '-01')::date,
                     interval '1 month'
                   ) gs
              WHERE a.start_month IS NOT NULL
                AND a.end_month IS NOT NULL
                AND a.end_month > a.start_month
        - sql:
            comment: "Set month = start_month for the original rows"
            sql: "UPDATE assignment SET month = start_month WHERE start_month IS NOT NULL AND month IS NULL"
        - sql:
            comment: "Fallback for rows with no start_month at all"
            sql: "UPDATE assignment SET month = '2026-01' WHERE month IS NULL"
  - changeSet:
      id: 11c
      author: laurent
      comment: "Make month non-nullable and drop old columns"
      changes:
        - addNotNullConstraint:
            tableName: assignment
            columnName: month
            columnDataType: varchar(7)
        - dropColumn:
            tableName: assignment
            columnName: start_month
        - dropColumn:
            tableName: assignment
            columnName: end_month
  - changeSet:
      id: 11d
      author: laurent
      comment: "Update unique constraint to include month"
      changes:
        - dropUniqueConstraint:
            tableName: assignment
            constraintName: uq_assignment_architect_project
        - addUniqueConstraint:
            tableName: assignment
            columnNames: architect_id, project_id, month
            constraintName: uq_assignment_architect_project_month
  - changeSet:
      id: 11e
      author: laurent
      comment: "Drop assignment_usage_override table"
      changes:
        - dropTable:
            tableName: assignment_usage_override
  - changeSet:
      id: 11f
      author: laurent
      comment: "Drop project date/duration fields and rename POTENTIAL to UPCOMING"
      changes:
        - dropColumn:
            tableName: project
            columnName: duration_months
        - dropColumn:
            tableName: project
            columnName: official_date
        - dropColumn:
            tableName: project
            columnName: plausible_date
        - sql:
            sql: "UPDATE project SET status = 'UPCOMING' WHERE status = 'POTENTIAL'"
```

- [ ] **Step 2: Register the migration in the master changelog**

Add to the end of `backend/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/011-simplify-assignments.yaml
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/changelog/011-simplify-assignments.yaml backend/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "chore: add migration to simplify assignments to per-month model"
```

---

### Task 2: Backend — Update Project Entity and Status Enum

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/project/ProjectStatus.java:1-5`
- Modify: `backend/src/main/java/org/lolobored/tm/project/Project.java:1-58`

- [ ] **Step 1: Write the failing test**

Add a new test to `backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java`:

```java
@Test
void createUpcomingProject_requiresStartDate() throws Exception {
    String json = """
            {"customerId": %d, "name": "Upcoming Project",
             "defaultUsagePercent": 20, "status": "UPCOMING",
             "startDate": "2026-09-01"}
            """.formatted(customerId);

    mockMvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("UPCOMING"))
            .andExpect(jsonPath("$.startDate").value("2026-09-01"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.project.ProjectControllerTest.createUpcomingProject_requiresStartDate" --info 2>&1 | tail -20`

Expected: FAIL — `UPCOMING` is not a valid enum value yet.

- [ ] **Step 3: Update ProjectStatus enum**

Replace the content of `backend/src/main/java/org/lolobored/tm/project/ProjectStatus.java`:

```java
package org.lolobored.tm.project;

public enum ProjectStatus {
    ACTIVE, UPCOMING
}
```

- [ ] **Step 4: Update Project entity — remove date/duration fields**

Replace the content of `backend/src/main/java/org/lolobored/tm/project/Project.java`:

```java
package org.lolobored.tm.project;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private LocalDate startDate;

    @Min(0)
    @Column(nullable = false)
    private int defaultUsagePercent;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public int getDefaultUsagePercent() { return defaultUsagePercent; }
    public void setDefaultUsagePercent(int defaultUsagePercent) { this.defaultUsagePercent = defaultUsagePercent; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
}
```

- [ ] **Step 5: Run the new test to verify it passes**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.project.ProjectControllerTest.createUpcomingProject_requiresStartDate" --info 2>&1 | tail -20`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/project/
git commit -m "feat: rename POTENTIAL to UPCOMING, remove duration and date fields from Project"
```

---

### Task 3: Backend — Update Assignment Entity and Remove Override System

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java:1-44`
- Delete: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentUsageOverride.java`
- Delete: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentUsageOverrideRepository.java`
- Delete: `backend/src/main/java/org/lolobored/tm/assignment/UsageOverrideController.java`

- [ ] **Step 1: Write the failing test**

Add a new test to `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`:

```java
@Test
void createAssignment_withMonth() throws Exception {
    String json = """
            {"architectId": %d, "projectId": %d, "usagePercent": 25, "tentative": false, "month": "2026-06"}
            """.formatted(architectId, projectId);

    mockMvc.perform(post("/api/assignments")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.month").value("2026-06"))
            .andExpect(jsonPath("$.usagePercent").value(25));
}

@Test
void createAssignment_duplicateMonthReturns409() throws Exception {
    String json = """
            {"architectId": %d, "projectId": %d, "tentative": false, "month": "2026-06"}
            """.formatted(architectId, projectId);

    mockMvc.perform(post("/api/assignments")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated());

    mockMvc.perform(post("/api/assignments")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isConflict());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.assignment.AssignmentControllerTest.createAssignment_withMonth" --tests "org.lolobored.tm.assignment.AssignmentControllerTest.createAssignment_duplicateMonthReturns409" --info 2>&1 | tail -20`

Expected: FAIL — `month` field does not exist on Assignment yet.

- [ ] **Step 3: Update Assignment entity**

Replace the content of `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java`:

```java
package org.lolobored.tm.assignment;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"architect_id", "project_id", "month"}))
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "architect_id", nullable = false)
    private Long architectId;

    @NotNull
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    private Integer usagePercent;

    @Column(nullable = false)
    private boolean tentative;

    @NotNull
    @Column(nullable = false, length = 7)
    private String month;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArchitectId() { return architectId; }
    public void setArchitectId(Long architectId) { this.architectId = architectId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Integer getUsagePercent() { return usagePercent; }
    public void setUsagePercent(Integer usagePercent) { this.usagePercent = usagePercent; }
    public boolean isTentative() { return tentative; }
    public void setTentative(boolean tentative) { this.tentative = tentative; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
}
```

- [ ] **Step 4: Delete override files**

```bash
rm backend/src/main/java/org/lolobored/tm/assignment/AssignmentUsageOverride.java
rm backend/src/main/java/org/lolobored/tm/assignment/AssignmentUsageOverrideRepository.java
rm backend/src/main/java/org/lolobored/tm/assignment/UsageOverrideController.java
```

- [ ] **Step 5: Update AssignmentController to handle 409 on duplicate**

Replace the `create` method in `backend/src/main/java/org/lolobored/tm/assignment/AssignmentController.java` (lines 28-32):

```java
@PostMapping
public ResponseEntity<Assignment> create(@Valid @RequestBody Assignment assignment) {
    assignment.setId(null);
    try {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(assignment));
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment already exists for this architect/project/month");
    }
}
```

- [ ] **Step 6: Run the new tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.assignment.AssignmentControllerTest" --info 2>&1 | tail -30`

Expected: PASS for the two new tests. Some existing tests may need the `month` field added — fix in next step.

- [ ] **Step 7: Update existing AssignmentControllerTest to include month field**

Update all JSON payloads in `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java` that create assignments to include `"month": "2026-06"`. For example, `createAndGetAssignment`:

```java
@Test
void createAndGetAssignment() throws Exception {
    String json = """
            {"architectId": %d, "projectId": %d, "usagePercent": 25, "tentative": false, "month": "2026-06"}
            """.formatted(architectId, projectId);

    String response = mockMvc.perform(post("/api/assignments")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.architectId").value(architectId))
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.usagePercent").value(25))
            .andExpect(jsonPath("$.month").value("2026-06"))
            .andReturn().getResponse().getContentAsString();

    Long id = objectMapper.readTree(response).get("id").asLong();
    mockMvc.perform(get("/api/assignments/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usagePercent").value(25));
}
```

Update `createAssignment_nullUsagePercent_fallsBackToProjectDefault`:

```java
@Test
void createAssignment_nullUsagePercent_fallsBackToProjectDefault() throws Exception {
    String json = """
            {"architectId": %d, "projectId": %d, "tentative": false, "month": "2026-06"}
            """.formatted(architectId, projectId);

    mockMvc.perform(post("/api/assignments")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.usagePercent").isEmpty());
}
```

Update `listAssignments_filterByArchitect`:

```java
@Test
void listAssignments_filterByArchitect() throws Exception {
    String json = """
            {"architectId": %d, "projectId": %d, "tentative": false, "month": "2026-06"}
            """.formatted(architectId, projectId);
    mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json));

    mockMvc.perform(get("/api/assignments?architectId=" + architectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

    mockMvc.perform(get("/api/assignments?architectId=9999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
}
```

Update `deleteAssignment`:

```java
@Test
void deleteAssignment() throws Exception {
    String json = """
            {"architectId": %d, "projectId": %d, "tentative": false, "month": "2026-06"}
            """.formatted(architectId, projectId);

    String response = mockMvc.perform(post("/api/assignments")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    Long id = objectMapper.readTree(response).get("id").asLong();
    mockMvc.perform(delete("/api/assignments/" + id)).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/assignments/" + id)).andExpect(status().isNotFound());
}
```

Also remove `startDate` and `durationMonths` from the project setup in `setUp()`:

```java
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
    project.setDefaultUsagePercent(20);
    project.setStatus(ProjectStatus.ACTIVE);
    projectId = projectRepository.save(project).getId();
}
```

- [ ] **Step 8: Run all assignment tests**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.assignment.AssignmentControllerTest" --info 2>&1 | tail -30`

Expected: All PASS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/assignment/ backend/src/test/java/org/lolobored/tm/assignment/
git commit -m "feat: simplify Assignment to per-month model, remove usage override system"
```

---

### Task 4: Backend — Update ProjectController Activate Endpoint

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/project/ProjectController.java:54-64`
- Modify: `backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java`

- [ ] **Step 1: Write the failing test**

Replace the existing `activateProject` test and add a new one in `backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java`:

```java
@Test
void activateProject_clearsStartDateAndUpdatesAssignments() throws Exception {
    String projectJson = """
            {"customerId": %d, "name": "Future Project",
             "defaultUsagePercent": 20, "status": "UPCOMING",
             "startDate": "2026-09-01"}
            """.formatted(customerId);

    String response = mockMvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON).content(projectJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    Long projectId = objectMapper.readTree(response).get("id").asLong();

    // Create a tentative assignment for this project
    Architect architect = new Architect();
    architect.setFirstName("Bob");
    architect.setLastName("Jones");
    architectRepository.save(architect);

    String assignmentJson = """
            {"architectId": %d, "projectId": %d, "tentative": true, "month": "2026-09"}
            """.formatted(architect.getId(), projectId);
    mockMvc.perform(post("/api/assignments")
                    .contentType(MediaType.APPLICATION_JSON).content(assignmentJson))
            .andExpect(status().isCreated());

    // Activate the project
    mockMvc.perform(patch("/api/projects/" + projectId + "/activate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.startDate").isEmpty());

    // Verify assignment is no longer tentative
    mockMvc.perform(get("/api/assignments?projectId=" + projectId))
            .andExpect(jsonPath("$[0].tentative").value(false));
}
```

You will also need to add imports at the top of the test file:

```java
import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
```

And autowire the repository:

```java
@Autowired private ArchitectRepository architectRepository;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.project.ProjectControllerTest.activateProject_clearsStartDateAndUpdatesAssignments" --info 2>&1 | tail -20`

Expected: FAIL — current activate endpoint doesn't clear startDate or update assignments.

- [ ] **Step 3: Update ProjectController activate endpoint**

Replace the `activate` method in `backend/src/main/java/org/lolobored/tm/project/ProjectController.java` (lines 54-64). Add `AssignmentRepository` as a constructor dependency:

```java
package org.lolobored.tm.project;

import org.lolobored.tm.assignment.AssignmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository repository;
    private final AssignmentRepository assignmentRepository;

    public ProjectController(ProjectRepository repository, AssignmentRepository assignmentRepository) {
        this.repository = repository;
        this.assignmentRepository = assignmentRepository;
    }

    @GetMapping
    public List<Project> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) ProjectStatus status) {
        if (customerId != null && status != null) return repository.findByCustomerIdAndStatus(customerId, status);
        else if (customerId != null) return repository.findByCustomerId(customerId);
        else if (status != null) return repository.findByStatus(status);
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Project> create(@Valid @RequestBody Project project) {
        project.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(project));
    }

    @GetMapping("/{id}")
    public Project get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Project update(@PathVariable Long id, @Valid @RequestBody Project project) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        project.setId(id);
        return repository.save(project);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repository.deleteById(id);
    }

    @PatchMapping("/{id}/activate")
    @Transactional
    public Project activate(@PathVariable Long id) {
        Project project = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        project.setStatus(ProjectStatus.ACTIVE);
        project.setStartDate(null);
        assignmentRepository.findByProjectId(id).forEach(a -> {
            a.setTentative(false);
            assignmentRepository.save(a);
        });
        return repository.save(project);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.project.ProjectControllerTest.activateProject_clearsStartDateAndUpdatesAssignments" --info 2>&1 | tail -20`

Expected: PASS

- [ ] **Step 5: Update existing project tests for new model**

Update `createAndGetProject` — remove `startDate` and `durationMonths`:

```java
@Test
void createAndGetProject() throws Exception {
    String json = """
            {
                "customerId": %d,
                "name": "KYC Platform",
                "defaultUsagePercent": 20,
                "status": "ACTIVE"
            }
            """.formatted(customerId);

    String response = mockMvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("KYC Platform"))
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn().getResponse().getContentAsString();

    Long id = objectMapper.readTree(response).get("id").asLong();
    mockMvc.perform(get("/api/projects/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("KYC Platform"));
}
```

Update `listProjects_filterByStatus` — use UPCOMING instead of POTENTIAL, remove old date fields:

```java
@Test
void listProjects_filterByStatus() throws Exception {
    String active = """
            {"customerId": %d, "name": "Active Project",
             "defaultUsagePercent": 20, "status": "ACTIVE"}
            """.formatted(customerId);
    String upcoming = """
            {"customerId": %d, "name": "Future Project",
             "defaultUsagePercent": 20, "status": "UPCOMING",
             "startDate": "2026-09-01"}
            """.formatted(customerId);

    mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(active));
    mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(upcoming));

    mockMvc.perform(get("/api/projects?status=UPCOMING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Future Project"));
}
```

Remove the old `activateProject` test (replaced by `activateProject_clearsStartDateAndUpdatesAssignments`).

Update `deleteProject` — remove `startDate` and `durationMonths`:

```java
@Test
void deleteProject() throws Exception {
    String json = """
            {"customerId": %d, "name": "To Delete",
             "defaultUsagePercent": 20, "status": "ACTIVE"}
            """.formatted(customerId);

    String response = mockMvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    Long id = objectMapper.readTree(response).get("id").asLong();
    mockMvc.perform(delete("/api/projects/" + id)).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/projects/" + id)).andExpect(status().isNotFound());
}
```

- [ ] **Step 6: Run all project tests**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.project.ProjectControllerTest" --info 2>&1 | tail -30`

Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/project/ backend/src/test/java/org/lolobored/tm/project/
git commit -m "feat: update activate endpoint to clear startDate and flip tentative assignments"
```

---

### Task 5: Backend — Simplify UsageService

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/usage/UsageService.java:1-141`
- Modify: `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java:1-181`

- [ ] **Step 1: Rewrite UsageServiceTest for the new per-month model**

Replace the entire content of `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java`:

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
        activeProject.setDefaultUsagePercent(20);
        activeProject.setStatus(ProjectStatus.ACTIVE);
        Long activeProjectId = projectRepository.save(activeProject).getId();

        Assignment a1 = new Assignment();
        a1.setArchitectId(architectId);
        a1.setProjectId(activeProjectId);
        a1.setUsagePercent(25);
        a1.setTentative(false);
        a1.setMonth("2026-06");
        assignmentRepository.save(a1);

        Assignment a1b = new Assignment();
        a1b.setArchitectId(architectId);
        a1b.setProjectId(activeProjectId);
        a1b.setUsagePercent(25);
        a1b.setTentative(false);
        a1b.setMonth("2026-07");
        assignmentRepository.save(a1b);

        Project upcomingProject = new Project();
        upcomingProject.setCustomerId(customerId);
        upcomingProject.setName("Future Work");
        upcomingProject.setDefaultUsagePercent(15);
        upcomingProject.setStatus(ProjectStatus.UPCOMING);
        Long upcomingProjectId = projectRepository.save(upcomingProject).getId();

        Assignment a2 = new Assignment();
        a2.setArchitectId(architectId);
        a2.setProjectId(upcomingProjectId);
        a2.setTentative(true);
        a2.setMonth("2026-09");
        assignmentRepository.save(a2);
    }

    @Test
    void computeUsage_perMonthAssignments() {
        YearMonth from = YearMonth.of(2026, 6);
        YearMonth to = YearMonth.of(2026, 7);

        List<ArchitectUsageDto> result = usageService.computeUsage(from, to, null, null);

        assertEquals(1, result.size());
        ArchitectUsageDto dto = result.get(0);
        assertEquals("Alice Smith", dto.architectName());

        MonthUsageDto june = dto.months().get(YearMonth.of(2026, 6));
        assertNotNull(june);
        assertEquals(25, june.total());
        assertEquals(1, june.assignments().size());
        assertFalse(june.assignments().get(0).tentative());

        MonthUsageDto july = dto.months().get(YearMonth.of(2026, 7));
        assertNotNull(july);
        assertEquals(25, july.total());
    }

    @Test
    void computeUsage_tentativeAssignment() {
        YearMonth from = YearMonth.of(2026, 9);
        YearMonth to = YearMonth.of(2026, 9);

        List<ArchitectUsageDto> result = usageService.computeUsage(from, to, null, null);

        ArchitectUsageDto dto = result.get(0);
        MonthUsageDto sept = dto.months().get(YearMonth.of(2026, 9));
        assertNotNull(sept);
        assertEquals(15, sept.total());
        assertTrue(sept.assignments().get(0).tentative());
    }

    @Test
    void computeUsage_monthOutsideRange_excluded() {
        YearMonth from = YearMonth.of(2026, 8);
        YearMonth to = YearMonth.of(2026, 8);

        List<ArchitectUsageDto> result = usageService.computeUsage(from, to, null, null);

        ArchitectUsageDto dto = result.get(0);
        MonthUsageDto aug = dto.months().get(YearMonth.of(2026, 8));
        assertNotNull(aug);
        assertEquals(0, aug.total());
        assertTrue(aug.assignments().isEmpty());
    }

    @Test
    void computeUsage_overlapMonth_sumsUsage() {
        Customer c = new Customer();
        c.setName("Beta");
        Long cId = customerRepository.save(c).getId();

        Project p2 = new Project();
        p2.setCustomerId(cId);
        p2.setName("Beta Project");
        p2.setDefaultUsagePercent(20);
        p2.setStatus(ProjectStatus.ACTIVE);
        Long p2Id = projectRepository.save(p2).getId();

        Assignment a = new Assignment();
        a.setArchitectId(architectId);
        a.setProjectId(p2Id);
        a.setUsagePercent(30);
        a.setTentative(false);
        a.setMonth("2026-07");
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

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.usage.UsageServiceTest" --info 2>&1 | tail -30`

Expected: FAIL — UsageService still references removed override classes.

- [ ] **Step 3: Rewrite UsageService**

Replace the entire content of `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`:

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
import org.springframework.stereotype.Service;

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
                String monthStr = month.toString();
                List<AssignmentUsageDto> monthAssignments = new ArrayList<>();

                for (Assignment assignment : assignments) {
                    if (!monthStr.equals(assignment.getMonth())) continue;

                    Project project = projectCache.get(assignment.getProjectId());
                    if (project == null) continue;

                    int usage = assignment.getUsagePercent() != null
                            ? assignment.getUsagePercent()
                            : project.getDefaultUsagePercent();

                    Customer customer = customerCache.get(project.getCustomerId());
                    String customerName = customer != null ? customer.getName() : "";

                    monthAssignments.add(new AssignmentUsageDto(
                            assignment.getId(), project.getId(), project.getName(), customerName,
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
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "org.lolobored.tm.usage.UsageServiceTest" --info 2>&1 | tail -30`

Expected: All PASS

- [ ] **Step 5: Run all backend tests**

Run: `cd backend && ./gradlew test --info 2>&1 | tail -30`

Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/usage/ backend/src/test/java/org/lolobored/tm/usage/
git commit -m "feat: simplify UsageService — per-month lookup, no more date-range calculation"
```

---

### Task 6: Frontend — Update Types, API Client, and Stores

**Files:**
- Modify: `frontend/src/types/index.ts:1-60`
- Modify: `frontend/src/api/client.ts:1-57`
- Modify: `frontend/src/stores/assignments.ts:1-15`

- [ ] **Step 1: Update TypeScript types**

Replace the content of `frontend/src/types/index.ts`:

```typescript
export type ProjectStatus = 'ACTIVE' | 'UPCOMING'

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
  defaultUsagePercent: number
  status: ProjectStatus
}

export interface Assignment {
  id: number
  architectId: number
  projectId: number
  usagePercent?: number
  tentative: boolean
  month: string
}

export interface AssignmentUsage {
  assignmentId: number
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

- [ ] **Step 2: Update API client — remove usageOverrideApi**

Replace the content of `frontend/src/api/client.ts`:

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
  update: (id: number, data: Partial<Assignment>) => api.put<Assignment>(`/assignments/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/assignments/${id}`),
}

export const usageApi = {
  get: (from: string, to: string, country?: string, architectId?: number) =>
    api.get<ArchitectUsage[]>('/usage', { params: { from, to, country, architectId } }).then(r => r.data),
}
```

- [ ] **Step 3: Update assignments store**

Replace the content of `frontend/src/stores/assignments.ts`:

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

- [ ] **Step 4: Run type check**

Run: `cd frontend && npx vue-tsc --noEmit 2>&1 | head -30`

Expected: Type errors in components that still reference old fields — these will be fixed in subsequent tasks.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/client.ts frontend/src/stores/assignments.ts
git commit -m "feat: update frontend types, API client, and stores for per-month model"
```

---

### Task 7: Frontend — Update ProjectForm and ProjectSidebar

**Files:**
- Modify: `frontend/src/components/ProjectForm.vue:1-89`
- Modify: `frontend/src/components/ProjectSidebar.vue:14-19`

- [ ] **Step 1: Update ProjectForm**

Replace the content of `frontend/src/components/ProjectForm.vue`:

```vue
<script setup lang="ts">
import { reactive, computed } from 'vue'
import type { Project, Customer } from '@/types'

const props = defineProps<{ project?: Project; customerId: number; customers: Customer[] }>()
const emit = defineEmits<{ submit: [data: Omit<Project, 'id'>]; cancel: [] }>()

const form = reactive({
  name: props.project?.name ?? '',
  startDate: props.project?.startDate ?? '',
  defaultUsagePercent: props.project?.defaultUsagePercent ?? 20,
  status: props.project?.status ?? 'ACTIVE' as const,
})

const isUpcoming = computed(() => form.status === 'UPCOMING')

function onSubmit() {
  emit('submit', {
    customerId: props.customerId,
    name: form.name,
    startDate: isUpcoming.value && form.startDate ? form.startDate : undefined,
    defaultUsagePercent: form.defaultUsagePercent,
    status: form.status,
  })
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="form">
    <div class="form-row"><label>Name</label><input v-model="form.name" required /></div>
    <div class="form-row">
      <label>Status</label>
      <select v-model="form.status">
        <option value="ACTIVE">Active</option>
        <option value="UPCOMING">Upcoming</option>
      </select>
    </div>
    <div class="form-row" v-if="isUpcoming">
      <label>Expected Start Date</label>
      <input v-model="form.startDate" type="date" required />
    </div>
    <div class="form-row">
      <label>Default Usage %</label>
      <div class="slider-row">
        <input v-model.number="form.defaultUsagePercent" type="range" min="5" max="100" step="5" class="slider" />
        <span class="slider-value">{{ form.defaultUsagePercent }}%</span>
      </div>
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
.slider-row { display: flex; align-items: center; gap: 0.5rem; }
.slider { flex: 1; accent-color: #3b82f6; }
.slider-value { font-weight: 600; font-size: 0.9rem; min-width: 36px; text-align: right; }
.form-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
</style>
```

- [ ] **Step 2: Update ProjectSidebar filter to match customer name**

In `frontend/src/components/ProjectSidebar.vue`, update the `sortedAndFilteredProjects` computed (lines 14-19):

```typescript
const sortedAndFilteredProjects = computed(() => {
  let list = [...props.projects]

  if (projectFilter.value) {
    const search = projectFilter.value.toLowerCase()
    list = list.filter(p =>
      p.name.toLowerCase().includes(search) ||
      customerName(props.customers, p.customerId).toLowerCase().includes(search)
    )
  }

  const assigned = props.assignedProjectIds ?? new Set()

  list.sort((a, b) => {
    const aAssigned = assigned.has(a.id) ? 1 : 0
    const bAssigned = assigned.has(b.id) ? 1 : 0
    if (aAssigned !== bAssigned) return aAssigned - bAssigned
    const custA = customerName(props.customers, a.customerId)
    const custB = customerName(props.customers, b.customerId)
    return custA.localeCompare(custB) || a.name.localeCompare(b.name)
  })

  return list
})
```

Also update the placeholder text on line 66:

```html
<input v-model="projectFilter" placeholder="Filter projects or customers..." class="project-filter" />
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ProjectForm.vue frontend/src/components/ProjectSidebar.vue
git commit -m "feat: simplify ProjectForm, add customer name to sidebar filter"
```

---

### Task 8: Frontend — Update UsageTimelineView and TimelineGrid

**Files:**
- Modify: `frontend/src/views/UsageTimelineView.vue:1-220`
- Modify: `frontend/src/components/TimelineGrid.vue:9-11`

- [ ] **Step 1: Update TimelineGrid emit for unassign**

In `frontend/src/components/TimelineGrid.vue`, change the `unassign` emit signature (line 10) to pass `assignmentId` instead of `architectId/projectId/projectName/month`:

```typescript
const emit = defineEmits<{
  drop: [architectId: number, month: string, data: { projectId: number; tentative: boolean; defaultUsagePercent: number }]
  unassign: [assignmentId: number]
  editUsage: [assignmentId: number, usage: number, month: string]
}>()
```

Update the unassign button in the template (line 117) to emit `assignmentId`:

```html
<button class="unassign-btn" @click.stop="emit('unassign', assignment.assignmentId)" title="Unassign">&minus;</button>
```

- [ ] **Step 2: Update UsageTimelineView — simplify drop and unassign**

Replace the content of `frontend/src/views/UsageTimelineView.vue`:

```vue
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUsageStore } from '@/stores/usage'
import { useProjectsStore } from '@/stores/projects'
import { useCustomersStore } from '@/stores/customers'
import { useAssignmentsStore } from '@/stores/assignments'
import { useArchitectsStore } from '@/stores/architects'
import { useGeoStore } from '@/stores/geo'
import { architectApi, assignmentApi } from '@/api/client'
import TimelineGrid from '@/components/TimelineGrid.vue'
import WorldMapView from '@/components/WorldMapView.vue'
import ProjectSidebar from '@/components/ProjectSidebar.vue'

const usageStore = useUsageStore()
const projectStore = useProjectsStore()
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

const assignedProjectIds = computed(() => {
  const ids = new Set<number>()
  for (const architect of usageStore.usageData) {
    for (const monthData of Object.values(architect.months)) {
      for (const assignment of monthData.assignments) {
        ids.add(assignment.projectId)
      }
    }
  }
  return ids
})

async function loadData() {
  await Promise.all([
    usageStore.fetchUsage(fromMonth.value, toMonth.value, countryFilter.value || undefined),
    projectStore.fetchAll(),
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
  data: { projectId: number; tentative: boolean; defaultUsagePercent: number }
) {
  try {
    await assignmentStore.create({
      architectId,
      projectId: data.projectId,
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
    <ProjectSidebar
      v-if="activeTab === 'timeline'"
      :projects="projectStore.projects"
      :customers="customerStore.customers"
      :assigned-project-ids="assignedProjectIds"
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
.toast { position: fixed; bottom: 1.5rem; left: 50%; transform: translateX(-50%); background: #334155; color: #fff; padding: 0.5rem 1.25rem; border-radius: 6px; font-size: 0.85rem; z-index: 300; box-shadow: 0 4px 12px rgba(0,0,0,0.2); }
</style>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/UsageTimelineView.vue frontend/src/components/TimelineGrid.vue
git commit -m "feat: simplify drop to single-month, remove confirmation dialog, add toast for duplicates"
```

---

### Task 9: Frontend — Update BacklogView and CustomersProjectsView

**Files:**
- Modify: `frontend/src/views/BacklogView.vue:1-61`
- Modify: `frontend/src/views/CustomersProjectsView.vue:123-138`

- [ ] **Step 1: Update BacklogView for UPCOMING status and single start date**

Replace the content of `frontend/src/views/BacklogView.vue`:

```vue
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useProjectsStore } from '@/stores/projects'
import { useCustomersStore } from '@/stores/customers'

const projectStore = useProjectsStore()
const customerStore = useCustomersStore()

const upcomingProjects = computed(() =>
  projectStore.projects.filter(p => p.status === 'UPCOMING')
)

function customerName(customerId: number) {
  return customerStore.customers.find(c => c.id === customerId)?.name ?? '-'
}

async function onActivate(id: number) {
  await projectStore.activate(id)
}

onMounted(async () => {
  await Promise.all([
    projectStore.fetchAll({ status: 'UPCOMING' }),
    customerStore.fetchAll(),
  ])
})
</script>

<template>
  <div>
    <h1>Project Backlog</h1>
    <p class="subtitle">Upcoming projects for future planning</p>

    <table>
      <thead>
        <tr><th>Project</th><th>Customer</th><th>Usage %</th><th>Expected Start</th><th>Actions</th></tr>
      </thead>
      <tbody>
        <tr v-for="project in upcomingProjects" :key="project.id">
          <td>{{ project.name }}</td>
          <td>{{ customerName(project.customerId) }}</td>
          <td>{{ project.defaultUsagePercent }}%</td>
          <td>{{ project.startDate ?? '-' }}</td>
          <td>
            <button class="primary" :data-testid="'activate-' + project.id" @click="onActivate(project.id)">Activate</button>
          </td>
        </tr>
        <tr v-if="upcomingProjects.length === 0">
          <td colspan="5" style="text-align:center; color:#94a3b8">No upcoming projects</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.subtitle { color: #64748b; margin-bottom: 1rem; }
</style>
```

- [ ] **Step 2: Update CustomersProjectsView project table columns**

In `frontend/src/views/CustomersProjectsView.vue`, replace the projects table (lines 123-149):

```html
      <div v-if="expandedCustomers.has(customer.id)" class="projects-table">
        <table>
          <thead><tr><th>Project</th><th>Status</th><th>Start</th><th>Usage %</th><th>Actions</th></tr></thead>
          <tbody>
            <tr v-for="project in projectStore.forCustomer(customer.id)" :key="project.id">
              <td>{{ project.name }}</td>
              <td><span :class="['badge', project.status === 'ACTIVE' ? 'badge-active' : 'badge-potential']">{{ project.status }}</span></td>
              <td>{{ project.status === 'UPCOMING' ? (project.startDate ?? '-') : '-' }}</td>
              <td>{{ project.defaultUsagePercent }}%</td>
              <td>
                <button @click="openEditProject(project)">Edit</button>
                <button class="danger" @click="onDeleteProject(project.id)">Delete</button>
              </td>
            </tr>
            <tr v-if="projectStore.forCustomer(customer.id).length === 0">
              <td colspan="5" style="text-align:center; color:#94a3b8">No projects</td>
            </tr>
          </tbody>
        </table>
      </div>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/BacklogView.vue frontend/src/views/CustomersProjectsView.vue
git commit -m "feat: update BacklogView and CustomersProjectsView for UPCOMING status"
```

---

### Task 10: Frontend — Update Tests

**Files:**
- Modify: `frontend/src/__tests__/UsageTimelineView.spec.ts:1-110`
- Modify: `frontend/src/__tests__/BacklogView.spec.ts:1-55`

- [ ] **Step 1: Update UsageTimelineView test mocks**

Replace the content of `frontend/src/__tests__/UsageTimelineView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UsageTimelineView from '@/views/UsageTimelineView.vue'

vi.mock('@/api/client', () => ({
  architectApi: {
    photoUrl: (id: number) => `/api/architects/${id}/photo`,
    list: vi.fn().mockResolvedValue([
      { id: 1, firstName: 'Alice', lastName: 'Smith', country: 'Australia' },
    ]),
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
              { assignmentId: 1, projectId: 1, projectName: 'KYC', customerName: 'Acme', usage: 20, tentative: false },
              { assignmentId: 2, projectId: 2, projectName: 'Beta', customerName: 'Beta Corp', usage: 20, tentative: true },
            ],
          },
          '2026-07': {
            total: 20,
            assignments: [
              { assignmentId: 3, projectId: 1, projectName: 'KYC', customerName: 'Acme', usage: 20, tentative: false },
            ],
          },
        },
      },
    ]),
  },
  projectApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, customerId: 1, name: 'KYC', status: 'ACTIVE', defaultUsagePercent: 20 },
      { id: 2, customerId: 2, name: 'Beta', status: 'UPCOMING', defaultUsagePercent: 20 },
    ]),
  },
  assignmentApi: {
    create: vi.fn().mockResolvedValue({ id: 99 }),
    update: vi.fn().mockResolvedValue({ id: 99 }),
    delete: vi.fn().mockResolvedValue(undefined),
    list: vi.fn().mockResolvedValue([]),
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
    countries: ['Australia', 'Japan'],
    fetchCountries: vi.fn(),
  }),
}))

describe('UsageTimelineView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders architect rows with usage data', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Smith')
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

  it('renders tab bar with Timeline and Map tabs', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const tabs = wrapper.findAll('[data-testid="view-tab"]')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toBe('Timeline')
    expect(tabs[1].text()).toBe('Map')
  })

  it('defaults to Timeline tab', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const activeTab = wrapper.find('[data-testid="view-tab"].active')
    expect(activeTab.text()).toBe('Timeline')
    expect(wrapper.find('.timeline-grid').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: Update BacklogView test mocks**

Replace the content of `frontend/src/__tests__/BacklogView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BacklogView from '@/views/BacklogView.vue'

const { mockActivate } = vi.hoisted(() => ({
  mockActivate: vi.fn().mockResolvedValue({
    id: 1, name: 'Future Project', status: 'ACTIVE',
  }),
}))

vi.mock('@/api/client', () => ({
  projectApi: {
    list: vi.fn().mockResolvedValue([
      {
        id: 1, customerId: 1, name: 'Future Project',
        defaultUsagePercent: 20, status: 'UPCOMING',
        startDate: '2026-09-01',
      },
    ]),
    activate: mockActivate,
  },
  customerApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, name: 'Acme Corp' },
    ]),
  },
}))

describe('BacklogView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders upcoming projects with start date', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    expect(wrapper.text()).toContain('Future Project')
    expect(wrapper.text()).toContain('2026-09-01')
  })

  it('activate button calls API', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    const activateBtn = wrapper.find('[data-testid="activate-1"]')
    await activateBtn.trigger('click')
    await flushPromises()

    expect(mockActivate).toHaveBeenCalledWith(1)
  })
})
```

- [ ] **Step 3: Run all frontend tests**

Run: `cd frontend && npx vitest run 2>&1 | tail -30`

Expected: All PASS

- [ ] **Step 4: Run frontend type check**

Run: `cd frontend && npx vue-tsc --noEmit 2>&1 | tail -20`

Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/__tests__/
git commit -m "test: update frontend tests for per-month assignment model"
```

---

### Task 11: Verify End-to-End

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test --info 2>&1 | tail -30`

Expected: All PASS

- [ ] **Step 2: Run all frontend tests**

Run: `cd frontend && npx vitest run 2>&1 | tail -30`

Expected: All PASS

- [ ] **Step 3: Run frontend type check**

Run: `cd frontend && npx vue-tsc --noEmit 2>&1 | tail -20`

Expected: No errors

- [ ] **Step 4: Start backend and verify migration runs**

Run: `cd backend && ./gradlew bootRun 2>&1 | head -40`

Expected: Liquibase migration 011 runs successfully, app starts.

- [ ] **Step 5: Manual browser testing**

Open `http://localhost:5173/` and verify:
1. Usage Timeline: drop a project on a single month — only that month gets the assignment
2. Drop the same project on the same month again — toast appears "Already assigned for this month"
3. Click the minus button on an assignment — it disappears immediately (no dialog)
4. Sidebar filter: type a customer name — projects for that customer appear
5. Customers & Projects: create a project with UPCOMING status — start date field appears
6. Backlog view: shows upcoming projects with single start date and Activate button
7. Activate an upcoming project — status changes to ACTIVE, start date disappears

- [ ] **Step 6: Commit any remaining fixes**

If any issues found during manual testing, fix and commit.
