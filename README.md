# 📰 World Trending News — Topic Lab

A Spring Boot web app that polls news **RSS feeds from around the world**, extracts the
**top 10 trending topics**, and lets you **experiment with a tunable filter pipeline** —
previewing the effect of every change before committing it.

---

## What it does

- **Polls 23 worldwide RSS/Atom feeds** in parallel on startup and every 15 minutes,
  caching articles in memory:
  - **Australia (4):** ABC News, Sydney Morning Herald, The Guardian AU, SBS News
  - **Americas (6):** CNN, New York Times, NPR, Washington Post, Fox News, USA Today
  - **Europe (7):** BBC News, The Guardian UK, Deutsche Welle, France 24, Euronews, The Independent, Sky News
  - **Asia / Middle East (6):** Al Jazeera, Times of India, The Japan Times, South China Morning Post, Channel NewsAsia, The Straits Times
- **Top 10 trending topics**, each **clickable** to drill into the source articles
  (headline links straight to the publisher).
- A **live "Topic Lab"**: every filter has an on/off switch and adjustable parameters.
  The **Preview** panel recomputes as you tune; the **Snapshot** panel keeps the results
  of the last *applied* settings so you can compare before/after. Up/down/new badges show
  how each topic moved between the two.

## The filter pipeline

Each filter can be toggled independently and carries its own parameters:

| Filter | What it does | Parameters |
|---|---|---|
| **Stopword removal** | Drops common low-signal words. The word list is **fully editable** and can be saved as the server default or reset. | editable word list |
| **Ignore punctuation** | Strips punctuation so `covid,` and `covid` count together. | — |
| **Collapse plurals** | Treats simple plurals as singular (`banks` → `bank`). | — |
| **Minimum word length** | Discards very short tokens. | min characters |
| **Noun detection (suffix)** | Keeps words with noun-like endings, drops obvious adverbs/verbs. | min length to test |
| **Capitalisation → proper nouns** | Uses **mid-sentence capitalisation** to detect proper nouns; can boost their score or keep *only* proper nouns. | require-proper toggle, score boost × |
| **Title vs content weighting** | Gives headline words more pull than body words. | title weight, content weight |
| **Minimum sources** | Only surfaces topics reported by several outlets. | distinct sources |
| **Recency** | Favours fresh news (exponential half-life) and drops stale articles. | max age (h), half-life (h) |

## Architecture

```
RSS feeds ──► RssFeedClient (Rome) ──► ArticleCacheService (in-memory, scheduled poll)
                                              │
                          FilterSettings ──► TrendingService ──► top-10 TrendingResult
                                              │                        │
                                        StopwordService           ApiController (REST)
                                                                       │
                                                          static SPA (index.html/app.js/style.css)
```

- **Stateless scoring:** `TrendingService.compute(settings)` is a pure function of the
  cache snapshot + settings, so the UI can preview any configuration without side effects.
- The front end holds two states — *applied* (snapshot) and *preview* — and only the
  user's **Apply** click promotes a preview into the snapshot.

## REST API

| Method & path | Purpose |
|---|---|
| `POST /api/trending` | Run the pipeline for a `FilterSettings` body, returns ranked topics + metadata |
| `GET /api/defaults` | Default settings the UI initialises from |
| `GET /api/sources` | Configured feeds grouped by region |
| `GET /api/stopwords` · `POST /api/stopwords` | Read / replace / reset the stopword list |
| `GET /api/status` | Cache freshness (article count, last poll, polling flag) |
| `POST /api/refresh` | Force an immediate re-poll |

## Run locally

Requires JDK 17+.

```bash
mvn spring-boot:run
# then open http://localhost:8080
```

Or build and run the jar:

```bash
mvn clean package
java -jar target/trending-news.jar
```

## Run with Docker

```bash
docker build -t trending-news .
docker run -p 8080:8080 trending-news
# open http://localhost:8080
```

The container reads `$PORT` (defaults to 8080) so it runs unchanged on most PaaS hosts.

## Deploy to Render

This repo includes a [`render.yaml`](./render.yaml) Blueprint and a Dockerfile, so deploying
is one of:

1. **Blueprint (recommended):** push this repo to GitHub, then in Render choose
   **New + → Blueprint** and point it at the repo. Render reads `render.yaml`, builds the
   Dockerfile, and wires up the health check at `/api/status`.
2. **Manual web service:** **New + → Web Service**, pick the repo, select **Docker** as the
   runtime. No build/start commands needed — the Dockerfile handles everything, and Render's
   injected `$PORT` is honoured automatically.

The free plan works fine; the first poll completes within ~30 s of boot.

## Notes

- Feeds occasionally change URLs or rate-limit; the poller logs and skips any it can't
  fetch, so the app stays up even if a source is temporarily unavailable.
- All article text is HTML-stripped (jsoup) before tokenisation.
- **Restricted networks:** some sandboxes/proxies return HTTP 403 for publisher domains.
  In that case the app still starts and serves the UI/API normally — it simply shows no
  topics until it can reach the feeds. On Render (open egress) the feeds load within ~30 s.
