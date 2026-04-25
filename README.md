# Job Aggregator

A personal, single-user job-hunting dashboard. Aggregates Java/Fintech listings from multiple sources, parses your resume with Claude, scores every job against it, and lets you track applications — all in one place.

---

## Why This Exists

Checking LinkedIn, Adzuna, and other portals every day is tedious — and even when you find a relevant role, deciding whether it's worth applying takes more reading. This tool pulls jobs into one feed, follows each listing's detail page to grab the full job description, and lets Claude score every job against your uploaded resume. You see a `% match`, the skills you have vs the ones you'd need to add, and an explicit experience-fit signal — so you spend time only on jobs that are actually worth applying to.

---

## Features

### Job Feed
- Aggregates jobs from **LinkedIn**, **Adzuna**, and **Remotive**, scheduled every 4 hours
- **Bangalore / Fintech** tabs for quick scoping
- Full job descriptions pulled per listing — LinkedIn via the `/jobPosting/{id}` endpoint, Adzuna via the embedded `window["az_details"]` JS object on the detail page
- **Match score** badge per card (e.g. `85% match`), color-graded: green ≥61, yellow 26–60, red ≤25
- **Experience-fit pill** — `Stretch +1y`, `Needs 7+y · You: 5y`, `Overqualified`, or `Exp. not stated`. Score is hard-capped when underqualified (gap=1 → ≤60, gap≥2 → ≤25)
- **Matched skills** rendered green; **missing skills** (in the JD but not on your resume) rendered grey/struck-through
- **Mark Seen** / **Bookmark** / **Mark as Applied** (pre-fills the application form) / **Score** (per-job rescore for jobs that timed out in a batch run)
- Sorted by match score desc, then posted date

### Resume / My Details
- Upload **PDF or DOCX** — Apache Tika extracts text, Claude (Sonnet) parses it into structured fields: `skills`, `primaryStack`, `yearsOfExperience`, `seniority`, `summary`
- Skill chips, years, seniority, stack, and summary visible on the **My Details** tab
- **Editable** — fix anything Claude got wrong; saving triggers an async re-score of all unscored / unseen / IN jobs
- Personal-details section (name/email/phone/links) with copy-to-clipboard for filling forms quickly

### Applied Jobs
- Full application tracker with status: `Awaiting`, `Interview Round`, `Coding Assessment`, `Rejected`, `No Callback`
- Add applications manually or import CSV (additive; duplicates skipped by company+role+date)
- Inline edit per row (status, date, interview type, remarks)
- Filter pills, column filters (location, interview), search by company, sort by date
- Stat cards per status (always reflects total, not the active filter)
- Pagination at 20/page

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.x |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Scheduler | Spring `@Scheduled` (every 4 hours) |
| LinkedIn / Adzuna scraping | Jsoup (HTML parsing) |
| Adzuna feed | Adzuna REST API (free tier) |
| Remotive feed | Remotive REST API |
| Resume parsing | Apache Tika 2.x (PDF + DOCX) |
| LLM (resume + job matching) | Claude (via `claude` CLI in dev; Anthropic Java SDK 2.17 also wired) |
| CSV parsing | OpenCSV |
| Build | Maven |
| Frontend | React 18 + Vite |
| HTTP client | Axios |
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
│       │   ├── config/
│       │   │   └── AnthropicConfig.java            # Anthropic SDK client bean (unused while CLI harness is active)
│       │   ├── controller/
│       │   │   ├── JobController.java              # GET /api/jobs, PATCH seen/bookmark, POST ingest, /score-all, /{id}/rescore
│       │   │   ├── ApplicationController.java      # CRUD + CSV upload for applications
│       │   │   └── ProfileController.java          # GET/PUT profile, POST /resume
│       │   ├── model/
│       │   │   ├── Job.java                        # jobs entity (incl. match_score / experience_fit / matched_skills etc.)
│       │   │   ├── Application.java                # applications entity
│       │   │   └── Profile.java                    # single-row profile (id=1) incl. resume fields
│       │   ├── repository/
│       │   │   ├── JobRepository.java              # filters + scoring-candidates query
│       │   │   ├── ApplicationRepository.java      # filters + CSV dedup check
│       │   │   └── ProfileRepository.java
│       │   ├── service/
│       │   │   ├── JobIngestionService.java        # Orchestrates sources, dedups, enriches, saves, scores
│       │   │   ├── LinkedInService.java            # Listing scrape + per-job /jobPosting/{id} enrichment (parallel)
│       │   │   ├── AdzunaService.java              # API call + detail-page enrichment via window["az_details"] (throttled)
│       │   │   ├── RemotiveService.java            # Remotive REST API, India-accessibility filter
│       │   │   ├── ResumeAnalysisService.java      # Tika → Claude (Sonnet) → Profile fields
│       │   │   ├── JobMatchingService.java         # Claude (Haiku) batched scoring + experience-gating
│       │   │   └── ClaudeCliRunner.java            # `claude -p --output-format json --model X --max-turns 1` wrapper
│       │   ├── scheduler/
│       │   │   └── IngestionScheduler.java         # @Scheduled(fixedRate = 4h)
│       │   └── util/
│       │       ├── SkillExtractor.java             # Regex match against ~100 known tech skills
│       │       └── DomainClassifier.java           # 'fintech' vs 'other' from company + title + description
│       └── resources/
│           └── application.yml                     # DB config, Adzuna keys, anthropic.api-key (env var)
└── frontend/
    ├── vite.config.js                              # Proxies /api → localhost:8080
    ├── index.html
    └── src/
        ├── App.jsx                                 # Tabs, job feed state, modal control
        ├── main.jsx
        ├── index.css
        ├── api/
        │   ├── jobs.js                             # getJobs, markSeen, toggleBookmark, triggerIngestion, rescoreJob
        │   ├── applications.js                     # getApplications, addApplication, updateStatus, uploadCsv
        │   └── profile.js                          # getProfile, saveProfile, uploadResume
        ├── context/
        │   └── ProfileContext.jsx                  # Loads profile once, shares it across tabs
        └── components/
            ├── FilterBar.jsx                       # Keyword, source, show/hide seen, fetch now
            ├── JobCard.jsx                         # Match score badge, exp pill, matched/missing skills, actions
            ├── ApplicationsTab.jsx                 # Applications tracker UI
            ├── AddApplicationModal.jsx             # Add/pre-fill application modal
            ├── ColumnFilter.jsx                    # Reusable multi-select column filter
            └── MyDetailsTab.jsx                    # Profile + resume upload + editable parsed summary
