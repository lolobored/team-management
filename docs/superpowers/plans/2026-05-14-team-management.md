# Team Management Tool — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a web app to manage Solution Architect assignments to customer projects with a visual usage timeline and drag-and-drop planning.

**Architecture:** Spring Boot 3 REST API serving a Vue 3 SPA. PostgreSQL runs in Docker Compose. Backend exposes CRUD endpoints for architects, customers, projects, and assignments, plus a computed usage endpoint. Frontend provides four views: architect management, customer/project management, usage timeline with drag-and-drop, and a project backlog.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL 16, Vue 3 (Composition API + TypeScript), Vite, Vue Router, Pinia, Axios

---

## File Structure

```
team-management/
├── docker-compose.yml
├── backend/
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd, .mvn/
│   └── src/
│       ├── main/
│       │   ├── java/org/lolobored/tm/
│       │   │   ├── TeamManagementApplication.java
│       │   │   ├── config/
│       │   │   │   └── WebConfig.java
│       │   │   ├── architect/
│       │   │   │   ├── Architect.java
│       │   │   │   ├── Region.java
│       │   │   │   ├── ArchitectRepository.java
│       │   │   │   └── ArchitectController.java
│       │   │   ├── customer/
│       │   │   │   ├── Customer.java
│       │   │   │   ├── CustomerRepository.java
│       │   │   │   └── CustomerController.java
│       │   │   ├── project/
│       │   │   │   ├── Project.java
│       │   │   │   ├── ProjectStatus.java
│       │   │   │   ├── ProjectRepository.java
│       │   │   │   └── ProjectController.java
│       │   │   ├── assignment/
│       │   │   │   ├── Assignment.java
│       │   │   │   ├── AssignmentRepository.java
│       │   │   │   └── AssignmentController.java
│       │   │   └── usage/
│       │   │       ├── UsageController.java
│       │   │       ├── UsageService.java
│       │   │       ├── ArchitectUsageDto.java
│       │   │       ├── MonthUsageDto.java
│       │   │       └── AssignmentUsageDto.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-test.yml
│       │       └── db/migration/
│       │           └── V1__initial_schema.sql
│       └── test/java/org/lolobored/tm/
│           ├── architect/
│           │   └── ArchitectControllerTest.java
│           ├── customer/
│           │   └── CustomerControllerTest.java
│           ├── project/
│           │   └── ProjectControllerTest.java
│           ├── assignment/
│           │   └── AssignmentControllerTest.java
│           └── usage/
│               └── UsageServiceTest.java
└── frontend/
    ├── package.json
    ├── tsconfig.json
    ├── tsconfig.app.json
    ├── tsconfig.node.json
    ├── vite.config.ts
    ├── index.html
    ├── env.d.ts
    └── src/
        ├── App.vue
        ├── main.ts
        ├── style.css
        ├── router/
        │   └── index.ts
        ├── types/
        │   └── index.ts
        ├── api/
        │   └── client.ts
        ├── stores/
        │   ├── architects.ts
        │   ├── customers.ts
        │   ├── projects.ts
        │   ├── assignments.ts
        │   └── usage.ts
        ├── views/
        │   ├── ArchitectsView.vue
        │   ├── CustomersProjectsView.vue
        │   ├── UsageTimelineView.vue
        │   └── BacklogView.vue
        └── components/
            ├── AppLayout.vue
            ├── ArchitectForm.vue
            ├── CustomerForm.vue
            ├── ProjectForm.vue
            ├── TimelineGrid.vue
            └── ProjectSidebar.vue
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `docker-compose.yml`
- Create: `backend/` (via Spring Initializr)
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-test.yml`
- Create: `backend/src/main/java/org/lolobored/tm/config/WebConfig.java`
- Create: `frontend/` (via create-vue)

- [ ] **Step 1: Create Docker Compose file**

Create `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: teammanagement
      POSTGRES_USER: tm
      POSTGRES_PASSWORD: tm
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

- [ ] **Step 2: Start PostgreSQL and verify**

Run: `docker compose up -d`
Then: `docker compose ps`
Expected: postgres container running, port 5432 mapped.

- [ ] **Step 3: Generate Spring Boot backend**

```bash
curl -s https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.4.1 \
  -d baseDir=backend \
  -d groupId=org.lolobored \
  -d artifactId=team-management \
  -d name=team-management \
  -d packageName=org.lolobored.tm \
  -d javaVersion=21 \
  -d dependencies=web,data-jpa,flyway,postgresql \
  -o /tmp/backend.zip && unzip -o /tmp/backend.zip -d .
```

This creates `backend/` with Maven wrapper, pom.xml, and the application class.

- [ ] **Step 4: Add H2 test dependency to pom.xml**

Add inside the `<dependencies>` section of `backend/pom.xml`:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

Note: `flyway-database-postgresql` is required by Flyway 10+ for PostgreSQL support.

- [ ] **Step 5: Configure application.yml**

Replace `backend/src/main/resources/application.properties` with `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/teammanagement
    username: tm
    password: tm
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
```

Delete the empty `application.properties` file.

- [ ] **Step 6: Configure test profile**

Create `backend/src/main/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

- [ ] **Step 7: Add CORS configuration**

Create `backend/src/main/java/org/lolobored/tm/config/WebConfig.java`:

```java
package org.lolobored.tm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
    }
}
```

- [ ] **Step 8: Verify backend compiles**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: Scaffold Vue 3 frontend**

```bash
npm create vue@latest frontend -- --typescript --router --pinia
```

When prompted, accept defaults. This creates `frontend/` with Vue 3 + TypeScript + Vue Router + Pinia.

- [ ] **Step 10: Install Axios and dev dependencies**

```bash
cd frontend && npm install axios && npm install -D vitest @vue/test-utils jsdom
```

- [ ] **Step 11: Configure Vite proxy**

Replace the contents of `frontend/vite.config.ts`:

```typescript
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 12: Configure Vitest**

Add to `frontend/vitest.config.ts`:

```typescript
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    root: fileURLToPath(new URL('./', import.meta.url))
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
```

- [ ] **Step 13: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build completes without errors.

- [ ] **Step 14: Commit**

```bash
git add docker-compose.yml backend frontend
git commit -m "feat: scaffold project with Spring Boot, Vue 3, and Docker Compose"
```

---

### Task 2: Database Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__initial_schema.sql`

- [ ] **Step 1: Write Flyway migration**

Create `backend/src/main/resources/db/migration/V1__initial_schema.sql`:

```sql
CREATE TABLE architect (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    region VARCHAR(10) NOT NULL
);

CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(255),
    city VARCHAR(255)
);

CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    start_date DATE,
    duration_months INTEGER NOT NULL,
    default_usage_percent INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    official_date DATE,
    plausible_date DATE
);

CREATE TABLE assignment (
    id BIGSERIAL PRIMARY KEY,
    architect_id BIGINT NOT NULL REFERENCES architect(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    usage_percent INTEGER,
    tentative BOOLEAN NOT NULL DEFAULT FALSE
);
```

- [ ] **Step 2: Verify migration runs**

Ensure PostgreSQL is running (`docker compose up -d`), then:

Run: `cd backend && ./mvnw spring-boot:run`
Expected: Application starts without errors. Check logs for `Successfully applied 1 migration`.
Stop the application with Ctrl+C.

- [ ] **Step 3: Verify tables exist**

```bash
docker compose exec postgres psql -U tm -d teammanagement -c "\dt"
```

Expected: Four tables listed: `architect`, `customer`, `project`, `assignment`, plus `flyway_schema_history`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V1__initial_schema.sql
git commit -m "feat: add Flyway migration for initial database schema"
```

---

### Task 3: Architect CRUD (Backend)

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/architect/Region.java`
- Create: `backend/src/main/java/org/lolobored/tm/architect/Architect.java`
- Create: `backend/src/main/java/org/lolobored/tm/architect/ArchitectRepository.java`
- Create: `backend/src/main/java/org/lolobored/tm/architect/ArchitectController.java`
- Create: `backend/src/test/java/org/lolobored/tm/architect/ArchitectControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/lolobored/tm/architect/ArchitectControllerTest.java`:

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                {"name": "Alice", "email": "alice@example.com", "region": "ANZ"}
                """;

        String response = mockMvc.perform(post("/api/architects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.region").value("ANZ"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/architects/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void updateArchitect() throws Exception {
        String json = """
                {"name": "Alice", "region": "ANZ"}
                """;
        String response = mockMvc.perform(post("/api/architects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        String updateJson = """
                {"name": "Alice Updated", "region": "ASIA"}
                """;
        mockMvc.perform(put("/api/architects/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.region").value("ASIA"));
    }

    @Test
    void deleteArchitect() throws Exception {
        String json = """
                {"name": "Alice", "region": "ANZ"}
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

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -pl . -Dtest=ArchitectControllerTest -q`
Expected: Compilation errors — `Region`, `Architect`, etc. do not exist yet.

- [ ] **Step 3: Create Region enum**

Create `backend/src/main/java/org/lolobored/tm/architect/Region.java`:

```java
package org.lolobored.tm.architect;

