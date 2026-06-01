# 📰 World Trending News — Topic Lab

A Spring Boot web app that polls news **RSS feeds from around the world**, extracts the
**top 10 trending topics**, and lets you **experiment with a tunable filter pipeline** —
previewing the effect of every change before committing it.

---

## What it does

- **Polls 37 worldwide RSS/Atom feeds** in parallel on startup and every 15 minutes,
  caching articles in memory (each tagged with its country so topics can be required to
  appear across multiple countries):
  - **Australia (4):** ABC News, Sydney Morning Herald, The Guardian AU, SBS News
  - **Americas (9):** CNN, New York Times, NPR, Washington Post, Fox News, USA Today, CBC News (CA), Global News (CA), CTV News (CA)
  - **Europe (14):** BBC News, The Guardian UK, Deutsche Welle, France 24, Euronews, The Independent, Sky News, The Telegraph (UK), Daily Mail (UK), Metro (UK), RTÉ News (Ireland), The Local (Spain), NL Times (Netherlands), The Moscow Times (Russia)
  - **Asia / Middle East (10):** Al Jazeera, Times of India, The Japan Times, South China Morning Post, Channel NewsAsia, The Straits Times, The Korea Herald, The Times of Israel, Gulf News (UAE), Dawn (Pakistan)
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
| **Collapse plurals** | Treats simple plurals as singular (`banks` → `bank`). *(off by default)* | — |
| **Multi-word topics** | Surfaces phrases (n-grams), e.g. `donald trump`, not just single words. | max words per topic |
| **Multi-word topics only** | Shows only phrases of 2+ words, hiding all single-word topics. *(on by default)* | — |
| **Roll up sub-phrases** | Counts a shorter multi-word phrase toward the longer phrase that contains it (`donald trump` → `president donald trump`); single words are never rolled up. *(on by default)* | min mentions of longer phrase |
| **Minimum word length** | Discards very short tokens. | min characters |
| **Noun detection (suffix)** | Keeps words with noun-like endings, drops obvious adverbs/verbs. | min length to test |
| **Capitalisation → proper nouns** | Uses **mid-sentence capitalisation** to detect proper nouns; can boost their score or keep *only* proper nouns. | require-proper toggle, score boost × |
| **Title vs content weighting** | Gives headline words more pull than body words. | title weight, content weight |
| **Minimum sources** | Only surfaces topics reported by several outlets. | distinct sources |
| **Recency** | Favours fresh news (exponential half-life) and drops stale articles. | max age (h, default 24), half-life (h) |

## How the score is calculated

Every topic's **score** is the sum, over each time it appears across the whole corpus, of a
per-occurrence weight:

```
weight = fieldWeight × recencyWeight × capitalisationBoost
score  = Σ weight   (over every occurrence, including repeats within one article)
```

Each factor (implemented in `TrendingService.processField` / `recencyWeight`):

1. **`fieldWeight` — where the term appeared** (the *Title vs content weighting* filter):
   - title → `titleWeight` (default **3.0**)
   - description/body → `contentWeight` (default **1.0**)
   - filter off → both `1.0`.

2. **`recencyWeight` — how fresh the article is** (the *Recency* filter):
   - disabled, or article has no date → `1.0`
   - older than `maxAgeHours` (default **24h**) → the article is **dropped entirely**
   - otherwise exponential half-life decay: `recencyWeight = 0.5 ^ (ageHours / halfLifeHours)`
     (default half-life **12h**, so ~1.0 when brand new, ~0.5 at 12h, ~0.25 at 24h; a
     half-life of 0 means "cutoff only, no decay").

3. **`capitalisationBoost`** (the *Capitalisation → proper nouns* filter, optional): when an
   occurrence looks like a proper noun (mid-sentence capitalised) the weight is multiplied by
   `boost` (default **2.0**). For multi-word topics this only applies when *every* word in the
   phrase is a proper-noun candidate.

**Multi-word topics** are scored with the same formula applied to the whole phrase. When
**Roll up sub-phrases** is on, an absorbed shorter phrase's score is **added** to its
container's score (along with its mentions, sources and articles).

The final score is rounded to two decimals and topics are ranked by descending score (top 20).

> The **hits** (number of articles mentioning the topic) and **src** (distinct sources)
> figures shown in the UI are plain counts for context — they are *not* part of the score.

Worked example: a headline word in a brand-new article that reads as a proper noun scores
`3.0 × ~1.0 × 2.0 ≈ 6.0` for that occurrence, whereas the same word buried in the body of a
24-hour-old article scores `1.0 × ~0.25 × 1.0 ≈ 0.25`.

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

## Prompt history

This project was built iteratively through the following prompts, in order:

1. Create a spring boot web app which polls news rss sources from around the world to show
   trending topics. There should be at least 5 sources from australia, 5 from america and
   then several from europe and some from asia. The app should show the top 10 trending
   topics and the topics should be clickable to show the articles the word came from. The
   app should allow the user to experience using different settings for tweaking the
   results. Each filter can be turned on and off and will have parameters that can be
   adjusted. The app should show a preview of the effect of adjusting a filter before it is
   applied permanently and then a snapshot of the results should be shown for previous
   filter settings. There should be some sort of filter to remove common words such as
   "a, the" etc and update this word list. Also create some filters that might prove useful
   using noun detection, ignoring punctuation and plurals, and how much extra weighting to
   give to title words vs content words, number of sources, recentness of articles and
   filters that use capitalisation to detect nouns. The app should contain a dockerfile and
   be easily deployable to render.
2. Alter the filter panel to be scrollable.
3. Switch the position of the snapshot and preview panels.
4. Incorporate multiple word trending topics and a filter that can be turned on and off to
   allow multiple word topics that are subsets of each other to be counted towards the
   longer word topics.
5. The scrollable filter panel is not working. When scrolling the panels do not remain in
   view.
6. When applying the roll up sub-phrases filter it does not seem to apply straight away?
7. It still takes a long time to update when updating filters and the user does not know
   what is going on.
8. Turn on roll up sub-phrases by default and can you speed up the time to update after a
   filter change.
9. Add a filter that enables only multiple word topics.
10. When changes are made to the filter multiple requests queue up to the backend.
11. The roll-up filter should only apply to phrases greater than 1 word.
12. Remove the Brisbane Times and The Age as news sources.
13. Make multi word topics only be selected by default.
14. Set recency max age to 24 hours by default.
15. Fix up formatting on mobile.
16. Turn off collapse plurals by default.
17. Don't include the phrase "latest news bulletin".
18. On mobile show the filter tab last.
19. How is the score calculated.
20. Record information about score calculation in the readme.
21. Create a filter that can be turned on and off to hide sporting news.
22. Create a filter that can be used to turn off Iran war news.
23. Provide a way of hiding and showing each section.
24. Add all prompts to the readme.