```

---

## Database Schema

### `jobs`
| Column | Type | Notes |
|---|---|---|
| id | SERIAL PK | |
| title, company, location | VARCHAR(255) | |
| url | TEXT UNIQUE | Used for deduplication |
| source | VARCHAR(50) | `linkedin`, `adzuna`, `remotive` |
| domain | VARCHAR(50) | `fintech` or `other` |
| country | VARCHAR(10) | `IN` (Remotive jobs are stamped `IN` since they're remote-from-India eligible) |
| description | TEXT | Full text after enrichment (HTML-stripped); falls back to API snippet on enrichment failure |
| skills | TEXT | Comma-separated extracted skills (from `SkillExtractor`) |
| posted_date, ingested_at | TIMESTAMP | |
| is_seen, is_bookmarked | BOOLEAN | |
| **match_score** | INT | 0–100, after experience-gating |
| **match_skill_score** | INT | 0–100, raw skill overlap from Claude |
| **matched_skills** | TEXT | Comma-separated skills present in both resume and JD |
| **missing_skills** | TEXT | Comma-separated skills in JD but not on resume |
| **years_required_min/max** | INT | Parsed from JD; null if not stated |
| **experience_fit** | VARCHAR(20) | `match` / `underqualified` / `overqualified` / `unknown` |
| **experience_gap_years** | INT | How many years short (0 if matched/over) |
| **match_rationale** | TEXT | One-line explanation from Claude |
| **match_computed_at** | TIMESTAMP | |

### `applications`
| Column | Type | Notes |
|---|---|---|
| id | SERIAL PK | |
| company, role, location | VARCHAR(255) | |
| applied_date | VARCHAR(100) | text, format `YYYY-MM-DD` |
| status | VARCHAR(50) | Awaiting / Interview Round / Coding Assessment / Rejected / No Callback |
| interview, mode_of_application | VARCHAR(100) | |
| remarks, status_check_url | TEXT | |
| created_at | TIMESTAMP | |

### `profile` (single row, `id = 1`)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Always `1` |
| name, email, phone | VARCHAR | |
| address, linkedin_url, portfolio_url, resume_url | TEXT | |
| **resume_filename** | VARCHAR(255) | |
| **resume_uploaded_at** | TIMESTAMP | |
| **resume_text** | TEXT | Raw text extracted by Tika (capped 50,000 chars) |
| **resume_skills** | TEXT | Comma-separated, from Claude |
| **resume_stack** | VARCHAR(255) | e.g. `Java/Spring Boot fintech backend` |
| **resume_years_of_experience** | INT | |
| **resume_seniority** | VARCHAR(50) | Junior / Mid-level / Senior / Lead / Staff / Principal |
| **resume_summary** | TEXT | 1–2 sentences from Claude |

---

## Setup & Running

### Prerequisites
- Java 17+
- Maven
- Node.js 18+
- PostgreSQL running locally
- **Claude Code CLI** (`claude` on `PATH`) — used as the LLM harness in dev. Authenticate once via `claude` and pick your subscription. *Or* an `ANTHROPIC_API_KEY` env var if you swap `ClaudeCliRunner` out for the Anthropic Java SDK calls.

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

anthropic:
  api-key: ${ANTHROPIC_API_KEY:}   # env var fallback; empty is fine in CLI-harness mode
```
Get free Adzuna API keys at [developer.adzuna.com](https://developer.adzuna.com).

### 3. Start the backend
```bash
cd backend
mvn spring-boot:run
```
Runs on `http://localhost:8080`. Hibernate auto-creates tables (and adds new columns on schema change) via `ddl-auto: update`.

### 4. Start the frontend
```bash
cd frontend
npm install
npm run dev
```
Runs on `http://localhost:5173`.

### 5. Upload a resume + fetch jobs
1. Open http://localhost:5173 → **My Details** → click **Upload Resume**, pick a PDF/DOCX. Wait ~10–20s for Sonnet to parse it.
2. Click **Fetch Now** in the Job Feed (or wait for the 4h scheduler). New jobs are scored against your resume automatically — sorted by match score.

---

## API Endpoints

### Jobs
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/jobs` | Get jobs with filters: `keyword`, `source`, `domain`, `hideSeen`, `location` |
| PATCH | `/api/jobs/{id}/seen` | Mark job as seen |
| PATCH | `/api/jobs/{id}/bookmark` | Toggle bookmark |
| POST | `/api/jobs/ingest` | Trigger ingestion synchronously |
| GET | `/api/jobs/score-all` | Re-score all unscored / unseen / IN jobs against the current resume (async) |
| POST | `/api/jobs/{id}/rescore` | Score a single job (sync) — useful for jobs that timed out in batch runs |

### Profile
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/profile` | Get the single profile row |
| PUT | `/api/profile` | Save profile (auto re-scores jobs if any resume field changed) |
| POST | `/api/profile/resume` | Multipart upload — Tika + Claude parse → save to profile + async re-score |

### Applications
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/applications` | Filters: `status`, `company`, `sort` |
| POST | `/api/applications` | Add a single application |
| PATCH | `/api/applications/{id}/status` | Update status, interview, remarks, etc. |
| DELETE | `/api/applications/{id}` | Delete an application |
| POST | `/api/applications/upload` | Import CSV (deduped by company+role+date) |

---

## Job Sources

| Source | Method | Notes |
|---|---|---|
| LinkedIn | Jsoup scrape of `seeMoreJobPostings` (`f_TPR=r604800`, last 7 days, mid-senior) → per-job enrichment via `/jobPosting/{id}` (parallel, 8 threads) | No login. Listings cap ~25 per call. |
| Adzuna | REST API (Bangalore + India fintech queries) → per-new-job enrichment by scraping `adzuna.in/details/{id}` and reading `window["az_details"].description` | Throttled at 1 req/sec to stay polite. Free tier limit 250 req/day. |
| Remotive | REST API, software-dev category, `java backend` query | Filtered to listings accessible from India (excludes US-only / EU-only / etc.) |

**Enrichment timing tradeoffs**:
- LinkedIn enriches inline in `fetchJobs()` (parallel, fast — ~5–10s extra per ingestion run)
- Adzuna enriches AFTER dedup, only for *new* jobs — avoids 1s/job waste on duplicates
- Remotive's API gives full descriptions natively; no scraping needed

---

## How the Match Scoring Works

1. **Resume parsing** (one-shot per upload, Sonnet via CLI)
   - Tika extracts raw text from the uploaded PDF/DOCX
   - Sonnet returns structured JSON: `skills`, `primaryStack`, `yearsOfExperience`, `seniority`, `summary`
   - Saved to the `profile` row (id=1)

2. **Per-job scoring** (Haiku via CLI, batched 5 at a time)
   - Prompt includes: candidate profile + the 5 jobs (title/company/description truncated to 2000 chars + existing skill tags)
   - Claude returns per-job: `yearsRequiredMin/Max`, `experienceFit`, `skillScore` (0-100), `matchedSkills`, `missingSkills`, `rationale`
   - Backend applies **deterministic experience gating** in Java (not in Claude's head):
     - gap = 0 (or unknown) → final score = skill score
     - gap = 1 → cap at 60 (yellow "Stretch")
     - gap ≥ 2 → cap at 25 (red "Hard pass")

3. **When scoring runs**
   - At ingestion: only on newly-saved jobs
   - On resume upload OR resume edit: async re-score of `match_score IS NULL AND is_seen = false AND country = 'IN'` (i.e. jobs you haven't dismissed and that would actually appear in the feed)
   - On demand: `GET /api/jobs/score-all` (rescore all candidates) or `POST /api/jobs/{id}/rescore` (single job, useful when batch hits the 300s CLI timeout)

---

## Future Ideas

- Swap `ClaudeCliRunner` for the Anthropic Java SDK + prompt caching (the resume + rubric goes in the cached system prompt, ~60% input-token savings on batched scoring)
- Daily email digest of top 5 unseen matches
- Browser extension to mark "applied" from the LinkedIn / company career page directly
- Smarter Adzuna throttle (back off on 429 and resume) instead of fixed 1s sleep
- Pagination of LinkedIn listings (currently `start=0` only, ~25 results per query)