public enum Region {
    ANZ, ASIA
}
```

- [ ] **Step 4: Create Architect entity**

Create `backend/src/main/java/org/lolobored/tm/architect/Architect.java`:

```java
package org.lolobored.tm.architect;

import jakarta.persistence.*;

@Entity
public class Architect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }
}
```

- [ ] **Step 5: Create ArchitectRepository**

Create `backend/src/main/java/org/lolobored/tm/architect/ArchitectRepository.java`:

```java
package org.lolobored.tm.architect;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchitectRepository extends JpaRepository<Architect, Long> {
}
```

- [ ] **Step 6: Create ArchitectController**

Create `backend/src/main/java/org/lolobored/tm/architect/ArchitectController.java`:

```java
package org.lolobored.tm.architect;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/architects")
public class ArchitectController {

    private final ArchitectRepository repository;

    public ArchitectController(ArchitectRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Architect> list() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Architect> create(@RequestBody Architect architect) {
        Architect saved = repository.save(architect);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public Architect get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Architect update(@PathVariable Long id, @RequestBody Architect architect) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        architect.setId(id);
        return repository.save(architect);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw test -pl . -Dtest=ArchitectControllerTest -q`
Expected: All 4 tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/architect backend/src/test/java/org/lolobored/tm/architect
git commit -m "feat: add Architect CRUD endpoint with tests"
```

---

### Task 4: Customer CRUD (Backend)

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/customer/Customer.java`
- Create: `backend/src/main/java/org/lolobored/tm/customer/CustomerRepository.java`
- Create: `backend/src/main/java/org/lolobored/tm/customer/CustomerController.java`
- Create: `backend/src/test/java/org/lolobored/tm/customer/CustomerControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/lolobored/tm/customer/CustomerControllerTest.java`:

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                {"name": "Acme Corp", "country": "Australia", "city": "Sydney"}
                """;

        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.country").value("Australia"))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        String updateJson = """
                {"name": "Acme Holdings", "country": "NZ", "city": "Auckland"}
                """;
        mockMvc.perform(put("/api/customers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Holdings"))
                .andExpect(jsonPath("$.city").value("Auckland"));
    }

    @Test
    void deleteCustomer() throws Exception {
        String json = """
                {"name": "Acme Corp"}
                """;
        String response = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
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

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -pl . -Dtest=CustomerControllerTest -q`
Expected: Compilation errors — `Customer` does not exist yet.

- [ ] **Step 3: Create Customer entity**

Create `backend/src/main/java/org/lolobored/tm/customer/Customer.java`:

```java
package org.lolobored.tm.customer;

import jakarta.persistence.*;

@Entity
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

- [ ] **Step 4: Create CustomerRepository**

Create `backend/src/main/java/org/lolobored/tm/customer/CustomerRepository.java`:

```java
package org.lolobored.tm.customer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
```

- [ ] **Step 5: Create CustomerController**

Create `backend/src/main/java/org/lolobored/tm/customer/CustomerController.java`:

```java
package org.lolobored.tm.customer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Customer> list() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Customer customer) {
        Customer saved = repository.save(customer);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Customer update(@PathVariable Long id, @RequestBody Customer customer) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        customer.setId(id);
        return repository.save(customer);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -pl . -Dtest=CustomerControllerTest -q`
Expected: All 4 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/customer backend/src/test/java/org/lolobored/tm/customer
git commit -m "feat: add Customer CRUD endpoint with tests"
```

---

### Task 5: Project CRUD (Backend)

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/project/ProjectStatus.java`
- Create: `backend/src/main/java/org/lolobored/tm/project/Project.java`
- Create: `backend/src/main/java/org/lolobored/tm/project/ProjectRepository.java`
- Create: `backend/src/main/java/org/lolobored/tm/project/ProjectController.java`
- Create: `backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/lolobored/tm/project/ProjectControllerTest.java`:

```java
package org.lolobored.tm.project;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ProjectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRepository customerRepository;

    private Long customerId;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setName("Acme Corp");
        customerId = customerRepository.save(customer).getId();
    }

    @Test
    void createAndGetProject() throws Exception {
        String json = """
                {
                    "customerId": %d,
                    "name": "KYC Platform",
                    "startDate": "2026-06-01",
                    "durationMonths": 6,
                    "defaultUsagePercent": 20,
                    "status": "ACTIVE"
                }
                """.formatted(customerId);

        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
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

    @Test
    void listProjects_filterByStatus() throws Exception {
        String active = """
                {"customerId": %d, "name": "Active Project", "startDate": "2026-06-01",
                 "durationMonths": 3, "defaultUsagePercent": 20, "status": "ACTIVE"}
                """.formatted(customerId);
        String potential = """
                {"customerId": %d, "name": "Future Project",
                 "durationMonths": 6, "defaultUsagePercent": 20, "status": "POTENTIAL",
                 "officialDate": "2026-09-01", "plausibleDate": "2026-10-01"}
                """.formatted(customerId);

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(active));
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(potential));

        mockMvc.perform(get("/api/projects?status=POTENTIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Future Project"));
    }

    @Test
    void activateProject() throws Exception {
        String json = """
                {"customerId": %d, "name": "Future Project",
                 "durationMonths": 6, "defaultUsagePercent": 20, "status": "POTENTIAL",
                 "officialDate": "2026-09-01", "plausibleDate": "2026-10-01"}
                """.formatted(customerId);

        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/projects/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.startDate").value("2026-10-01"));
    }

    @Test
    void deleteProject() throws Exception {
        String json = """
                {"customerId": %d, "name": "To Delete", "startDate": "2026-06-01",
                 "durationMonths": 3, "defaultUsagePercent": 20, "status": "ACTIVE"}
                """.formatted(customerId);

        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/projects/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + id))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -pl . -Dtest=ProjectControllerTest -q`
Expected: Compilation errors — `Project`, `ProjectStatus` do not exist.

- [ ] **Step 3: Create ProjectStatus enum**

Create `backend/src/main/java/org/lolobored/tm/project/ProjectStatus.java`:

```java
package org.lolobored.tm.project;

