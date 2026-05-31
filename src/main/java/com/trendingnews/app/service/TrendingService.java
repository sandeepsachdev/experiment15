package com.trendingnews.app.service;

import com.trendingnews.app.filter.TextProcessor;
import com.trendingnews.app.filter.TextProcessor.RawToken;
import com.trendingnews.app.model.Article;
import com.trendingnews.app.model.FilterSettings;
import com.trendingnews.app.model.TrendingResult;
import com.trendingnews.app.model.TrendingTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The heart of the app: turns the cached articles into ranked trending topics by running
 * the configurable filter pipeline described by a {@link FilterSettings}.
 *
 * <p>The method is pure with respect to its inputs (cache snapshot + settings), so the UI
 * can call it repeatedly to preview different settings without side effects.
 */
@Service
public class TrendingService {

    private static final int TOP_N = 10;
    private static final int MAX_ARTICLES_PER_TOPIC = 8;

    private final ArticleCacheService cache;
    private final StopwordService stopwordService;

    @Autowired
    public TrendingService(ArticleCacheService cache, StopwordService stopwordService) {
        this.cache = cache;
        this.stopwordService = stopwordService;
    }

    public TrendingResult compute(FilterSettings settings) {
        if (settings == null) {
            settings = FilterSettings.withDefaults();
        }
        List<Article> all = cache.getArticles();
        Set<String> stopwords = resolveStopwords(settings);
        Instant now = Instant.now();

        Map<String, Accumulator> accumulators = new HashMap<>();
        int considered = 0;
        Map<String, Integer> byRegion = new TreeMap<>();

        for (Article article : all) {
            double recencyWeight = recencyWeight(article, settings, now);
            if (recencyWeight < 0) {
                continue; // dropped by the recency cutoff
            }
            considered++;
            if (article.getRegion() != null) {
                byRegion.merge(article.getRegion(), 1, Integer::sum);
            }

            double titleWeight = settings.getTitleWeight().isEnabled()
                    ? settings.getTitleWeight().getTitleWeight() : 1.0;
            double contentWeight = settings.getTitleWeight().isEnabled()
                    ? settings.getTitleWeight().getContentWeight() : 1.0;

            // De-duplicate terms within a single article so one story can't spam a term's count.
            Set<String> seenInArticle = new HashSet<>();
            processField(article.getTitle(), titleWeight, recencyWeight,
                    article, settings, stopwords, accumulators, seenInArticle);
            processField(article.getDescription(), contentWeight, recencyWeight,
                    article, settings, stopwords, accumulators, seenInArticle);
        }

        List<TrendingTopic> topics = rank(accumulators, settings);

        return new TrendingResult(
                topics,
                all.size(),
                considered,
                cache.getSourceCount(),
                byRegion,
                cache.getLastRefreshed() == null ? null : cache.getLastRefreshed().toString());
    }

    private void processField(String text, double fieldWeight, double recencyWeight,
                              Article article, FilterSettings settings, Set<String> stopwords,
                              Map<String, Accumulator> accumulators, Set<String> seenInArticle) {
        FilterSettings.CapitalisationFilter cap = settings.getCapitalisation();
        FilterSettings.NounFilter noun = settings.getNoun();
        FilterSettings.MinLengthFilter minLen = settings.getMinLength();

        for (RawToken token : TextProcessor.tokenize(text)) {
            boolean proper = cap.isEnabled() && TextProcessor.isProperNounCandidate(token);

            String term = token.raw();
            if (settings.getPunctuation().isEnabled()) {
                term = TextProcessor.stripPunctuation(term);
            }
            term = term.toLowerCase();
            if (term.isBlank()) {
                continue;
            }
            if (settings.getPlural().isEnabled()) {
                term = TextProcessor.singularize(term);
            }

            // Length floor.
            if (minLen.isEnabled() && term.length() < minLen.getMinLength()) {
                continue;
            }
            // Stopwords.
            if (settings.getStopwords().isEnabled() && stopwords.contains(term)) {
                continue;
            }
            // Noun heuristic.
            if (noun.isEnabled()) {
                if (term.length() < noun.getMinLength()) {
                    continue;
                }
                if (!proper && !TextProcessor.looksLikeNoun(term)) {
                    continue;
                }
            }

            double weight = fieldWeight * recencyWeight;
            if (proper && cap.isEnabled()) {
                weight *= cap.getBoost();
            }

            Accumulator acc = accumulators.computeIfAbsent(term, k -> new Accumulator());
            // Count each article once per term for "mentions"/source breadth, but always add weight.
            boolean firstInArticle = seenInArticle.add(term);
            acc.score += weight;
            if (firstInArticle) {
                acc.mentions++;
                acc.sources.add(article.getSourceName());
                acc.articles.put(article.getLink() == null ? article.getTitle() : article.getLink(), article);
            }
            if (proper) {
                acc.properMentions++;
            }
        }
    }

