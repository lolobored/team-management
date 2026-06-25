# Team Management Tool — Design Spec

## Overview

A lightweight web application for managing a team of Solution Architects and their assignments to customer projects. The tool provides visibility into architect utilization over time, supports planning with potential future projects, and allows drag-and-drop assignment directly from a usage timeline.

## Context

- Team size: ~6 architects, not expected to exceed 10 within 3 years
- Architects typically handle 3-4 projects in parallel
- Roughly 18-24 active projects at any given time
- Architects are based in two regions: ANZ and Asia
- No authentication required initially; architecture should allow adding Spring Security later

## Data Model

### Architect

| Field    | Type          | Notes                  |
|----------|---------------|------------------------|
| id       | Long (PK)     | Auto-generated         |
| name     | String        | Required               |
| email    | String        | Optional               |
| region   | Enum          | ANZ, ASIA              |

### Customer

| Field   | Type      | Notes          |
|---------|-----------|----------------|
| id      | Long (PK) | Auto-generated |
| name    | String    | Required       |
| country | String    | Optional       |
| city    | String    | Optional       |

### Project

| Field               | Type      | Notes                                              |
|---------------------|-----------|------------------------------------------------------|
| id                  | Long (PK) | Auto-generated                                      |
| customerId          | Long (FK) | References Customer                                 |
| name                | String    | Required                                            |
| startDate           | Date      | Required for ACTIVE projects. For POTENTIAL projects, set upon activation (defaults from plausibleDate if available, otherwise officialDate) |
| durationMonths      | Integer   | Required                                            |
| defaultUsagePercent | Integer   | Default usage % for assignments (e.g., 20)          |
| status              | Enum      | ACTIVE, POTENTIAL                                   |
| officialDate        | Date      | Nullable — expected start date communicated by pre-sales (for POTENTIAL projects) |
| plausibleDate       | Date      | Nullable — realistic start date estimate, typically later than officialDate (for POTENTIAL projects) |

**Timeline calculation for POTENTIAL projects**: When a potential project has tentative assignments, the timeline uses `plausibleDate` (falling back to `officialDate`) as the effective start date. Upon activation, the user confirms or adjusts the `startDate`.

### Assignment

| Field        | Type      | Notes                                                      |
|--------------|-----------|--------------------------------------------------------------|
| id           | Long (PK) | Auto-generated                                              |
| architectId  | Long (FK) | References Architect                                        |
| projectId    | Long (FK) | References Project                                          |
| usagePercent | Integer   | Nullable — falls back to project's defaultUsagePercent if null |
| tentative    | Boolean   | True for pre-assignments on POTENTIAL projects               |

### Key Relationships

- Customer has many Projects
- Project has many Assignments
- Architect has many Assignments
- An architect's total usage for a given month = sum of usagePercent across all assignments that overlap that month

## Tech Stack

### Backend — Spring Boot 3

- Java 21
- Spring Data JPA for database access
- Flyway for database schema migrations
- REST controllers serving JSON

### Database — PostgreSQL 16

- Runs in Docker Compose
- Single schema, four tables
- Volume-mounted for data persistence across container restarts

### Frontend — Vue 3

- Vue 3 with Composition API and TypeScript
- Vite as build tool and dev server
- Vue Router for view navigation
- Pinia for state management
- Timeline/Gantt component for the usage view with drag-and-drop support

### Development Setup — Docker Compose

- PostgreSQL container (port 5432)
- Spring Boot runs locally via `./mvnw spring-boot:run` or IDE
- Vue dev server via `npm run dev` with Vite proxy forwarding `/api` requests to Spring Boot

## API Design

### Architects

| Method | Endpoint              | Purpose          |
|--------|-----------------------|------------------|
| GET    | /api/architects       | List all         |
| POST   | /api/architects       | Create           |
| GET    | /api/architects/{id}  | Read one         |
| PUT    | /api/architects/{id}  | Update           |
| DELETE | /api/architects/{id}  | Delete           |

### Customers

| Method | Endpoint              | Purpose          |
|--------|-----------------------|------------------|
| GET    | /api/customers        | List all         |
| POST   | /api/customers        | Create           |
| GET    | /api/customers/{id}   | Read one         |
| PUT    | /api/customers/{id}   | Update           |
| DELETE | /api/customers/{id}   | Delete           |

### Projects

| Method | Endpoint                      | Purpose                                  |
|--------|-------------------------------|------------------------------------------|
| GET    | /api/projects                 | List all (filterable by customerId, status) |
| POST   | /api/projects                 | Create                                   |
| GET    | /api/projects/{id}            | Read one                                 |
| PUT    | /api/projects/{id}            | Update                                   |
| DELETE | /api/projects/{id}            | Delete                                   |
| PATCH  | /api/projects/{id}/activate   | Convert POTENTIAL to ACTIVE              |

### Assignments

| Method | Endpoint                | Purpose                                        |
|--------|-------------------------|------------------------------------------------|
| GET    | /api/assignments        | List all (filterable by architectId, projectId) |
| POST   | /api/assignments        | Create                                         |
| GET    | /api/assignments/{id}   | Read one                                       |
| PUT    | /api/assignments/{id}   | Update                                         |
| DELETE | /api/assignments/{id}   | Delete                                         |

### Usage (Computed)

| Method | Endpoint    | Purpose                                                  |
|--------|-------------|----------------------------------------------------------|
| GET    | /api/usage  | Per-architect, per-month usage. Params: from, to, region (optional), architectId (optional) |

Response shape:

```json
[
  {
    "architectId": 1,
    "architectName": "Laurent",
    "region": "ANZ",
    "months": {
      "2026-06": {
        "total": 40,
        "assignments": [
          { "projectId": 1, "projectName": "Acme KYC", "customerName": "Acme", "usage": 20, "tentative": false },
          { "projectId": 2, "projectName": "Beta Onboarding", "customerName": "Beta Corp", "usage": 20, "tentative": true }
        ]
      }
    }
  }
]
```

## UI Views

### 1. Architects

Simple table listing all architects. Supports add, edit, and delete. Filterable by region (ANZ/Asia).

Fields displayed: name, email, region.

### 2. Customers & Projects

Hierarchical view. List of customers, each expandable to reveal their projects.

- Add/edit/delete customers
- Add/edit/delete projects under a customer
- Projects display a status badge (ACTIVE / POTENTIAL)
- Potential projects show both official and plausible dates
- Filterable by customer name or project status

### 3. Usage Timeline (Main Planning View)

The primary view for day-to-day planning.

- **Rows**: One per architect, filterable by region (ANZ/Asia) and by name
- **Columns**: Months — defaults to current month + 11 months ahead, scrollable
- **Cells**: Show project blocks with usage percentage
  - Active assignments: solid colored blocks
  - Tentative assignments: dashed/faded blocks
  - Total usage per cell, color-coded: green (<80%), amber (80-100%), red (>100% = overbooked)
- **Drag-and-drop**: A sidebar panel lists available projects (both active and potential). Drag a project onto an architect's row to create an assignment. Drag existing blocks to move or reassign them.
- **Potential projects** in the sidebar are available for tentative pre-assignment

### 4. Project Backlog

Focused view of POTENTIAL projects only.

- Shows official date vs. plausible date side by side
- Quick action button to convert a project to ACTIVE status
- Useful for forward planning and pipeline review meetings

## Future Considerations (Not in Scope)

These items are explicitly deferred but the architecture should not prevent them:

- Authentication via Spring Security
- Additional architect fields (skills, availability, leave)
- Reporting and export (PDF, CSV)
- Notifications (e.g., overbooked architect alerts)
