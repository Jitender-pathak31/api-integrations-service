# API Integrations Service (Spring Boot 4 + Java 17)

A clean, production-style Spring Boot backend service that demonstrates how to **call (abrufen)** data from a third-party REST API, **transform & edit (bearbeiten)** the data locally, and **re-expose (wieder bereitstellen)** the curated data through your own REST endpoints.

---

## Architecture & Data Lifecycle

```text
[3rd-Party API: JSONPlaceholder]
       │  (1) CALL via Spring RestClient (ExternalPostClient)
       ▼
[ExternalPostDto (Java Record)]
       │  (2) TRANSFORM & ENRICH (IntegratedPost.fromExternal)
       │      - Capitalizes title & cleans line breaks
       │      - Calculates wordCount & 80-char summary
       ▼
[Local H2 Database (IntegratedPost JPA Entity)]
       │  (3) EDIT / CURATE via PUT /api/integrations/posts/{id}
       │      - Modify title, body, curatorNotes, or set approved = true
       ▼
[Downstream Consumers]
          (4) WIEDER BEREITSTELLEN via GET /api/integrations/posts?approvedOnly=true
```

---

## Project Structure

```text
api-integrations-service/
├── README.md                           # Architecture, endpoints, and manual test guide
├── pom.xml                             # WebMVC + RestClient + Data JPA + H2
└── src/
    ├── main/java/com/example/integration/
    │   ├── ApiIntegrationApplication.java    # Main Spring Boot entrypoint
    │   ├── ExternalPostDto.java              # Java record for raw 3rd-party JSON
    │   ├── ExternalPostClient.java           # Spring RestClient wrapper for 3rd-party calls
    │   ├── IntegratedPost.java               # Rich JPA Entity (transformation + edit logic)
    │   ├── IntegratedPostRepository.java     # Spring Data JPA Repository
    │   ├── PostEditRequest.java              # Java record for PUT edit requests
    │   └── IntegrationController.java        # REST Controller (/api/integrations/posts)
    ├── main/resources/
    │   └── application.properties            # Runs on Port 8081 + external API URL config
    └── test/java/com/example/integration/
        └── IntegrationControllerTest.java    # Offline test verifying Sync -> Edit -> Serve
```

---

## REST API Endpoints (Port `8081`)

Base URL: `http://localhost:8081/api/integrations/posts`

| Stage | Method | Endpoint | Description |
| :--- | :--- | :--- | :--- |
| **1. Call (Stateless)** | `GET` | `/live-preview/{externalId}` | Calls the 3rd-party API and returns the transformed post immediately **without** saving to DB. |
| **1. Call & Sync** | `POST` | `/sync/{externalId}` | Calls the 3rd-party API for one post, transforms it, and **saves/updates** it in our local DB. |
| **1. Batch Sync** | `POST` | `/sync-batch?limit=5` | Calls the 3rd-party API for top $N$ posts, transforms them, and saves them locally. |
| **2. Edit Locally** | `PUT` | `/{id}` | Edits `title`, `body`, `curatorNotes`, or `approved` status of a locally synced post (and recalculates `wordCount` & `summary`). |
| **3. Wieder bereitstellen** | `GET` | `/` | Re-exposes all integrated posts (or pass `?approvedOnly=true` to return only approved posts). |
| **3. Wieder bereitstellen** | `GET` | `/{id}` | Re-exposes a single integrated post by its local database ID. |
| **Cleanup** | `DELETE` | `/{id}` | Deletes a locally stored post. |

---

## How to Run and Test Manually

### Step 1: Start the Service

From the project root directory:

```bash
./mvnw spring-boot:run
```

*(Runs on `http://localhost:8081` by default.)*

---

### Step 2: Walk Through the Full Lifecycle with `curl`

Open a second terminal and run these commands in order:

#### A. Live Preview a 3rd-Party Post (Stateless Fetch + Transform)
Fetches post `#1` from `https://jsonplaceholder.typicode.com/posts/1` and shows our enriched format (`wordCount`, `summary`, normalized `title`) without saving:
```bash
curl -s http://localhost:8081/api/integrations/posts/live-preview/1
```

#### B. Sync (Import) Posts from the 3rd-Party API into Our Database
Import external post `#1` singly, or import a batch of 3 posts:
```bash
# Sync single external post #1
curl -i -X POST http://localhost:8081/api/integrations/posts/sync/1

# Sync top 3 posts from the 3rd-party API
curl -i -X POST "http://localhost:8081/api/integrations/posts/sync-batch?limit=3"
```

#### C. Edit & Approve the Imported Data Locally (`PUT`)
Now modify local post `#1` — give it a clean English title, custom body, curator notes, and mark `"approved": true`:
```bash
curl -i -X PUT http://localhost:8081/api/integrations/posts/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spring Boot API Integration Guide",
    "body": "We fetched this record from a third-party API, edited its content locally, and re-exposed it.",
    "curatorNotes": "Reviewed and approved for public API",
    "approved": true
  }'
```
*(Notice how `wordCount` automatically updates to `16` and `summary` updates to match your new `body`!)*

#### D. Wieder bereitstellen (Re-expose the Curated Data)
```bash
# 1. Fetch ALL locally stored posts (both approved and unapproved)
curl -s http://localhost:8081/api/integrations/posts

# 2. Fetch ONLY curated & approved posts for downstream consumers
curl -s "http://localhost:8081/api/integrations/posts?approvedOnly=true"
```

---

### Step 3: Inspect the Local H2 Database Console
1. Open `http://localhost:8081/h2-console` in your browser.
2. Connect using H2's standard in-memory defaults (**JDBC URL**: `jdbc:h2:mem:integrationsdb`, default H2 user `sa`, no password).
3. Run `SELECT * FROM INTEGRATED_POST;` to see both your edited and unedited records.

