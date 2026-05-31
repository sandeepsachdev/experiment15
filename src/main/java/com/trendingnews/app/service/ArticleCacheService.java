package com.trendingnews.app.service;

import com.trendingnews.app.model.Article;
import com.trendingnews.app.model.NewsSource;
import com.trendingnews.app.rss.NewsSourceRegistry;
import com.trendingnews.app.rss.RssFeedClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Holds the in-memory cache of harvested articles and refreshes it on a schedule.
 *
 * <p>All feeds are fetched in parallel. The trending pipeline always runs against this
 * cache, which keeps previews snappy and avoids hammering publishers on every request.
 */
@Service
public class ArticleCacheService {

    private static final Logger log = LoggerFactory.getLogger(ArticleCacheService.class);

    private final NewsSourceRegistry registry;
    private final RssFeedClient feedClient;

    private volatile List<Article> articles = List.of();
    private volatile Instant lastRefreshed;
    private volatile boolean refreshing;

    @Autowired
    public ArticleCacheService(NewsSourceRegistry registry, RssFeedClient feedClient) {
        this.registry = registry;
        this.feedClient = feedClient;
    }

    /** Kick off an initial load shortly after startup, off the main thread. */
    @PostConstruct
    public void init() {
        new Thread(this::refresh, "initial-feed-load").start();
    }

    /** Refresh every 15 minutes. */
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    public void scheduledRefresh() {
        refresh();
    }

    public synchronized void refresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        long start = System.currentTimeMillis();
        List<NewsSource> sources = registry.getSources();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(12, Math.max(1, sources.size())));
        try {
            List<Future<List<Article>>> futures = new ArrayList<>();
            for (NewsSource source : sources) {
                futures.add(pool.submit(() -> feedClient.fetch(source)));
            }

            // De-duplicate by link, keeping the first occurrence.
            Map<String, Article> deduped = new LinkedHashMap<>();
            for (Future<List<Article>> future : futures) {
                try {
                    for (Article article : future.get(30, TimeUnit.SECONDS)) {
                        String key = article.getLink() == null || article.getLink().isBlank()
                                ? article.getSourceName() + "::" + article.getTitle()
                                : article.getLink();
                        deduped.putIfAbsent(key, article);
                    }
                } catch (Exception e) {
                    log.warn("A feed task failed: {}", e.toString());
                }
            }

            this.articles = List.copyOf(deduped.values());
            this.lastRefreshed = Instant.now();
            log.info("Refreshed cache: {} unique articles from {} sources in {} ms",
                    articles.size(), sources.size(), System.currentTimeMillis() - start);
        } finally {
            pool.shutdownNow();
            refreshing = false;
        }
    }

    public List<Article> getArticles() {
        return articles;
    }

    public Instant getLastRefreshed() {
        return lastRefreshed;
    }

    public boolean isRefreshing() {
        return refreshing;
    }

    public int getSourceCount() {
        return registry.getSources().size();
    }
}
