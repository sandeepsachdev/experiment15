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

        Set<String> absorbed = applyPhraseRollup(accumulators, settings);
        List<TrendingTopic> topics = rank(accumulators, absorbed, settings);

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

        // First pass: normalise and filter each token, keeping the ones that survive (in
        // order) so contiguous n-grams can be built from them.
        List<AcceptedToken> accepted = new ArrayList<>();
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

            accepted.add(new AcceptedToken(term, proper));
        }

        // Second pass: emit single words and, when the phrase filter is on, contiguous
        // multi-word n-grams up to the configured length.
        int maxWords = settings.getPhrase().isEnabled()
                ? Math.max(1, settings.getPhrase().getMaxWords()) : 1;

        for (int i = 0; i < accepted.size(); i++) {
            StringBuilder phrase = new StringBuilder();
            boolean allProper = true;
            int limit = Math.min(maxWords, accepted.size() - i);
            for (int n = 0; n < limit; n++) {
                AcceptedToken at = accepted.get(i + n);
                if (n > 0) {
                    phrase.append(' ');
                }
                phrase.append(at.term);
                allProper = allProper && at.proper;

                String key = phrase.toString();
                double weight = fieldWeight * recencyWeight;
                if (allProper && cap.isEnabled()) {
                    weight *= cap.getBoost();
                }

                Accumulator acc = accumulators.computeIfAbsent(key, k -> new Accumulator());
                // Count each phrase once per article for "mentions"/source breadth, but
                // always add weight so repeated mentions still raise the score.
                boolean firstInArticle = seenInArticle.add(key);
                acc.score += weight;
                if (firstInArticle) {
                    acc.mentions++;
                    acc.sources.add(article.getSourceName());
                    acc.articles.put(article.getLink() == null ? article.getTitle() : article.getLink(), article);
                }
                if (allProper) {
                    acc.properMentions++;
                }
            }
        }
    }

    /**
     * Rolls shorter phrases up into the longer phrases that contain them.
     *
     * <p>When the filter is enabled, each phrase is absorbed into the single best container
     * — the longest (then highest-scoring) phrase that contains it as a contiguous run of
     * words and that was itself mentioned often enough. The shorter phrase's score, mentions,
     * sources and articles are merged into the container and the shorter phrase is dropped
     * from the results. Processing shortest-first lets contributions chain up to the longest
     * phrase without ever double-counting (each phrase moves into exactly one container).
     *
     * @return the set of accumulator keys that were absorbed and must be skipped when ranking
     */
    private Set<String> applyPhraseRollup(Map<String, Accumulator> accumulators, FilterSettings settings) {
        FilterSettings.PhraseRollupFilter rollup = settings.getPhraseRollup();
        if (!rollup.isEnabled()) {
            return Set.of();
        }

        // Pre-split every phrase into its words once.
        Map<String, String[]> words = new HashMap<>();
        for (String key : accumulators.keySet()) {
            words.put(key, key.split(" "));
        }

        // Shortest phrases first so a word can flow through an intermediate phrase up to the
        // longest container.
        List<String> byLength = new ArrayList<>(accumulators.keySet());
        byLength.sort(Comparator.comparingInt(k -> words.get(k).length));

        Set<String> absorbed = new HashSet<>();
        for (String shortKey : byLength) {
            if (absorbed.contains(shortKey)) {
                continue;
            }
            String[] shortWords = words.get(shortKey);
            String bestContainer = null;
            int bestLen = shortWords.length;
            double bestScore = -1;

            for (Map.Entry<String, Accumulator> e : accumulators.entrySet()) {
                String longKey = e.getKey();
                if (longKey.equals(shortKey) || absorbed.contains(longKey)) {
                    continue;
                }
                String[] longWords = words.get(longKey);
                if (longWords.length <= shortWords.length) {
                    continue;
                }
                if (e.getValue().mentions < rollup.getMinContainerMentions()) {
                    continue;
                }
                if (!TextProcessor.containsContiguous(longWords, shortWords)) {
                    continue;
                }
                // Prefer the longest container; break ties by score.
                if (longWords.length > bestLen
                        || (longWords.length == bestLen && e.getValue().score > bestScore)) {
                    bestContainer = longKey;
                    bestLen = longWords.length;
                    bestScore = e.getValue().score;
                }
            }

            if (bestContainer != null) {
                accumulators.get(bestContainer).absorb(accumulators.get(shortKey));
                absorbed.add(shortKey);
            }
        }
        return absorbed;
    }

    private List<TrendingTopic> rank(Map<String, Accumulator> accumulators, Set<String> absorbed,
                                     FilterSettings settings) {
        FilterSettings.MinSourcesFilter minSources = settings.getMinSources();
        FilterSettings.CapitalisationFilter cap = settings.getCapitalisation();

        List<Map.Entry<String, Accumulator>> entries = new ArrayList<>();
        for (Map.Entry<String, Accumulator> e : accumulators.entrySet()) {
            if (absorbed.contains(e.getKey())) {
                continue;
            }
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

    /** A token that survived filtering, ready to be combined into n-grams. */
    private record AcceptedToken(String term, boolean proper) {
    }

    /** Mutable per-term tally used while scanning the corpus. */
    private static class Accumulator {
        double score;
        int mentions;
        int properMentions;
        final Set<String> sources = new HashSet<>();
        final Map<String, Article> articles = new LinkedHashMap<>();

        /** Merge another phrase's tally into this one (used by phrase rollup). */
        void absorb(Accumulator other) {
            this.score += other.score;
            this.mentions += other.mentions;
            this.properMentions += other.properMentions;
            this.sources.addAll(other.sources);
            this.articles.putAll(other.articles);
        }
    }
}
