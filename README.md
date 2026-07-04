# PRPilot

AI-powered code review platform. Listens for GitHub pull request events,
analyzes the actual code changes using RAG over the codebase, and posts
structured review comments back to the PR — built as a Kafka-backed
microservices system deployed to production.

[![webhook-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml)
[![ingestion-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml)
[![review-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml)
[![notification-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/notification-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/notification-service-ci.yml)

## 🚀 Live Demo

**Webhook endpoint:** `https://prpilot-ai-code-review-platform-production.up.railway.app/webhooks/github`

Open a pull request on any connected GitHub repo — PRPilot automatically
fetches the real diff, retrieves semantically similar code context, and
posts a Claude-generated review within ~30 seconds.

## How to connect your own GitHub repo

**1. Add a webhook to your repo**

`github.com/YOUR_USERNAME/YOUR_REPO` → Settings → Webhooks → Add webhook

| Field | Value |
|---|---|
| Payload URL | `https://prpilot-ai-code-review-platform-production.up.railway.app/webhooks/github` |
| Content type | `application/json` |
| Secret | Contact the repo owner for the webhook secret |
| Events | Pull requests only |

**2. Open a pull request — that's it.**

PRPilot will clone the repo, analyze the codebase using semantic search,
fetch the actual PR diff, and post a structured Claude review as a comment.

> **Note:** Works best on public repos. Private repos require additional
> GitHub App configuration not included in this v1 deployment.

## Status

✅ **Backend complete and deployed to production.**

| Service | Status |
|---|---|
| `webhook-service` | ✅ Live — receives GitHub PR webhooks, HMAC-SHA256 verified |
| `ingestion-service` | ✅ Live — clones repos, chunks code, generates embeddings |
| `review-service` | ✅ Live — fetches real PR diff, RAG retrieval, Claude review |
| `notification-service` | ✅ Live — posts review comments back to GitHub PRs |
| `frontend` | ⏳ Planned — React dashboard showing review history |

## Architecture

```
GitHub PR opened
      │
      ▼
webhook-service (:8081)                    ← Railway
  HMAC-SHA256 verified
      │
      ▼ Kafka: pr.events                   ← Confluent Cloud
      │
      ├──────────────────────────────────────────┐
      ▼                                          ▼
ingestion-service (:8082)           review-service (:8083)
  JGit shallow clone                  fetch real PR diff (GitHub API)
  chunk source files                  embed diff as query (Voyage AI)
  embed chunks (Voyage AI)            pgvector similarity search
  store in pgvector                   top-8 chunks + diff → Claude
      │                               structured review generated
      ▼                                          │
Neon Postgres + pgvector              publish to reviews.completed
(code_chunks table)                             │
                                               ▼ Kafka: reviews.completed
                                    notification-service (:8084)
                                      POST PR comment via GitHub API
                                      → 🤖 PRPilot AI Review
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, 4 microservices
- **Messaging:** Apache Kafka (Confluent Cloud)
- **Data:** PostgreSQL + pgvector on Neon (HNSW index, cosine similarity)
- **Schema migrations:** Flyway (versioned globally across services)
- **Repo cloning:** JGit (pure-Java, shallow clones)
- **Embeddings:** Voyage AI (`voyage-code-2`, 1536-dim, code-specialized)
- **AI review:** Claude API (`claude-haiku-4-5`)
- **GitHub integration:** Webhooks (inbound) + REST API (diff fetch + PR comments)
- **Testing:** JUnit 5, Testcontainers, Mockito, Awaitility — 31 tests total
- **CI:** GitHub Actions — 4 workflows, CI-first development
- **Deployment:** Railway (4 services), Neon (Postgres), Confluent Cloud (Kafka)
- **Frontend (planned):** React, TypeScript, Vercel

## Local development

Requires Docker, Java 21, Gradle, and env vars in `~/.zshrc`:

```bash
export VOYAGE_API_KEY="your-voyage-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
export GITHUB_TOKEN_PRPILOT="your-github-pat"   # repo scope
```

Start infrastructure:

```bash
docker compose up -d
```

Run services (each terminal, `source ~/.zshrc` first):

```bash
cd services/webhook-service      && ./gradlew bootRun   # :8081
cd services/ingestion-service    && ./gradlew bootRun   # :8082
cd services/review-service       && ./gradlew bootRun   # :8083
cd services/notification-service && ./gradlew bootRun   # :8084
```

Simulate a webhook locally:

```bash
SECRET="dev-secret-change-me"
BODY='{"action":"opened","pull_request":{"number":1,"title":"Your PR title","user":{"login":"your-username"},"head":{"sha":"abc123","ref":"feature/branch"},"base":{"ref":"main"},"html_url":"https://github.com/your/repo/pull/1"},"repository":{"full_name":"your/repo"}}'
SIGNATURE="sha256=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')"
curl -i -X POST http://localhost:8081/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: pull_request" \
  -H "X-GitHub-Delivery: test-001" \
  -H "X-Hub-Signature-256: $SIGNATURE" \
  -d "$BODY"
```

## Services

### webhook-service
- HMAC-SHA256 signature verification (constant-time, prevents timing attacks)
- Kafka `pr.events` producer keyed by repo for ordering guarantees
- Idempotent producer (`acks=all`, `enable.idempotence=true`)
- **9 tests:** HMAC unit tests + Testcontainers integration tests

### ingestion-service
- `ErrorHandlingDeserializer` prevents infinite retry on malformed messages
- JGit shallow clone (depth=1), 60-line chunking, source file filtering
- Voyage AI batched embedding with exponential backoff retry on rate limits
- pgvector HNSW index for sub-linear approximate nearest-neighbor search
- **11 tests:** smoke test + CodeChunker unit tests (boundaries, filtering, isolation)

### review-service
- Fetches real PR diff via GitHub API (`/pulls/{pr}/files`) — actual changed
  code used as RAG query, not just PR title/metadata
- Separate Kafka consumer group from ingestion
- Query embedding (`input_type: query`) + pgvector cosine similarity search
- Top-8 chunk retrieval + real diff → structured Claude prompt
- Review status tracking: `PENDING` → `COMPLETED` / `FAILED`
- Idempotent: duplicate `deliveryId` skipped to prevent double-billing
- **7 tests:** prompt unit tests + Testcontainers integration tests

### notification-service
- Consumes `reviews.completed`, posts comment via GitHub REST API
- Prepends `🤖 PRPilot AI Review` header for clear attribution
- No database — pure Kafka consumer + HTTP client
- **4 tests:** unit tests + Testcontainers integration test

## Production infrastructure

| Component | Provider | Notes |
|---|---|---|
| 4 Spring Boot services | Railway (Hobby) | Auto-deploy from GitHub on push |
| Postgres + pgvector | Neon | Serverless, free tier, pgvector enabled |
| Kafka | Confluent Cloud | `pr.events` (3 partitions), `reviews.completed` (1 partition) |
| Embeddings | Voyage AI | `voyage-code-2`, 1536-dim, code-specialized |
| AI review | Anthropic | `claude-haiku-4-5` |

## Known limitations / planned improvements

- **Diff truncation:** PRs with diffs >8,000 chars are truncated — a smarter
  approach would prioritize the most-changed files
- **Line-based chunking:** 60-line fixed windows can split functions mid-body;
  AST-aware chunking (tree-sitter / JavaParser) would improve retrieval quality
- **Model cascading:** currently always uses Haiku; upgrading to Sonnet for
  complex PRs would improve review depth
- **Public repos only:** private repo support requires GitHub App installation tokens

## License

MIT