public enum ProjectStatus {
    ACTIVE, POTENTIAL
}
```

- [ ] **Step 4: Create Project entity**

Create `backend/src/main/java/org/lolobored/tm/project/Project.java`:

```java
package org.lolobored.tm.project;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String name;

    private LocalDate startDate;

    @Column(nullable = false)
    private int durationMonths;

    @Column(nullable = false)
    private int defaultUsagePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    private LocalDate officialDate;

    private LocalDate plausibleDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }
    public int getDefaultUsagePercent() { return defaultUsagePercent; }
    public void setDefaultUsagePercent(int defaultUsagePercent) { this.defaultUsagePercent = defaultUsagePercent; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public LocalDate getOfficialDate() { return officialDate; }
    public void setOfficialDate(LocalDate officialDate) { this.officialDate = officialDate; }
    public LocalDate getPlausibleDate() { return plausibleDate; }
    public void setPlausibleDate(LocalDate plausibleDate) { this.plausibleDate = plausibleDate; }
}
```

- [ ] **Step 5: Create ProjectRepository**

Create `backend/src/main/java/org/lolobored/tm/project/ProjectRepository.java`:

```java
package org.lolobored.tm.project;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStatus(ProjectStatus status);
    List<Project> findByCustomerId(Long customerId);
    List<Project> findByCustomerIdAndStatus(Long customerId, ProjectStatus status);
}
```

- [ ] **Step 6: Create ProjectController**

Create `backend/src/main/java/org/lolobored/tm/project/ProjectController.java`:

```java
package org.lolobored.tm.project;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository repository;

    public ProjectController(ProjectRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Project> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) ProjectStatus status) {
        if (customerId != null && status != null) {
            return repository.findByCustomerIdAndStatus(customerId, status);
        } else if (customerId != null) {
            return repository.findByCustomerId(customerId);
        } else if (status != null) {
            return repository.findByStatus(status);
        }
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody Project project) {
        Project saved = repository.save(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public Project get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Project update(@PathVariable Long id, @RequestBody Project project) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        project.setId(id);
        return repository.save(project);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    @PatchMapping("/{id}/activate")
    public Project activate(@PathVariable Long id) {
        Project project = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (project.getStartDate() == null) {
            if (project.getPlausibleDate() != null) {
                project.setStartDate(project.getPlausibleDate());
            } else if (project.getOfficialDate() != null) {
                project.setStartDate(project.getOfficialDate());
            }
        }
        project.setStatus(ProjectStatus.ACTIVE);
        return repository.save(project);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw test -pl . -Dtest=ProjectControllerTest -q`
Expected: All 4 tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/project backend/src/test/java/org/lolobored/tm/project
git commit -m "feat: add Project CRUD endpoint with activate and status filtering"
```

---

### Task 6: Assignment CRUD (Backend)

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java`
- Create: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentRepository.java`
- Create: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentController.java`
- Create: `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`:

```java
package org.lolobored.tm.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.architect.Region;
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
        architect.setName("Alice");
        architect.setRegion(Region.ANZ);
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/assignments/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/assignments/" + id))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -pl . -Dtest=AssignmentControllerTest -q`
Expected: Compilation errors — `Assignment` does not exist.

- [ ] **Step 3: Create Assignment entity**

Create `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java`:

```java
package org.lolobored.tm.assignment;

import jakarta.persistence.*;

@Entity
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "architect_id", nullable = false)
    private Long architectId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    private Integer usagePercent;

    @Column(nullable = false)
    private boolean tentative;

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
}
```

- [ ] **Step 4: Create AssignmentRepository**

Create `backend/src/main/java/org/lolobored/tm/assignment/AssignmentRepository.java`:

```java
package org.lolobored.tm.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByArchitectId(Long architectId);
    List<Assignment> findByProjectId(Long projectId);
}
```

- [ ] **Step 5: Create AssignmentController**

Create `backend/src/main/java/org/lolobored/tm/assignment/AssignmentController.java`:

```java
package org.lolobored.tm.assignment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
            @RequestParam(required = false) Long projectId) {
        if (architectId != null) {
            return repository.findByArchitectId(architectId);
        } else if (projectId != null) {
            return repository.findByProjectId(projectId);
        }
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Assignment> create(@RequestBody Assignment assignment) {
        Assignment saved = repository.save(assignment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public Assignment get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Assignment update(@PathVariable Long id, @RequestBody Assignment assignment) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        assignment.setId(id);
        return repository.save(assignment);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -pl . -Dtest=AssignmentControllerTest -q`
Expected: All 4 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/assignment backend/src/test/java/org/lolobored/tm/assignment
git commit -m "feat: add Assignment CRUD endpoint with filtering and tests"
```

---

### Task 7: Usage Computation Service & Endpoint

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/usage/AssignmentUsageDto.java`
- Create: `backend/src/main/java/org/lolobored/tm/usage/MonthUsageDto.java`
- Create: `backend/src/main/java/org/lolobored/tm/usage/ArchitectUsageDto.java`
- Create: `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`
- Create: `backend/src/main/java/org/lolobored/tm/usage/UsageController.java`
- Create: `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java`:

```java
package org.lolobored.tm.usage;

import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.architect.Region;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UsageServiceTest {

    @Autowired private UsageService usageService;
    @Autowired private ArchitectRepository architectRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AssignmentRepository assignmentRepository;

    private Long architectId;

    @BeforeEach
    void setUp() {
        Architect architect = new Architect();
        architect.setName("Alice");
        architect.setRegion(Region.ANZ);
        architectId = architectRepository.save(architect).getId();

        Customer customer = new Customer();
        customer.setName("Acme");
        Long customerId = customerRepository.save(customer).getId();

        // Active project: June 2026, 3 months (June, July, August)
        Project activeProject = new Project();
        activeProject.setCustomerId(customerId);
        activeProject.setName("KYC Platform");
        activeProject.setStartDate(LocalDate.of(2026, 6, 1));
        activeProject.setDurationMonths(3);
        activeProject.setDefaultUsagePercent(20);
        activeProject.setStatus(ProjectStatus.ACTIVE);
        Long activeProjectId = projectRepository.save(activeProject).getId();

        // Assignment with explicit usage
        Assignment a1 = new Assignment();
        a1.setArchitectId(architectId);
        a1.setProjectId(activeProjectId);
        a1.setUsagePercent(25);
        a1.setTentative(false);
        assignmentRepository.save(a1);

        // Potential project with plausible date Sept 2026, 2 months
        Project potentialProject = new Project();
        potentialProject.setCustomerId(customerId);
        potentialProject.setName("Future Work");
        potentialProject.setDurationMonths(2);
        potentialProject.setDefaultUsagePercent(15);
        potentialProject.setStatus(ProjectStatus.POTENTIAL);
        potentialProject.setOfficialDate(LocalDate.of(2026, 8, 1));
        potentialProject.setPlausibleDate(LocalDate.of(2026, 9, 1));
        Long potentialProjectId = projectRepository.save(potentialProject).getId();

        // Tentative assignment with null usage (falls back to project default 15%)
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
        assertEquals("Alice", dto.architectName());
        assertEquals(Region.ANZ, dto.region());

        // June: active 25%
        MonthUsageDto june = dto.months().get(YearMonth.of(2026, 6));
        assertNotNull(june);
        assertEquals(25, june.total());
        assertEquals(1, june.assignments().size());
        assertFalse(june.assignments().get(0).tentative());

        // August: active 25%
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

        // Sept: tentative 15% (project default)
        MonthUsageDto sept = dto.months().get(YearMonth.of(2026, 9));
        assertNotNull(sept);
        assertEquals(15, sept.total());
        assertTrue(sept.assignments().get(0).tentative());

        // Oct: tentative 15%
        MonthUsageDto oct = dto.months().get(YearMonth.of(2026, 10));
        assertNotNull(oct);
        assertEquals(15, oct.total());
    }

    @Test
    void computeUsage_overlapMonth_sumsUsage() {
        // Query a range that spans both projects (impossible with current data since
        // active ends Aug and potential starts Sept). Let's add another assignment.
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

        // July: KYC 25% + Beta 30% = 55%
        assertEquals(55, july.total());
        assertEquals(2, july.assignments().size());
    }

    @Test
    void computeUsage_filterByRegion() {
        Architect asiaArchitect = new Architect();
        asiaArchitect.setName("Bob");
        asiaArchitect.setRegion(Region.ASIA);
        architectRepository.save(asiaArchitect);

        YearMonth from = YearMonth.of(2026, 6);
        YearMonth to = YearMonth.of(2026, 6);

        List<ArchitectUsageDto> anzOnly = usageService.computeUsage(from, to, Region.ANZ, null);
        assertEquals(1, anzOnly.size());
        assertEquals("Alice", anzOnly.get(0).architectName());

        List<ArchitectUsageDto> asiaOnly = usageService.computeUsage(from, to, Region.ASIA, null);
        assertEquals(1, asiaOnly.size());
        assertEquals("Bob", asiaOnly.get(0).architectName());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -pl . -Dtest=UsageServiceTest -q`
Expected: Compilation errors — DTOs and service do not exist.

- [ ] **Step 3: Create DTOs**

Create `backend/src/main/java/org/lolobored/tm/usage/AssignmentUsageDto.java`:

```java
package org.lolobored.tm.usage;

public record AssignmentUsageDto(
        Long projectId,
        String projectName,
        String customerName,
        int usage,
        boolean tentative
) {}
```

Create `backend/src/main/java/org/lolobored/tm/usage/MonthUsageDto.java`:

```java
package org.lolobored.tm.usage;

import java.util.List;

public record MonthUsageDto(
        int total,
        List<AssignmentUsageDto> assignments
) {}
```

Create `backend/src/main/java/org/lolobored/tm/usage/ArchitectUsageDto.java`:

```java
package org.lolobored.tm.usage;

import org.lolobored.tm.architect.Region;
import java.time.YearMonth;
import java.util.Map;

public record ArchitectUsageDto(
        Long architectId,
        String architectName,
        Region region,
        Map<YearMonth, MonthUsageDto> months
) {}
```

- [ ] **Step 4: Create UsageService**

Create `backend/src/main/java/org/lolobored/tm/usage/UsageService.java`:

```java
package org.lolobored.tm.usage;

import org.lolobored.tm.architect.Architect;
import org.lolobored.tm.architect.ArchitectRepository;
import org.lolobored.tm.architect.Region;
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
                                                 Region region, Long architectId) {
        List<Architect> architects;
        if (architectId != null) {
            architects = architectRepository.findById(architectId)
                    .map(List::of).orElse(List.of());
        } else {
            architects = architectRepository.findAll();
        }

        if (region != null) {
            architects = architects.stream()
                    .filter(a -> a.getRegion() == region)
                    .toList();
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

                    if (month.isBefore(projectStart) || month.isAfter(projectEnd)) continue;

                    int usage = assignment.getUsagePercent() != null
                            ? assignment.getUsagePercent()
                            : project.getDefaultUsagePercent();

                    Customer customer = customerCache.get(project.getCustomerId());
                    String customerName = customer != null ? customer.getName() : "";

                    monthAssignments.add(new AssignmentUsageDto(
                            project.getId(),
                            project.getName(),
                            customerName,
                            usage,
                            assignment.isTentative()
                    ));
                }

                int total = monthAssignments.stream().mapToInt(AssignmentUsageDto::usage).sum();
                months.put(month, new MonthUsageDto(total, monthAssignments));
            }

            result.add(new ArchitectUsageDto(
                    architect.getId(),
                    architect.getName(),
                    architect.getRegion(),
                    months
            ));
        }

        return result;
    }

    private LocalDate getEffectiveStartDate(Project project) {
        if (project.getStatus() == ProjectStatus.ACTIVE) {
            return project.getStartDate();
        }
        if (project.getPlausibleDate() != null) {
            return project.getPlausibleDate();
        }
        return project.getOfficialDate();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -pl . -Dtest=UsageServiceTest -q`
Expected: All 4 tests pass.

- [ ] **Step 6: Create UsageController**

Create `backend/src/main/java/org/lolobored/tm/usage/UsageController.java`:

```java
package org.lolobored.tm.usage;

import org.lolobored.tm.architect.Region;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
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
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) Long architectId) {
        return usageService.computeUsage(
                YearMonth.parse(from),
                YearMonth.parse(to),
                region,
                architectId
        );
    }
}
```

- [ ] **Step 7: Run all backend tests**

Run: `cd backend && ./mvnw test -q`
Expected: All tests pass (architect, customer, project, assignment, usage).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/lolobored/tm/usage backend/src/test/java/org/lolobored/tm/usage
git commit -m "feat: add usage computation service and endpoint"
```

---

### Task 8: Frontend Scaffolding

**Files:**
- Create: `frontend/src/types/index.ts`
- Create: `frontend/src/api/client.ts`
- Modify: `frontend/src/router/index.ts`
- Create: `frontend/src/components/AppLayout.vue`
- Modify: `frontend/src/App.vue`
- Create: `frontend/src/style.css`

- [ ] **Step 1: Define TypeScript types**

Create `frontend/src/types/index.ts`:

```typescript
export type Region = 'ANZ' | 'ASIA'
export type ProjectStatus = 'ACTIVE' | 'POTENTIAL'

export interface Architect {
  id: number
  name: string
  email?: string
  region: Region
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
  region: Region
  months: Record<string, MonthUsage>
}
```

- [ ] **Step 2: Create API client**

Create `frontend/src/api/client.ts`:

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
  get: (from: string, to: string, region?: string, architectId?: number) =>
    api.get<ArchitectUsage[]>('/usage', { params: { from, to, region, architectId } }).then(r => r.data),
}
```

- [ ] **Step 3: Set up router**

Replace `frontend/src/router/index.ts`:

```typescript
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/timeline',
    },
    {
      path: '/architects',
      name: 'architects',
      component: () => import('@/views/ArchitectsView.vue'),
    },
    {
      path: '/customers',
      name: 'customers',
      component: () => import('@/views/CustomersProjectsView.vue'),
    },
    {
      path: '/timeline',
      name: 'timeline',
      component: () => import('@/views/UsageTimelineView.vue'),
    },
    {
      path: '/backlog',
      name: 'backlog',
      component: () => import('@/views/BacklogView.vue'),
    },
  ],
})

export default router
```

- [ ] **Step 4: Create AppLayout component**

Create `frontend/src/components/AppLayout.vue`:

```vue
<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app-layout">
    <nav class="sidebar">
      <h2>Team Mgmt</h2>
      <ul>
        <li><RouterLink to="/timeline">Usage Timeline</RouterLink></li>
        <li><RouterLink to="/architects">Architects</RouterLink></li>
        <li><RouterLink to="/customers">Customers & Projects</RouterLink></li>
        <li><RouterLink to="/backlog">Project Backlog</RouterLink></li>
      </ul>
    </nav>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 220px;
  background: #1e293b;
  color: #e2e8f0;
  padding: 1rem;
  flex-shrink: 0;
}

.sidebar h2 {
  font-size: 1.1rem;
  margin-bottom: 1.5rem;
  color: #38bdf8;
}

.sidebar ul {
  list-style: none;
  padding: 0;
}

.sidebar li {
  margin-bottom: 0.5rem;
}

.sidebar a {
  color: #cbd5e1;
  text-decoration: none;
  display: block;
  padding: 0.5rem 0.75rem;
  border-radius: 4px;
}

.sidebar a:hover,
.sidebar a.router-link-active {
  background: #334155;
  color: #fff;
}

.content {
  flex: 1;
  padding: 1.5rem;
  background: #f8fafc;
}
</style>
```

- [ ] **Step 5: Update App.vue**

Replace `frontend/src/App.vue`:

```vue
<script setup lang="ts">
import AppLayout from '@/components/AppLayout.vue'
</script>

<template>
  <AppLayout />
</template>
```

- [ ] **Step 6: Add global styles**

Create `frontend/src/style.css`:

```css
*,
*::before,
*::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 14px;
  color: #1e293b;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  text-align: left;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e2e8f0;
}

th {
  background: #f1f5f9;
  font-weight: 600;
}

button {
  padding: 0.4rem 0.8rem;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 0.85rem;
}

button:hover {
  background: #f1f5f9;
}

button.primary {
  background: #3b82f6;
  color: #fff;
  border-color: #3b82f6;
}

button.primary:hover {
  background: #2563eb;
}

button.danger {
  color: #ef4444;
  border-color: #ef4444;
}

input, select {
  padding: 0.4rem 0.6rem;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  font-size: 0.9rem;
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 10px;
  font-size: 0.75rem;
  font-weight: 600;
}

.badge-active {
  background: #dcfce7;
  color: #166534;
}

.badge-potential {
  background: #fef9c3;
  color: #854d0e;
}
```

- [ ] **Step 7: Update main.ts to import styles**

Replace `frontend/src/main.ts`:

```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

- [ ] **Step 8: Create placeholder view files**

Create these four placeholder files so the router doesn't break:

`frontend/src/views/ArchitectsView.vue`:
```vue
<template><div><h1>Architects</h1></div></template>
```

`frontend/src/views/CustomersProjectsView.vue`:
```vue
<template><div><h1>Customers & Projects</h1></div></template>
```

`frontend/src/views/UsageTimelineView.vue`:
```vue
<template><div><h1>Usage Timeline</h1></div></template>
```

`frontend/src/views/BacklogView.vue`:
```vue
<template><div><h1>Project Backlog</h1></div></template>
```

- [ ] **Step 9: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 10: Commit**

```bash
git add frontend/src
git commit -m "feat: set up frontend layout, router, types, and API client"
```

---

### Task 9: Architects View (Frontend)

**Files:**
- Create: `frontend/src/stores/architects.ts`
- Create: `frontend/src/components/ArchitectForm.vue`
- Modify: `frontend/src/views/ArchitectsView.vue`
- Create: `frontend/src/__tests__/ArchitectsView.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/ArchitectsView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ArchitectsView from '@/views/ArchitectsView.vue'

vi.mock('@/api/client', () => ({
  architectApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, name: 'Alice', email: 'alice@test.com', region: 'ANZ' },
      { id: 2, name: 'Bob', region: 'ASIA' },
    ]),
    create: vi.fn().mockResolvedValue({ id: 3, name: 'Charlie', region: 'ANZ' }),
    delete: vi.fn().mockResolvedValue(undefined),
  },
}))