    private List<TrendingTopic> rank(Map<String, Accumulator> accumulators, FilterSettings settings) {
        FilterSettings.MinSourcesFilter minSources = settings.getMinSources();
        FilterSettings.CapitalisationFilter cap = settings.getCapitalisation();

        List<Map.Entry<String, Accumulator>> entries = new ArrayList<>();
        for (Map.Entry<String, Accumulator> e : accumulators.entrySet()) {
            Accumulator acc = e.getValue();
            if (minSources.isEnabled() && acc.sources.size() < minSources.getMinSources()) {
                continue;
            }
            if (cap.isEnabled() && cap.isRequireCapitalised() && acc.properMentions == 0) {
                continue;
            }
            entries.add(e);
        }

        entries.sort(Comparator.<Map.Entry<String, Accumulator>>comparingDouble(e -> e.getValue().score).reversed());

        List<TrendingTopic> topics = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, entries.size()); i++) {
            Map.Entry<String, Accumulator> e = entries.get(i);
            Accumulator acc = e.getValue();
            topics.add(new TrendingTopic(
                    e.getKey(),
                    round(acc.score),
                    acc.mentions,
                    acc.sources.size(),
                    toArticleRefs(acc)));
        }
        return topics;
    }

    private List<TrendingTopic.ArticleRef> toArticleRefs(Accumulator acc) {
        List<Article> articles = new ArrayList<>(acc.articles.values());
        articles.sort(Comparator.comparing(
                Article::getPublishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<TrendingTopic.ArticleRef> refs = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_ARTICLES_PER_TOPIC, articles.size()); i++) {
            Article a = articles.get(i);
            refs.add(new TrendingTopic.ArticleRef(
                    a.getTitle(),
                    a.getLink(),
                    a.getSourceName(),
                    a.getRegion(),
                    a.getPublishedAt() == null ? null : a.getPublishedAt().toString()));
        }
        return refs;
    }

    /**
     * Returns the recency multiplier for an article, or a negative value if the article
     * falls outside the configured age cutoff and should be dropped.
     */
    private double recencyWeight(Article article, FilterSettings settings, Instant now) {
        FilterSettings.RecencyFilter recency = settings.getRecency();
        if (!recency.isEnabled()) {
            return 1.0;
        }
        if (article.getPublishedAt() == null) {
            return 1.0; // unknown date: keep with neutral weight
        }
        double ageHours = Duration.between(article.getPublishedAt(), now).toMinutes() / 60.0;
        if (ageHours < 0) {
            ageHours = 0;
        }
        if (recency.getMaxAgeHours() > 0 && ageHours > recency.getMaxAgeHours()) {
            return -1;
        }
        if (recency.getHalfLifeHours() <= 0) {
            return 1.0; // cutoff only, no decay
        }
        return Math.pow(0.5, ageHours / recency.getHalfLifeHours());
    }

    private Set<String> resolveStopwords(FilterSettings settings) {
        if (!settings.getStopwords().isEnabled()) {
            return Set.of();
        }
        List<String> override = settings.getStopwords().getWords();
        if (override != null && !override.isEmpty()) {
            Set<String> set = new HashSet<>();
            for (String w : override) {
                if (w != null && !w.isBlank()) {
                    set.add(w.trim().toLowerCase());
                }
            }
            return set;
        }
        return new HashSet<>(stopwordService.getWords());
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Mutable per-term tally used while scanning the corpus. */
    private static class Accumulator {
        double score;
        int mentions;
        int properMentions;
        final Set<String> sources = new HashSet<>();
        final Map<String, Article> articles = new LinkedHashMap<>();
    }
}
