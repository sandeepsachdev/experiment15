package com.trendingnews.app.controller;

import com.trendingnews.app.model.FilterSettings;
import com.trendingnews.app.model.NewsSource;
import com.trendingnews.app.model.TrendingResult;
import com.trendingnews.app.rss.NewsSourceRegistry;
import com.trendingnews.app.service.ArticleCacheService;
import com.trendingnews.app.service.StopwordService;
import com.trendingnews.app.service.TrendingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the single-page UI.
 *
 * <ul>
 *   <li>{@code POST /api/trending} – run the pipeline for a given settings object</li>
 *   <li>{@code GET  /api/sources}  – list the configured feeds grouped by region</li>
 *   <li>{@code GET/POST /api/stopwords} – read / update the stopword list</li>
 *   <li>{@code GET  /api/status}   – cache freshness</li>
 *   <li>{@code POST /api/refresh}  – force a re-poll</li>
 *   <li>{@code GET  /api/defaults} – the default settings the UI initialises from</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final TrendingService trendingService;
    private final ArticleCacheService cacheService;
    private final StopwordService stopwordService;
    private final NewsSourceRegistry sourceRegistry;

    @Autowired
    public ApiController(TrendingService trendingService,
                         ArticleCacheService cacheService,
                         StopwordService stopwordService,
                         NewsSourceRegistry sourceRegistry) {
        this.trendingService = trendingService;
        this.cacheService = cacheService;
        this.stopwordService = stopwordService;
        this.sourceRegistry = sourceRegistry;
    }

    @PostMapping("/trending")
    public TrendingResult trending(@RequestBody(required = false) FilterSettings settings) {
        return trendingService.compute(settings == null ? FilterSettings.withDefaults() : settings);
    }

    @GetMapping("/defaults")
    public FilterSettings defaults() {
        return FilterSettings.withDefaults();
    }

    @GetMapping("/blocked-phrases")
    public Map<String, Object> blockedPhrases() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phrases", trendingService.getDefaultBlockedPhrases());
        return out;
    }

    @GetMapping("/sources")
    public Map<String, Object> sources() {
        Map<String, List<String>> byRegion = new LinkedHashMap<>();
        for (NewsSource s : sourceRegistry.getSources()) {
            byRegion.computeIfAbsent(s.getRegion(), k -> new ArrayList<>()).add(s.getName());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", sourceRegistry.getSources().size());
        out.put("byRegion", byRegion);
        return out;
    }

    @GetMapping("/stopwords")
    public Map<String, Object> getStopwords() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("words", stopwordService.getWords());
        return out;
    }

    @PostMapping("/stopwords")
    public Map<String, Object> updateStopwords(@RequestBody StopwordRequest request) {
        if (request != null && request.isReset()) {
            stopwordService.resetToDefault();
        } else if (request != null) {
            stopwordService.replaceAll(request.getWords());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("words", stopwordService.getWords());
        return out;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("articles", cacheService.getArticles().size());
        out.put("sources", cacheService.getSourceCount());
        out.put("refreshing", cacheService.isRefreshing());
        out.put("lastRefreshed", cacheService.getLastRefreshed() == null
                ? null : cacheService.getLastRefreshed().toString());
        return out;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        new Thread(cacheService::refresh, "manual-refresh").start();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "refresh started");
        return out;
    }

    /** Request body for {@code POST /api/stopwords}. */
    public static class StopwordRequest {
        private List<String> words;
        private boolean reset;

        public List<String> getWords() {
            return words;
        }

        public void setWords(List<String> words) {
            this.words = words;
        }

        public boolean isReset() {
            return reset;
        }

        public void setReset(boolean reset) {
            this.reset = reset;
        }
    }
}