describe('ArchitectsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders architect list on mount', async () => {
    const wrapper = mount(ArchitectsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Bob')
    expect(wrapper.text()).toContain('ANZ')
    expect(wrapper.text()).toContain('ASIA')
  })

  it('filters by region', async () => {
    const wrapper = mount(ArchitectsView)
    await flushPromises()

    const select = wrapper.find('select')
    await select.setValue('ANZ')

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).not.toContain('Bob')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/ArchitectsView.spec.ts`
Expected: FAIL — ArchitectsView is a placeholder.

- [ ] **Step 3: Create architects store**

Create `frontend/src/stores/architects.ts`:

```typescript
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { architectApi } from '@/api/client'
import type { Architect } from '@/types'

export const useArchitectsStore = defineStore('architects', () => {
  const architects = ref<Architect[]>([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    architects.value = await architectApi.list()
    loading.value = false
  }

  async function create(data: Omit<Architect, 'id'>) {
    const created = await architectApi.create(data)
    architects.value.push(created)
    return created
  }

  async function update(id: number, data: Omit<Architect, 'id'>) {
    const updated = await architectApi.update(id, data)
    const index = architects.value.findIndex(a => a.id === id)
    if (index !== -1) architects.value[index] = updated
    return updated
  }

  async function remove(id: number) {
    await architectApi.delete(id)
    architects.value = architects.value.filter(a => a.id !== id)
  }

  return { architects, loading, fetchAll, create, update, remove }
})
```

- [ ] **Step 4: Create ArchitectForm component**

Create `frontend/src/components/ArchitectForm.vue`:

```vue
<script setup lang="ts">
import { reactive } from 'vue'
import type { Architect } from '@/types'

const props = defineProps<{
  architect?: Architect
}>()

const emit = defineEmits<{
  submit: [data: Omit<Architect, 'id'>]
  cancel: []
}>()

const form = reactive({
  name: props.architect?.name ?? '',
  email: props.architect?.email ?? '',
  region: props.architect?.region ?? 'ANZ' as const,
})

function onSubmit() {
  emit('submit', {
    name: form.name,
    email: form.email || undefined,
    region: form.region,
  })
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="architect-form">
    <div class="form-row">
      <label>Name</label>
      <input v-model="form.name" required />
    </div>
    <div class="form-row">
      <label>Email</label>
      <input v-model="form.email" type="email" />
    </div>
    <div class="form-row">
      <label>Region</label>
      <select v-model="form.region">
        <option value="ANZ">ANZ</option>
        <option value="ASIA">Asia</option>
      </select>
    </div>
    <div class="form-actions">
      <button type="submit" class="primary">Save</button>
      <button type="button" @click="emit('cancel')">Cancel</button>
    </div>
  </form>
</template>

<style scoped>
.architect-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-width: 400px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.form-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
</style>
```

- [ ] **Step 5: Implement ArchitectsView**

Replace `frontend/src/views/ArchitectsView.vue`:

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useArchitectsStore } from '@/stores/architects'
import ArchitectForm from '@/components/ArchitectForm.vue'
import type { Architect } from '@/types'

const store = useArchitectsStore()
const regionFilter = ref('')
const showForm = ref(false)
const editingArchitect = ref<Architect | undefined>()

const filtered = computed(() => {
  if (!regionFilter.value) return store.architects
  return store.architects.filter(a => a.region === regionFilter.value)
})

onMounted(() => store.fetchAll())

function openCreate() {
  editingArchitect.value = undefined
  showForm.value = true
}

function openEdit(architect: Architect) {
  editingArchitect.value = architect
  showForm.value = true
}

async function onSubmit(data: Omit<Architect, 'id'>) {
  if (editingArchitect.value) {
    await store.update(editingArchitect.value.id, data)
  } else {
    await store.create(data)
  }
  showForm.value = false
}

async function onDelete(id: number) {
  if (confirm('Delete this architect?')) {
    await store.remove(id)
  }
}
</script>

<template>
  <div>
    <div class="header">
      <h1>Architects</h1>
      <div class="controls">
        <select v-model="regionFilter">
          <option value="">All regions</option>
          <option value="ANZ">ANZ</option>
          <option value="ASIA">Asia</option>
        </select>
        <button class="primary" @click="openCreate">+ Add Architect</button>
      </div>
    </div>

    <div v-if="showForm" class="form-overlay">
      <div class="form-panel">
        <h2>{{ editingArchitect ? 'Edit' : 'Add' }} Architect</h2>
        <ArchitectForm
          :architect="editingArchitect"
          @submit="onSubmit"
          @cancel="showForm = false"
        />
      </div>
    </div>

    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Email</th>
          <th>Region</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in filtered" :key="a.id">
          <td>{{ a.name }}</td>
          <td>{{ a.email ?? '-' }}</td>
          <td>{{ a.region }}</td>
          <td>
            <button @click="openEdit(a)">Edit</button>
            <button class="danger" @click="onDelete(a.id)">Delete</button>
          </td>
        </tr>
        <tr v-if="filtered.length === 0">
          <td colspan="4" style="text-align: center; color: #94a3b8;">No architects found</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.controls {
  display: flex;
  gap: 0.5rem;
}

.form-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.form-panel {
  background: #fff;
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}

.form-panel h2 {
  margin-bottom: 1rem;
}

td button + button {
  margin-left: 0.25rem;
}
</style>
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/ArchitectsView.spec.ts`
Expected: Both tests pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/stores/architects.ts frontend/src/components/ArchitectForm.vue \
  frontend/src/views/ArchitectsView.vue frontend/src/__tests__/ArchitectsView.spec.ts
git commit -m "feat: add Architects view with CRUD, region filter, and tests"
```

---

### Task 10: Customers & Projects View (Frontend)

**Files:**
- Create: `frontend/src/stores/customers.ts`
- Create: `frontend/src/stores/projects.ts`
- Create: `frontend/src/components/CustomerForm.vue`
- Create: `frontend/src/components/ProjectForm.vue`
- Modify: `frontend/src/views/CustomersProjectsView.vue`
- Create: `frontend/src/__tests__/CustomersProjectsView.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/CustomersProjectsView.spec.ts`:

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

describe('CustomersProjectsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders customers and their projects', async () => {
    const wrapper = mount(CustomersProjectsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Acme Corp')
    expect(wrapper.text()).toContain('Australia')
  })

  it('shows project status badges', async () => {
    const wrapper = mount(CustomersProjectsView)
    await flushPromises()

    // Expand the customer to see projects
    const expandBtn = wrapper.find('[data-testid="expand-1"]')
    if (expandBtn.exists()) await expandBtn.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('KYC Platform')
    expect(wrapper.text()).toContain('ACTIVE')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/CustomersProjectsView.spec.ts`
Expected: FAIL — view is a placeholder.

- [ ] **Step 3: Create customers store**

Create `frontend/src/stores/customers.ts`:

```typescript
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { customerApi } from '@/api/client'
import type { Customer } from '@/types'

export const useCustomersStore = defineStore('customers', () => {
  const customers = ref<Customer[]>([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    customers.value = await customerApi.list()
    loading.value = false
  }

  async function create(data: Omit<Customer, 'id'>) {
    const created = await customerApi.create(data)
    customers.value.push(created)
    return created
  }

  async function update(id: number, data: Omit<Customer, 'id'>) {
    const updated = await customerApi.update(id, data)
    const index = customers.value.findIndex(c => c.id === id)
    if (index !== -1) customers.value[index] = updated
    return updated
  }

  async function remove(id: number) {
    await customerApi.delete(id)
    customers.value = customers.value.filter(c => c.id !== id)
  }

  return { customers, loading, fetchAll, create, update, remove }
})
```

- [ ] **Step 4: Create projects store**

Create `frontend/src/stores/projects.ts`:

```typescript
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { projectApi } from '@/api/client'
import type { Project } from '@/types'

export const useProjectsStore = defineStore('projects', () => {
  const projects = ref<Project[]>([])
  const loading = ref(false)

  async function fetchAll(params?: { customerId?: number; status?: string }) {
    loading.value = true
    projects.value = await projectApi.list(params)
    loading.value = false
  }

  async function create(data: Omit<Project, 'id'>) {
    const created = await projectApi.create(data)
    projects.value.push(created)
    return created
  }

  async function update(id: number, data: Omit<Project, 'id'>) {
    const updated = await projectApi.update(id, data)
    const index = projects.value.findIndex(p => p.id === id)
    if (index !== -1) projects.value[index] = updated
    return updated
  }

  async function remove(id: number) {
    await projectApi.delete(id)
    projects.value = projects.value.filter(p => p.id !== id)
  }

  async function activate(id: number) {
    const updated = await projectApi.activate(id)
    const index = projects.value.findIndex(p => p.id === id)
    if (index !== -1) projects.value[index] = updated
    return updated
  }

  function forCustomer(customerId: number) {
    return projects.value.filter(p => p.customerId === customerId)
  }

  return { projects, loading, fetchAll, create, update, remove, activate, forCustomer }
})
```

- [ ] **Step 5: Create CustomerForm component**

Create `frontend/src/components/CustomerForm.vue`:

```vue
<script setup lang="ts">
import { reactive } from 'vue'
import type { Customer } from '@/types'

const props = defineProps<{ customer?: Customer }>()
const emit = defineEmits<{
  submit: [data: Omit<Customer, 'id'>]
  cancel: []
}>()

const form = reactive({
  name: props.customer?.name ?? '',
  country: props.customer?.country ?? '',
  city: props.customer?.city ?? '',
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
    <div class="form-row">
      <label>Name</label>
      <input v-model="form.name" required />
    </div>
    <div class="form-row">
      <label>Country</label>
      <input v-model="form.country" />
    </div>
    <div class="form-row">
      <label>City</label>
      <input v-model="form.city" />
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

- [ ] **Step 6: Create ProjectForm component**

Create `frontend/src/components/ProjectForm.vue`:

```vue
<script setup lang="ts">
import { reactive, computed } from 'vue'
import type { Project, Customer } from '@/types'

const props = defineProps<{
  project?: Project
  customerId: number
  customers: Customer[]
}>()

const emit = defineEmits<{
  submit: [data: Omit<Project, 'id'>]
  cancel: []
}>()

const form = reactive({
  name: props.project?.name ?? '',
  startDate: props.project?.startDate ?? '',
  durationMonths: props.project?.durationMonths ?? 6,
  defaultUsagePercent: props.project?.defaultUsagePercent ?? 20,
  status: props.project?.status ?? 'ACTIVE' as const,
  officialDate: props.project?.officialDate ?? '',
  plausibleDate: props.project?.plausibleDate ?? '',
})

const isPotential = computed(() => form.status === 'POTENTIAL')

function onSubmit() {
  emit('submit', {
    customerId: props.customerId,
    name: form.name,
    startDate: form.startDate || undefined,
    durationMonths: form.durationMonths,
    defaultUsagePercent: form.defaultUsagePercent,
    status: form.status,
    officialDate: isPotential.value && form.officialDate ? form.officialDate : undefined,
    plausibleDate: isPotential.value && form.plausibleDate ? form.plausibleDate : undefined,
  })
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="form">
    <div class="form-row">
      <label>Name</label>
      <input v-model="form.name" required />
    </div>
    <div class="form-row">
      <label>Status</label>
      <select v-model="form.status">
        <option value="ACTIVE">Active</option>
        <option value="POTENTIAL">Potential</option>
      </select>
    </div>
    <div class="form-row" v-if="!isPotential">
      <label>Start Date</label>
      <input v-model="form.startDate" type="date" required />
    </div>
    <div class="form-row">
      <label>Duration (months)</label>
      <input v-model.number="form.durationMonths" type="number" min="1" required />
    </div>
    <div class="form-row">
      <label>Default Usage %</label>
      <input v-model.number="form.defaultUsagePercent" type="number" min="1" max="100" required />
    </div>
    <div v-if="isPotential">
      <div class="form-row">
        <label>Official Date (pre-sales estimate)</label>
        <input v-model="form.officialDate" type="date" />
      </div>
      <div class="form-row">
        <label>Plausible Date (realistic estimate)</label>
        <input v-model="form.plausibleDate" type="date" />
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
.form-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
</style>
```

- [ ] **Step 7: Implement CustomersProjectsView**

Replace `frontend/src/views/CustomersProjectsView.vue`:

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
  if (expandedCustomers.value.has(id)) {
    expandedCustomers.value.delete(id)
  } else {
    expandedCustomers.value.add(id)
  }
}

function openAddCustomer() {
  editingCustomer.value = undefined
  showCustomerForm.value = true
}

function openEditCustomer(c: Customer) {
  editingCustomer.value = c
  showCustomerForm.value = true
}

async function onCustomerSubmit(data: Omit<Customer, 'id'>) {
  if (editingCustomer.value) {
    await customerStore.update(editingCustomer.value.id, data)
  } else {
    await customerStore.create(data)
  }
  showCustomerForm.value = false
}

async function onDeleteCustomer(id: number) {
  if (confirm('Delete this customer and all its projects?')) {
    await customerStore.remove(id)
  }
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
  if (editingProject.value) {
    await projectStore.update(editingProject.value.id, data)
  } else {
    await projectStore.create(data)
  }
  showProjectForm.value = false
}

async function onDeleteProject(id: number) {
  if (confirm('Delete this project?')) {
    await projectStore.remove(id)
  }
}
</script>

<template>
  <div>
    <div class="header">
      <h1>Customers & Projects</h1>
      <button class="primary" @click="openAddCustomer">+ Add Customer</button>
    </div>

    <div v-if="showCustomerForm || showProjectForm" class="form-overlay">
      <div class="form-panel">
        <template v-if="showCustomerForm">
          <h2>{{ editingCustomer ? 'Edit' : 'Add' }} Customer</h2>
          <CustomerForm :customer="editingCustomer" @submit="onCustomerSubmit" @cancel="showCustomerForm = false" />
        </template>
        <template v-if="showProjectForm">
          <h2>{{ editingProject ? 'Edit' : 'Add' }} Project</h2>
          <ProjectForm
            :project="editingProject"
            :customer-id="projectFormCustomerId"
            :customers="customerStore.customers"
            @submit="onProjectSubmit"
            @cancel="showProjectForm = false"
          />
        </template>
      </div>
    </div>

    <div v-for="customer in customerStore.customers" :key="customer.id" class="customer-card">
      <div class="customer-header">
        <button
          class="expand-btn"
          :data-testid="'expand-' + customer.id"
          @click="toggleExpand(customer.id)"
        >
          {{ expandedCustomers.has(customer.id) ? '▼' : '▶' }}
        </button>
        <div class="customer-info">
          <strong>{{ customer.name }}</strong>
          <span v-if="customer.country || customer.city" class="location">
            {{ [customer.city, customer.country].filter(Boolean).join(', ') }}
          </span>
        </div>
        <div class="customer-actions">
          <button @click="openAddProject(customer.id)">+ Project</button>
          <button @click="openEditCustomer(customer)">Edit</button>
          <button class="danger" @click="onDeleteCustomer(customer.id)">Delete</button>
        </div>
      </div>

      <div v-if="expandedCustomers.has(customer.id)" class="projects-table">
        <table>
          <thead>
            <tr>
              <th>Project</th>
              <th>Status</th>
              <th>Start</th>
              <th>Duration</th>
              <th>Usage %</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="project in projectStore.forCustomer(customer.id)" :key="project.id">
              <td>{{ project.name }}</td>
              <td>
                <span :class="['badge', project.status === 'ACTIVE' ? 'badge-active' : 'badge-potential']">
                  {{ project.status }}
                </span>
              </td>
              <td>
                <template v-if="project.status === 'ACTIVE'">{{ project.startDate }}</template>
                <template v-else>
                  <div>Official: {{ project.officialDate ?? '-' }}</div>
                  <div>Plausible: {{ project.plausibleDate ?? '-' }}</div>
                </template>
              </td>
              <td>{{ project.durationMonths }} months</td>
              <td>{{ project.defaultUsagePercent }}%</td>
              <td>
                <button @click="openEditProject(project)">Edit</button>
                <button class="danger" @click="onDeleteProject(project.id)">Delete</button>
              </td>
            </tr>
            <tr v-if="projectStore.forCustomer(customer.id).length === 0">
              <td colspan="6" style="text-align:center; color:#94a3b8">No projects</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="customerStore.customers.length === 0" class="empty">
      No customers yet. Add one to get started.
    </div>
  </div>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }

.customer-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  margin-bottom: 0.75rem;
  background: #fff;
}

.customer-header {
  display: flex;
  align-items: center;
  padding: 0.75rem 1rem;
  gap: 0.75rem;
}

.expand-btn {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 0.25rem;
}

.customer-info { flex: 1; }
.location { color: #64748b; font-size: 0.85rem; margin-left: 0.5rem; }

.customer-actions { display: flex; gap: 0.25rem; }
.customer-actions button + button { margin-left: 0; }

.projects-table { border-top: 1px solid #e2e8f0; padding: 0 0.5rem 0.5rem; }

.form-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; z-index: 100;
}
.form-panel { background: #fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); }
.form-panel h2 { margin-bottom: 1rem; }

.empty { text-align: center; color: #94a3b8; padding: 3rem; }
</style>
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/CustomersProjectsView.spec.ts`
Expected: Both tests pass.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/stores/customers.ts frontend/src/stores/projects.ts \
  frontend/src/components/CustomerForm.vue frontend/src/components/ProjectForm.vue \
  frontend/src/views/CustomersProjectsView.vue frontend/src/__tests__/CustomersProjectsView.spec.ts
git commit -m "feat: add Customers & Projects view with hierarchical CRUD"
```

---

### Task 11: Project Backlog View (Frontend)

**Files:**
- Modify: `frontend/src/views/BacklogView.vue`
- Create: `frontend/src/__tests__/BacklogView.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/BacklogView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BacklogView from '@/views/BacklogView.vue'

const mockActivate = vi.fn().mockResolvedValue({
  id: 1, name: 'Future Project', status: 'ACTIVE', startDate: '2026-10-01',
})

vi.mock('@/api/client', () => ({
  projectApi: {
    list: vi.fn().mockResolvedValue([
      {
        id: 1, customerId: 1, name: 'Future Project',
        durationMonths: 6, defaultUsagePercent: 20, status: 'POTENTIAL',
        officialDate: '2026-09-01', plausibleDate: '2026-10-01',
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

  it('renders potential projects with dates', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    expect(wrapper.text()).toContain('Future Project')
    expect(wrapper.text()).toContain('2026-09-01')
    expect(wrapper.text()).toContain('2026-10-01')
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

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/BacklogView.spec.ts`
Expected: FAIL — view is a placeholder.

- [ ] **Step 3: Implement BacklogView**

Replace `frontend/src/views/BacklogView.vue`:

```vue
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useProjectsStore } from '@/stores/projects'
import { useCustomersStore } from '@/stores/customers'

const projectStore = useProjectsStore()
const customerStore = useCustomersStore()

const potentialProjects = computed(() =>
  projectStore.projects.filter(p => p.status === 'POTENTIAL')
)

function customerName(customerId: number) {
  return customerStore.customers.find(c => c.id === customerId)?.name ?? '-'
}

async function onActivate(id: number) {
  await projectStore.activate(id)
}

onMounted(async () => {
  await Promise.all([
    projectStore.fetchAll({ status: 'POTENTIAL' }),
    customerStore.fetchAll(),
  ])
})
</script>

<template>
  <div>
    <h1>Project Backlog</h1>
    <p class="subtitle">Potential projects for future planning</p>

    <table>
      <thead>
        <tr>
          <th>Project</th>
          <th>Customer</th>
          <th>Duration</th>
          <th>Usage %</th>
          <th>Official Date</th>
          <th>Plausible Date</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="project in potentialProjects" :key="project.id">
          <td>{{ project.name }}</td>
          <td>{{ customerName(project.customerId) }}</td>
          <td>{{ project.durationMonths }} months</td>
          <td>{{ project.defaultUsagePercent }}%</td>
          <td>{{ project.officialDate ?? '-' }}</td>
          <td>{{ project.plausibleDate ?? '-' }}</td>
          <td>
            <button
              class="primary"
              :data-testid="'activate-' + project.id"
              @click="onActivate(project.id)"
            >
              Activate
            </button>
          </td>
        </tr>
        <tr v-if="potentialProjects.length === 0">
          <td colspan="7" style="text-align:center; color:#94a3b8">No potential projects</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.subtitle { color: #64748b; margin-bottom: 1rem; }
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/BacklogView.spec.ts`
Expected: Both tests pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/BacklogView.vue frontend/src/__tests__/BacklogView.spec.ts
git commit -m "feat: add Project Backlog view with activate action"
```

---

### Task 12: Usage Timeline View with Drag-and-Drop (Frontend)

**Files:**
- Create: `frontend/src/stores/usage.ts`
- Create: `frontend/src/stores/assignments.ts`
- Create: `frontend/src/components/TimelineGrid.vue`
- Create: `frontend/src/components/ProjectSidebar.vue`
- Modify: `frontend/src/views/UsageTimelineView.vue`
- Create: `frontend/src/__tests__/UsageTimelineView.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/UsageTimelineView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UsageTimelineView from '@/views/UsageTimelineView.vue'

vi.mock('@/api/client', () => ({
  usageApi: {
    get: vi.fn().mockResolvedValue([
      {
        architectId: 1,
        architectName: 'Alice',
        region: 'ANZ',
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

describe('UsageTimelineView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders architect rows with usage data', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('KYC')
  })

  it('shows total usage in cells', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    expect(wrapper.text()).toContain('40%')
    expect(wrapper.text()).toContain('20%')
  })

  it('filters by region', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const select = wrapper.find('[data-testid="region-filter"]')
    expect(select.exists()).toBe(true)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts`
Expected: FAIL — view is a placeholder.

- [ ] **Step 3: Create usage store**

Create `frontend/src/stores/usage.ts`:

```typescript
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { usageApi } from '@/api/client'
import type { ArchitectUsage } from '@/types'

export const useUsageStore = defineStore('usage', () => {
  const usageData = ref<ArchitectUsage[]>([])
  const loading = ref(false)

  async function fetchUsage(from: string, to: string, region?: string, architectId?: number) {
    loading.value = true
    usageData.value = await usageApi.get(from, to, region, architectId)
    loading.value = false
  }

  return { usageData, loading, fetchUsage }
})
```

- [ ] **Step 4: Create assignments store**

Create `frontend/src/stores/assignments.ts`:

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

- [ ] **Step 5: Create ProjectSidebar component**

Create `frontend/src/components/ProjectSidebar.vue`:

```vue
<script setup lang="ts">
import type { Project, Customer } from '@/types'

defineProps<{
  projects: Project[]
  customers: Customer[]
}>()

function customerName(customers: Customer[], customerId: number) {
  return customers.find(c => c.id === customerId)?.name ?? ''
}

function onDragStart(event: DragEvent, project: Project) {
  event.dataTransfer!.setData('application/json', JSON.stringify({
    type: 'project',
    projectId: project.id,
    tentative: project.status === 'POTENTIAL',
    defaultUsagePercent: project.defaultUsagePercent,
  }))
  event.dataTransfer!.effectAllowed = 'copy'
}
</script>

<template>
  <div class="sidebar-panel">
    <h3>Projects</h3>
    <div
      v-for="project in projects"
      :key="project.id"
      class="project-item"
      :class="{ potential: project.status === 'POTENTIAL' }"
      draggable="true"
      @dragstart="onDragStart($event, project)"
    >
      <div class="project-name">{{ project.name }}</div>
      <div class="project-customer">{{ customerName(customers, project.customerId) }}</div>
      <div class="project-meta">
        <span :class="['badge', project.status === 'ACTIVE' ? 'badge-active' : 'badge-potential']">
          {{ project.status }}
        </span>
        <span>{{ project.defaultUsagePercent }}%</span>
      </div>
    </div>
    <div v-if="projects.length === 0" class="empty">No projects</div>
  </div>
</template>

<style scoped>
.sidebar-panel {
  width: 220px;
  border-left: 1px solid #e2e8f0;
  padding: 1rem;
  background: #fff;
  overflow-y: auto;
}

.sidebar-panel h3 {
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
  text-transform: uppercase;
  color: #64748b;
}

.project-item {
  padding: 0.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  margin-bottom: 0.5rem;
  cursor: grab;
  background: #fff;
}

.project-item:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}

.project-item.potential {
  border-style: dashed;
}

.project-name {
  font-weight: 600;
  font-size: 0.85rem;
}

.project-customer {
  font-size: 0.75rem;
  color: #64748b;
}

.project-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.25rem;
  font-size: 0.75rem;
}

.empty {
  color: #94a3b8;
  font-size: 0.85rem;
  text-align: center;
  padding: 1rem;
}
</style>
```

- [ ] **Step 6: Create TimelineGrid component**

Create `frontend/src/components/TimelineGrid.vue`:

```vue
<script setup lang="ts">
import type { ArchitectUsage, MonthUsage, AssignmentUsage } from '@/types'

defineProps<{
  usageData: ArchitectUsage[]
  months: string[]
}>()

const emit = defineEmits<{
  drop: [architectId: number, month: string, data: { projectId: number; tentative: boolean; defaultUsagePercent: number }]
}>()

function usageColor(total: number): string {
  if (total > 100) return '#fecaca'
  if (total >= 80) return '#fef3c7'
  if (total > 0) return '#dcfce7'
  return 'transparent'
}

function usageLabelColor(total: number): string {
  if (total > 100) return '#dc2626'
  if (total >= 80) return '#d97706'
  if (total > 0) return '#16a34a'
  return '#94a3b8'
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
  if (data.type === 'project') {
    emit('drop', architectId, month, {
      projectId: data.projectId,
      tentative: data.tentative,
      defaultUsagePercent: data.defaultUsagePercent,
    })
  }
}

function getMonthData(architect: ArchitectUsage, month: string): MonthUsage {
  return architect.months[month] ?? { total: 0, assignments: [] }
}
</script>

<template>
  <div class="timeline-grid">
    <table>
      <thead>
        <tr>
          <th class="architect-col">Architect</th>
          <th v-for="month in months" :key="month" class="month-col">
            {{ formatMonth(month) }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="architect in usageData" :key="architect.architectId">
          <td class="architect-col">
            <div class="architect-name">{{ architect.architectName }}</div>
            <div class="architect-region">{{ architect.region }}</div>
          </td>
          <td
            v-for="month in months"
            :key="month"
            class="month-cell"
            :style="{ background: usageColor(getMonthData(architect, month).total) }"
            @dragover="onDragOver"
            @drop="onDrop($event, architect.architectId, month)"
          >
            <div class="cell-content">
              <div
                v-for="(assignment, i) in getMonthData(architect, month).assignments"
                :key="i"
                class="assignment-block"
                :class="{ tentative: assignment.tentative }"
                :title="`${assignment.projectName} (${assignment.customerName}) - ${assignment.usage}%`"
              >
                {{ assignment.projectName }} {{ assignment.usage }}%
              </div>
              <div
                v-if="getMonthData(architect, month).total > 0"
                class="total-label"
                :style="{ color: usageLabelColor(getMonthData(architect, month).total) }"
              >
                {{ getMonthData(architect, month).total }}%
              </div>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.timeline-grid {
  overflow-x: auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

table {
  min-width: max-content;
}

.architect-col {
  position: sticky;
  left: 0;
  background: #f8fafc;
  z-index: 1;
  min-width: 140px;
}

.architect-name { font-weight: 600; }
.architect-region { font-size: 0.75rem; color: #64748b; }

.month-col {
  min-width: 130px;
  text-align: center;
  font-size: 0.8rem;
}

.month-cell {
  vertical-align: top;
  min-height: 60px;
  transition: background 0.15s;
}

.month-cell:hover {
  outline: 2px dashed #3b82f6;
  outline-offset: -2px;
}

.cell-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 50px;
}

.assignment-block {
  font-size: 0.7rem;
  padding: 2px 4px;
  border-radius: 3px;
  background: #bfdbfe;
  border: 1px solid #93c5fd;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assignment-block.tentative {
  background: #fef9c3;
  border: 1px dashed #fbbf24;
}

.total-label {
  font-size: 0.75rem;
  font-weight: 700;
  text-align: right;
  margin-top: auto;
  padding-top: 2px;
}
</style>
```

- [ ] **Step 7: Implement UsageTimelineView**

Replace `frontend/src/views/UsageTimelineView.vue`:

```vue
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUsageStore } from '@/stores/usage'
import { useProjectsStore } from '@/stores/projects'
import { useCustomersStore } from '@/stores/customers'
import { useAssignmentsStore } from '@/stores/assignments'
import TimelineGrid from '@/components/TimelineGrid.vue'
import ProjectSidebar from '@/components/ProjectSidebar.vue'

const usageStore = useUsageStore()
const projectStore = useProjectsStore()
const customerStore = useCustomersStore()
const assignmentStore = useAssignmentsStore()

const regionFilter = ref('')
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
const toMonth = computed(() => addMonths(fromMonth.value, 11))

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
    usageStore.fetchUsage(fromMonth.value, toMonth.value, regionFilter.value || undefined),
    projectStore.fetchAll(),
    customerStore.fetchAll(),
  ])
}

onMounted(loadData)
watch([regionFilter, fromMonth], loadData)

async function onDrop(
  architectId: number,
  _month: string,
  data: { projectId: number; tentative: boolean; defaultUsagePercent: number }
) {
  await assignmentStore.create({
    architectId,
    projectId: data.projectId,
    tentative: data.tentative,
  })
  await loadData()
}
</script>

<template>
  <div class="timeline-view">
    <div class="main-area">
      <div class="header">
        <h1>Usage Timeline</h1>
        <div class="controls">
          <input
            v-model="nameFilter"
            placeholder="Filter by name..."
            data-testid="name-filter"
          />
          <select v-model="regionFilter" data-testid="region-filter">
            <option value="">All regions</option>
            <option value="ANZ">ANZ</option>
            <option value="ASIA">Asia</option>
          </select>
          <label>
            From:
            <input v-model="fromMonth" type="month" />
          </label>
        </div>
      </div>

      <TimelineGrid
        :usage-data="filteredUsage"
        :months="months"
        @drop="onDrop"
      />
    </div>

    <ProjectSidebar
      :projects="projectStore.projects"
      :customers="customerStore.customers"
    />
  </div>
</template>

<style scoped>
.timeline-view {
  display: flex;
  gap: 0;
  height: calc(100vh - 3rem);
}

.main-area {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  flex-shrink: 0;
}

.controls {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.controls label {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
}
</style>
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts`
Expected: All 3 tests pass.

- [ ] **Step 9: Run all frontend tests**

Run: `cd frontend && npx vitest run`
Expected: All tests pass across all view specs.

- [ ] **Step 10: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/stores/usage.ts frontend/src/stores/assignments.ts \
  frontend/src/components/TimelineGrid.vue frontend/src/components/ProjectSidebar.vue \
  frontend/src/views/UsageTimelineView.vue frontend/src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: add Usage Timeline view with drag-and-drop assignment"
```

---

### Task 13: End-to-End Smoke Test

**Files:** None (manual verification only)

- [ ] **Step 1: Start all services**

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run &
cd frontend && npm run dev &
```

Wait for both to start. Backend on http://localhost:8080, frontend on http://localhost:5173.

- [ ] **Step 2: Verify in browser**

Open http://localhost:5173 in a browser. Verify:

1. **Sidebar navigation** works — click each link, each view loads
2. **Architects**: Add an architect (name, email, region). Verify it appears in the table. Edit it. Filter by region. Delete it.
3. **Customers & Projects**: Add a customer. Expand it. Add an ACTIVE project under it. Add a POTENTIAL project with official and plausible dates. Verify status badges.
4. **Backlog**: Verify the potential project appears. Click Activate. Verify it disappears from backlog.
5. **Timeline**: Verify architect rows appear. Drag a project from the sidebar onto an architect's row. Verify the assignment appears in the grid with correct usage percentage.

- [ ] **Step 3: Run full test suites**

```bash
cd backend && ./mvnw test -q
cd frontend && npx vitest run
```

Expected: All backend and frontend tests pass.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: finalize project setup and verify end-to-end"
```
