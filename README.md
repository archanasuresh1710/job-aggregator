# Job Aggregator

A personal job hunting dashboard built to aggregate Java/Fintech job listings from multiple sources, track applications, and cut down the time spent checking multiple job portals.

---

## Why This Exists

Checking LinkedIn, Adzuna, and Naukri separately every day is tedious. This tool pulls all relevant jobs into one place, filters them by domain and source, and lets you track every application — all from a single dashboard.

---

## Features

### Job Feed
- Aggregates jobs from **LinkedIn** and **Adzuna** automatically every 4 hours
- **Domain tabs** — Fintech vs Other, so high-priority roles are always visible first
- **Skill tags** extracted from job descriptions (Java, Spring Boot, Kafka, AWS, etc.)
- Filter by keyword, source, and domain
- Hide seen jobs by default — toggle to show them back
- **Mark as Seen** and **Bookmark** on each card
- **Mark as Applied** — opens a pre-filled application form and removes the job from feed

### Applied Jobs
- Full application tracker with status: `Applied`, `Interview Round`, `Rejected`, `No Callback`
- Add applications manually via form or import from CSV
- Inline status/interview/remarks editing per row
- Filter by status, search by company name
- Sort by date applied (asc/desc)
- Stat cards showing counts per status — always consistent regardless of active filter
- Company names link directly to the company's application status portal (where configured)
- CSV import is additive — duplicates are skipped, existing data is never wiped

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.x |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Scheduler | Spring `@Scheduled` (every 4 hours) |
| LinkedIn Scraping | Jsoup (HTML parser) |
| Adzuna | Adzuna REST API (free tier) |
| CSV Parsing | OpenCSV |
| Build | Maven |
| Frontend | React 18 + Vite |
| HTTP Client | Axios |
| Styling | Plain CSS |

---

## Project Structure

```
job-aggregator/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/archana/jobs/
│       │   ├── JobAggregatorApplication.java       # Entry point, enables scheduling
│       │   ├── controller/
│       │   │   ├── JobController.java              # GET /api/jobs, PATCH seen/bookmark, POST ingest
│       │   │   └── ApplicationController.java      # CRUD + CSV upload for applications
│       │   ├── model/
│       │   │   ├── Job.java                        # Jobs table entity
│       │   │   └── Application.java                # Applications table entity
│       │   ├── repository/
│       │   │   ├── JobRepository.java              # Native query with keyword/source/domain/hideSeen filters
│       │   │   └── ApplicationRepository.java      # Filters + dedup check
│       │   ├── service/
│       │   │   ├── JobIngestionService.java        # Orchestrates all sources, deduplicates by URL
│       │   │   ├── LinkedInService.java            # Scrapes LinkedIn guest jobs API via Jsoup
│       │   │   ├── AdzunaService.java              # Calls Adzuna REST API, filters irrelevant titles
│       │   │   └── NaukriService.java              # Disabled — Naukri's internal API is unstable
│       │   ├── scheduler/
│       │   │   └── IngestionScheduler.java         # Triggers ingestion every 4 hours
│       │   └── util/
│       │       ├── SkillExtractor.java             # Scans text for ~80 known tech skills
│       │       └── DomainClassifier.java           # Classifies jobs as 'fintech' or 'other'
│       └── resources/
│           ├── application.yml                     # DB config, Adzuna API keys, server port
│           └── resume/
│               ├── resume1.pdf                     # Active resume (for future AI match analysis)
│               └── resume2.pdf
└── frontend/
    ├── vite.config.js                              # Proxies /api → localhost:8080
    ├── index.html
    └── src/
        ├── App.jsx                                 # Root — tabs, job feed state, modal control
        ├── main.jsx
        ├── index.css
        ├── api/
        │   ├── jobs.js                             # getJobs, markSeen, toggleBookmark, triggerIngestion
        │   └── applications.js                     # getApplications, addApplication, updateStatus, uploadCsv
        └── components/
            ├── FilterBar.jsx                       # Keyword, source, show/hide seen, fetch now
            ├── JobCard.jsx                         # Job card with skills, actions
            ├── ApplicationsTab.jsx                 # Full applications tracker UI
            └── AddApplicationModal.jsx             # Add/pre-fill application form modal
```

