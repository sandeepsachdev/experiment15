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
import java.util.Arrays;
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

    private static final int TOP_N = 20;
    private static final int MAX_ARTICLES_PER_TOPIC = 8;

    /**
     * Multi-word feed boilerplate that should never surface as a trending topic, regardless
     * of how often it appears. Compared case-insensitively against generated phrases.
     */
    private static final Set<String> BLOCKED_PHRASES = Set.of(
            "latest news bulletin");

    /**
     * Lower-case keywords that mark an article as sport. Matched as whole words against the
     * title + description so the "hide sporting news" filter can drop those articles.
     */
    private static final Set<String> SPORT_KEYWORDS = Set.of(
            "sport", "sports", "football", "soccer", "rugby", "cricket", "tennis", "golf",
            "basketball", "baseball", "hockey", "nba", "nfl", "mlb", "nhl", "afl", "nrl",
            "olympics", "olympic", "f1", "formula 1", "formula one", "grand prix", "motogp",
            "premier league", "champions league", "la liga", "bundesliga", "serie a",
            "world cup", "wimbledon", "match", "matches", "tournament", "tournaments",
            "playoff", "playoffs", "fixture", "fixtures", "striker", "midfielder",
            "goalkeeper", "touchdown", "wicket", "wickets", "batsman", "bowler",
            "athletics", "marathon", "boxing", "ufc", "cycling", "swimming");

    /**
     * Lower-case keywords/phrases that mark an article as Iran-war / Iran-conflict coverage.
     * A match requires an Iran reference together with a conflict term (see
     * {@link #isIranWarArticle}).
     */
    private static final Set<String> IRAN_TERMS = Set.of("iran", "iranian", "tehran");
    private static final Set<String> WAR_TERMS = Set.of(
            "war", "strike", "strikes", "airstrike", "airstrikes", "missile", "missiles",
            "attack", "attacks", "conflict", "military", "nuclear", "ceasefire", "troops",
            "retaliation", "retaliatory", "bombing", "bombed", "escalation", "warfare",
            "offensive", "drone", "drones", "rocket", "rockets", "shelling", "invasion");

    /**
     * Lower-case place/actor terms that mark an article as Israel / Lebanon / Gaza coverage.
     * Combined with {@link #WAR_TERMS} (see {@link #isMideastConflictArticle}) so general
     * stories about these places (economy, culture) are not dropped — only conflict ones.
     */
    private static final Set<String> MIDEAST_TERMS = Set.of(
            "israel", "israeli", "gaza", "gazan", "lebanon", "lebanese", "hamas",
            "hezbollah", "idf", "west bank", "palestinian", "palestine", "beirut",
            "tel aviv", "netanyahu");

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
            if (settings.getHideSports().isEnabled() && isSportArticle(article)) {
                continue; // excluded by the "hide sporting news" filter
            }
            if (settings.getHideIranWar().isEnabled() && isIranWarArticle(article)) {
                continue; // excluded by the "hide Iran war news" filter
            }
            if (settings.getHideMideastConflict().isEnabled() && isMideastConflictArticle(article)) {
                continue; // excluded by the "hide Israel/Lebanon/Gaza conflict" filter
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

        Set<String> absorbed = new HashSet<>(applyPhraseRollup(accumulators, settings));
        absorbed.addAll(applyMergeOverlap(accumulators, absorbed, settings));
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

        // First pass: normalise and filter each token. Surviving tokens are grouped into
        // contiguous "segments"; whenever a token is dropped (stopword, number, non-noun, …)
        // the current segment ends, so phrases never span across a removed word (no fabricated
        // adjacencies like "president united states" from "president of the united states").
        List<List<AcceptedToken>> segments = new ArrayList<>();
        List<AcceptedToken> current = new ArrayList<>();
        for (RawToken token : TextProcessor.tokenize(text)) {
            boolean proper = cap.isEnabled() && TextProcessor.isProperNounCandidate(token);

            String term = token.raw();
            if (settings.getPunctuation().isEnabled()) {
                term = TextProcessor.stripPunctuation(term);
            }
            term = term.toLowerCase();
            boolean dropped = term.isBlank();
            if (!dropped && settings.getPlural().isEnabled()) {
                term = TextProcessor.singularize(term);
            }

            // Length floor.
            if (!dropped && minLen.isEnabled() && term.length() < minLen.getMinLength()) {
                dropped = true;
            }
            // Stopwords.
            if (!dropped && settings.getStopwords().isEnabled() && stopwords.contains(term)) {
                dropped = true;
            }
            // Pure-number / date tokens ("250", "31st", "2026").
            if (!dropped && settings.getNumeric().isEnabled() && isNumericToken(term)) {
                dropped = true;
            }
            // Noun heuristic.
            if (!dropped && noun.isEnabled()) {
                if (term.length() < noun.getMinLength() || (!proper && !TextProcessor.looksLikeNoun(term))) {
                    dropped = true;
                }
            }

            if (dropped) {
                // A removed token breaks phrase continuity: close off the current segment.
                if (!current.isEmpty()) {
                    segments.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(new AcceptedToken(term, proper));
            }
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }

        // Second pass: emit contiguous n-grams within each segment. The phrase filter sets the
        // longest n-gram; the "multi-word only" filter suppresses single-word (1-gram) topics.
        // When multi-word only is on we always generate phrases (else nothing would be produced).
        boolean multiWordOnly = settings.getMultiWordOnly().isEnabled();
        int maxWords = (settings.getPhrase().isEnabled() || multiWordOnly)
                ? Math.max(multiWordOnly ? 2 : 1, settings.getPhrase().getMaxWords()) : 1;
        int minWords = multiWordOnly ? 2 : 1;
        boolean countOnce = settings.getCountOncePerArticle().isEnabled();

        for (List<AcceptedToken> accepted : segments) {
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

                    // Skip emitting topics shorter than the minimum (e.g. single words when
                    // "multi-word only" is enabled).
                    if (n + 1 < minWords) {
                        continue;
                    }

                    String key = phrase.toString();
                    // Never surface known boilerplate phrases as topics.
                    if (BLOCKED_PHRASES.contains(key)) {
                        continue;
                    }
                    double weight = fieldWeight * recencyWeight;
                    if (allProper && cap.isEnabled()) {
                        weight *= cap.getBoost();
                    }

                    Accumulator acc = accumulators.computeIfAbsent(key, k -> new Accumulator());
                    // "mentions"/source breadth always count an article once. By default the
                    // score also only counts a topic once per article; with the toggle off,
                    // repeated mentions within a story each add to the score.
                    boolean firstInArticle = seenInArticle.add(key);
                    if (firstInArticle || !countOnce) {
                        acc.score += weight;
                    }
                    if (firstInArticle) {
                        acc.mentions++;
                        acc.sources.add(article.getSourceName());
                        acc.articles.put(article.getLink() == null ? article.getTitle() : article.getLink(), article);
                        if (allProper) {
                            acc.properMentions++;
                        }
                    } else if (allProper && !countOnce) {
                        acc.properMentions++;
                    }
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

        int minMentions = rollup.getMinContainerMentions();

        // Pre-split every phrase into its words once, and build an inverted index from each
        // word to the multi-word phrases that contain it. A phrase can only be a container for
        // a shorter phrase if it shares the shorter phrase's first word, so the index lets us
        // consider just those candidates instead of scanning the whole map (O(n^2) -> ~linear).
        Map<String, String[]> words = new HashMap<>();
        Map<String, List<String>> wordToPhrases = new HashMap<>();
        for (String key : accumulators.keySet()) {
            String[] w = key.split(" ");
            words.put(key, w);
            if (w.length < 2) {
                continue; // single words are never containers
            }
            // Only index phrases that are eligible containers (enough mentions).
            if (accumulators.get(key).mentions < minMentions) {
                continue;
            }
            Set<String> distinct = new HashSet<>(Arrays.asList(w));
            for (String token : distinct) {
                wordToPhrases.computeIfAbsent(token, k -> new ArrayList<>()).add(key);
            }
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
            // Only roll up multi-word phrases; single words are kept as their own topics.
            if (shortWords.length < 2) {
                continue;
            }
            List<String> candidates = wordToPhrases.get(shortWords[0]);
            if (candidates == null) {
                continue; // no longer phrase shares this phrase's first word
            }
            String bestContainer = null;
            int bestLen = shortWords.length;
            double bestScore = -1;

            for (String longKey : candidates) {
                if (longKey.equals(shortKey) || absorbed.contains(longKey)) {
                    continue;
                }
                String[] longWords = words.get(longKey);
                if (longWords.length <= shortWords.length) {
                    continue;
                }
                if (!TextProcessor.containsContiguous(longWords, shortWords)) {
                    continue;
                }
                // Prefer the longest container; break ties by score.
                double longScore = accumulators.get(longKey).score;
                if (longWords.length > bestLen
                        || (longWords.length == bestLen && longScore > bestScore)) {
                    bestContainer = longKey;
                    bestLen = longWords.length;
                    bestScore = longScore;
                }
            }

            if (bestContainer != null) {
                // Union (not sum): the sub-phrase comes from the same stories as its
                // container, so summing would multiply the shared coverage. absorbOverlap
                // keeps the score and recomputes mentions from the distinct article set.
                accumulators.get(bestContainer).absorbOverlap(accumulators.get(shortKey));
                absorbed.add(shortKey);
            }
        }
        return absorbed;
    }

    /**
     * Merges near-duplicate overlapping phrases that describe the same story. Two phrases are
     * considered the same topic when they share at least one word AND their source-article
     * sets overlap by at least the configured fraction (Jaccard). This collapses fragments
     * like "alleged drug boat", "drug boat kills" and "strike alleged drug" — overlapping
     * windows of one headline that phrase rollup misses because none is a contiguous subset
     * of another.
     *
     * <p>Greedy and order-stable: the highest-scoring phrase becomes each cluster's keeper and
     * absorbs the others (union of articles/sources, so the score reflects distinct coverage
     * rather than the sum of overlapping fragments).
     *
     * @return the set of accumulator keys that were absorbed and must be skipped when ranking
     */
    private Set<String> applyMergeOverlap(Map<String, Accumulator> accumulators,
                                          Set<String> alreadyAbsorbed, FilterSettings settings) {
        FilterSettings.MergeOverlapFilter merge = settings.getMergeOverlap();
        if (!merge.isEnabled()) {
            return Set.of();
        }
        double minOverlap = merge.getMinArticleOverlap();

        // Candidates: multi-word phrases still in play, highest score first (keepers win ties).
        List<String> keys = new ArrayList<>();
        for (String k : accumulators.keySet()) {
            if (!alreadyAbsorbed.contains(k) && k.indexOf(' ') >= 0) {
                keys.add(k);
            }
        }
        keys.sort(Comparator.comparingDouble((String k) -> accumulators.get(k).score).reversed());

        Map<String, Set<String>> wordSets = new HashMap<>();
        for (String k : keys) {
            wordSets.put(k, new HashSet<>(Arrays.asList(k.split(" "))));
        }

        Set<String> absorbed = new HashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            String keeper = keys.get(i);
            if (absorbed.contains(keeper)) {
                continue;
            }
            Accumulator keeperAcc = accumulators.get(keeper);
            for (int j = i + 1; j < keys.size(); j++) {
                String other = keys.get(j);
                if (absorbed.contains(other)) {
                    continue;
                }
                // Must share at least one word.
                if (!sharesWord(wordSets.get(keeper), wordSets.get(other))) {
                    continue;
                }
                if (articleOverlap(keeperAcc.articles, accumulators.get(other).articles) >= minOverlap) {
                    keeperAcc.absorbOverlap(accumulators.get(other));
                    absorbed.add(other);
                }
            }
        }
        return absorbed;
    }

    private boolean sharesWord(Set<String> a, Set<String> b) {
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = small == a ? b : a;
        for (String w : small) {
            if (large.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Jaccard overlap of two articles maps keyed by link: |A∩B| / |A∪B|. */
    private double articleOverlap(Map<String, Article> a, Map<String, Article> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        Map<String, Article> small = a.size() <= b.size() ? a : b;
        Map<String, Article> large = small == a ? b : a;
        for (String key : small.keySet()) {
            if (large.containsKey(key)) {
                inter++;
            }
        }
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
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

    /** True when the article's title or description matches any sport keyword (whole word). */
    private boolean isSportArticle(Article article) {
        String text = (article.getTitle() + " " + article.getDescription()).toLowerCase();
        return containsAnyKeyword(text, SPORT_KEYWORDS);
    }

    /**
     * True when the article looks like Iran-war coverage: it must reference Iran AND contain
     * at least one conflict/war term, so general Iran stories (e.g. culture, economy) survive.
     */
    private boolean isIranWarArticle(Article article) {
        String text = (article.getTitle() + " " + article.getDescription()).toLowerCase();
        return containsAnyKeyword(text, IRAN_TERMS) && containsAnyKeyword(text, WAR_TERMS);
    }

    /**
     * True when the article looks like Israel/Lebanon/Gaza conflict coverage: it must
     * reference one of those places/actors AND contain a conflict/war term, so general
     * stories about them (economy, culture) survive while conflict coverage is dropped.
     */
    private boolean isMideastConflictArticle(Article article) {
        String text = (article.getTitle() + " " + article.getDescription()).toLowerCase();
        return containsAnyKeyword(text, MIDEAST_TERMS) && containsAnyKeyword(text, WAR_TERMS);
    }

    /**
     * True when a token is purely numeric or a number with an ordinal/date suffix, e.g.
     * "250", "31st", "2026", "1990s", "10th". Such tokens are noise as standalone topics.
     */
    private boolean isNumericToken(String term) {
        int digits = 0;
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else if (Character.isLetter(c)) {
                // Allow only trailing ordinal/decade suffixes after the digits.
                String rest = term.substring(i);
                if (digits > 0 && (rest.equals("st") || rest.equals("nd") || rest.equals("rd")
                        || rest.equals("th") || rest.equals("s"))) {
                    return true;
                }
                return false;
            }
            // punctuation inside (e.g. commas) is ignored for this test
        }
        return digits > 0;
    }

    /**
     * Whole-word / phrase containment test: each keyword matches only on word boundaries so
     * "sport" doesn't match "transport" and "war" doesn't match "warehouse".
     */
    private boolean containsAnyKeyword(String lowerText, Set<String> keywords) {
        for (String kw : keywords) {
            int from = 0;
            int idx;
            while ((idx = lowerText.indexOf(kw, from)) >= 0) {
                boolean leftOk = idx == 0 || !Character.isLetterOrDigit(lowerText.charAt(idx - 1));
                int end = idx + kw.length();
                boolean rightOk = end >= lowerText.length()
                        || !Character.isLetterOrDigit(lowerText.charAt(end));
                if (leftOk && rightOk) {
                    return true;
                }
                from = idx + 1;
            }
        }
        return false;
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

        /**
         * Merge another phrase's tally into this one by UNION (used by both phrase rollup and
         * merge-overlap). Because the absorbed phrase comes from the same stories as the
         * keeper, scores are NOT summed (that would double-count the shared coverage); the
         * keeper's score is retained and articles/sources are unioned so mentions/source
         * breadth reflect the combined, de-duplicated set.
         */
        void absorbOverlap(Accumulator other) {
            this.score = Math.max(this.score, other.score);
            this.properMentions = Math.max(this.properMentions, other.properMentions);
            this.sources.addAll(other.sources);
            this.articles.putAll(other.articles);
            this.mentions = this.articles.size();
        }
    }
}