---

## Database Schema

### `jobs`
| Column | Type | Notes |
|---|---|---|
| id | SERIAL PK | |
| title | VARCHAR(255) | |
| company | VARCHAR(255) | |
| location | VARCHAR(255) | |
| url | TEXT UNIQUE | Used for deduplication |
| source | VARCHAR(50) | `linkedin`, `adzuna` |
| domain | VARCHAR(50) | `fintech` or `other` |
| description | TEXT | |
| skills | TEXT | Comma-separated extracted skills |
| posted_date | TIMESTAMP | |
| ingested_at | TIMESTAMP | Defaults to now |
| is_seen | BOOLEAN | Default false |
| is_bookmarked | BOOLEAN | Default false |

### `applications`
| Column | Type | Notes |
|---|---|---|
| id | SERIAL PK | |
| company | VARCHAR(255) | |
| role | VARCHAR(255) | |
| applied_date | VARCHAR(100) | Stored as text, format `YYYY-MM-DD` |
| location | VARCHAR(255) | |
| status | VARCHAR(50) | Applied / Interview Round / Rejected / No Callback |
| interview | VARCHAR(100) | Yes / No / Coding Assessment |
| remarks | TEXT | |
| mode_of_application | VARCHAR(100) | LinkedIn / Naukri / Foundit / Referral / Career Page / Other |
| status_check_url | TEXT | Optional — company portal link |
| created_at | TIMESTAMP | |

---

## Setup & Running

### Prerequisites
- Java 17+
- Maven
- Node.js 18+
- PostgreSQL running locally

### 1. Create the database
```sql
CREATE DATABASE jobdb;
```

### 2. Configure credentials
Edit `backend/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/jobdb
    username: postgres
    password: your_password

adzuna:
  app-id: "your_adzuna_app_id"
  app-key: "your_adzuna_app_key"
```

Get free Adzuna API keys at [developer.adzuna.com](https://developer.adzuna.com).

### 3. Start the backend
```bash
cd backend
mvn spring-boot:run
```
Runs on `http://localhost:8080`. Hibernate auto-creates tables on first run.

### 4. Start the frontend
```bash
cd frontend
npm install
npm run dev
```
Runs on `http://localhost:5173`.

### 5. Fetch jobs
Open the app and click **Fetch Now**, or wait for the scheduler to run (every 4 hours).

---

## API Endpoints

### Jobs
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/jobs` | Get jobs with filters: `keyword`, `source`, `domain`, `hideSeen` |
| PATCH | `/api/jobs/{id}/seen` | Mark job as seen |
| PATCH | `/api/jobs/{id}/bookmark` | Toggle bookmark |
| POST | `/api/jobs/ingest` | Manually trigger ingestion |

### Applications
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/applications` | Get applications with filters: `status`, `company`, `sort` |
| POST | `/api/applications` | Add a single application |
| PATCH | `/api/applications/{id}/status` | Update status, interview, remarks |
| DELETE | `/api/applications/{id}` | Delete an application |
| POST | `/api/applications/upload` | Import CSV (deduplicates by company+role+date) |

---

## Job Sources

| Source | Method | Notes |
|---|---|---|
| LinkedIn | Jsoup scraping of guest jobs API | No login required. May rate-limit. |
| Adzuna | REST API | Free tier: 250 req/day. Requires API key. |
| Naukri | Disabled | Internal API endpoint is unstable. |

---

## Planned (Phase 2)

- **AI Resume Match** — compare each job description against resumes in `resources/resume/` using Claude API. Returns match score, matched skills, missing skills, and tailoring advice.
- **Redis deduplication cache**
- **Email digest** — daily top 5 matches
- **"Applied" status tracking** from browser extension
