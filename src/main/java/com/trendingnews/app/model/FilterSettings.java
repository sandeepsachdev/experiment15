package com.trendingnews.app.model;

import java.util.List;

/**
 * The complete, user-tunable configuration for the trending pipeline.
 *
 * <p>Every filter can be switched on/off independently and carries its own parameters.
 * The object is deserialized straight from the JSON the UI sends, so missing fields fall
 * back to the defaults defined here. Call {@link #withDefaults()} for a sensible baseline.
 */
public class FilterSettings {

    /** Number of trending topics to return (clamped to 5..50 by the service). */
    private int topN = 20;

    /** Remove common, low-signal words ("a", "the", "is", ...). The list is editable. */
    private StopwordFilter stopwords = new StopwordFilter();

    /** Strip punctuation from tokens so "covid," and "covid" are counted together. */
    private PunctuationFilter punctuation = new PunctuationFilter();

    /** Collapse simple plurals to their singular form ("banks" -> "bank"). */
    private PluralFilter plural = new PluralFilter();

    /** Keep only words that look like nouns based on a suffix/length heuristic. */
    private NounFilter noun = new NounFilter();

    /** Use mid-sentence capitalisation as a proper-noun signal (boost or require). */
    private CapitalisationFilter capitalisation = new CapitalisationFilter();

    /** Weight title words differently from body/content words. */
    private TitleWeightFilter titleWeight = new TitleWeightFilter();

    /** Require a topic to appear across a minimum number of distinct sources. */
    private MinSourcesFilter minSources = new MinSourcesFilter();

    /** Require a topic to appear in feeds from a minimum number of distinct countries. */
    private MinCountriesFilter minCountries = new MinCountriesFilter();

    /** Favour recent articles and optionally drop ones older than a cutoff. */
    private RecencyFilter recency = new RecencyFilter();

    /** Drop very short tokens regardless of other filters. */
    private MinLengthFilter minLength = new MinLengthFilter();

    /** Drop pure-number / date tokens ("250", "31st", "2026"). */
    private NumericFilter numeric = new NumericFilter();

    /** Surface multi-word (n-gram) topics in addition to single words. */
    private PhraseFilter phrase = new PhraseFilter();

    /** Show only multi-word topics, suppressing single-word ones. */
    private MultiWordOnlyFilter multiWordOnly = new MultiWordOnlyFilter();

    /** Count each topic at most once per article (ignore repeat mentions within a story). */
    private CountOncePerArticleFilter countOncePerArticle = new CountOncePerArticleFilter();

    /** Roll shorter phrases up into the longer phrases that contain them. */
    private PhraseRollupFilter phraseRollup = new PhraseRollupFilter();

    /** Merge near-duplicate overlapping phrases from the same story into one topic. */
    private MergeOverlapFilter mergeOverlap = new MergeOverlapFilter();

    /** Restrict the corpus to a chosen set of regions (empty/disabled = all regions). */
    private RegionFilter region = new RegionFilter();

    /** Exclude sport articles from the corpus entirely. */
    private HideSportsFilter hideSports = new HideSportsFilter();

    /** Exclude Iran-war / Iran-conflict articles from the corpus entirely. */
    private HideIranWarFilter hideIranWar = new HideIranWarFilter();

    /** Exclude Israel / Lebanon / Gaza conflict articles from the corpus entirely. */
    private HideMideastConflictFilter hideMideastConflict = new HideMideastConflictFilter();

    public static FilterSettings withDefaults() {
        return new FilterSettings();
    }

    // ----- getters / setters -----

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public StopwordFilter getStopwords() {
        return stopwords;
    }

    public void setStopwords(StopwordFilter stopwords) {
        this.stopwords = stopwords;
    }

    public PunctuationFilter getPunctuation() {
        return punctuation;
    }

    public void setPunctuation(PunctuationFilter punctuation) {
        this.punctuation = punctuation;
    }

    public PluralFilter getPlural() {
        return plural;
    }

    public void setPlural(PluralFilter plural) {
        this.plural = plural;
    }

    public NounFilter getNoun() {
        return noun;
    }

    public void setNoun(NounFilter noun) {
        this.noun = noun;
    }

    public CapitalisationFilter getCapitalisation() {
        return capitalisation;
    }

    public void setCapitalisation(CapitalisationFilter capitalisation) {
        this.capitalisation = capitalisation;
    }

    public TitleWeightFilter getTitleWeight() {
        return titleWeight;
    }

    public void setTitleWeight(TitleWeightFilter titleWeight) {
        this.titleWeight = titleWeight;
    }

    public MinSourcesFilter getMinSources() {
        return minSources;
    }

    public void setMinSources(MinSourcesFilter minSources) {
        this.minSources = minSources;
    }

    public MinCountriesFilter getMinCountries() {
        return minCountries;
    }

    public void setMinCountries(MinCountriesFilter minCountries) {
        this.minCountries = minCountries;
    }

    public RecencyFilter getRecency() {
        return recency;
    }

    public void setRecency(RecencyFilter recency) {
        this.recency = recency;
    }

    public MinLengthFilter getMinLength() {
        return minLength;
    }

    public void setMinLength(MinLengthFilter minLength) {
        this.minLength = minLength;
    }

    public NumericFilter getNumeric() {
        return numeric;
    }

    public void setNumeric(NumericFilter numeric) {
        this.numeric = numeric;
    }

    public PhraseFilter getPhrase() {
        return phrase;
    }

    public void setPhrase(PhraseFilter phrase) {
        this.phrase = phrase;
    }

    public MultiWordOnlyFilter getMultiWordOnly() {
        return multiWordOnly;
    }

    public void setMultiWordOnly(MultiWordOnlyFilter multiWordOnly) {
        this.multiWordOnly = multiWordOnly;
    }

    public CountOncePerArticleFilter getCountOncePerArticle() {
        return countOncePerArticle;
    }

    public void setCountOncePerArticle(CountOncePerArticleFilter countOncePerArticle) {
        this.countOncePerArticle = countOncePerArticle;
    }

    public PhraseRollupFilter getPhraseRollup() {
        return phraseRollup;
    }

    public void setPhraseRollup(PhraseRollupFilter phraseRollup) {
        this.phraseRollup = phraseRollup;
    }

    public MergeOverlapFilter getMergeOverlap() {
        return mergeOverlap;
    }

    public void setMergeOverlap(MergeOverlapFilter mergeOverlap) {
        this.mergeOverlap = mergeOverlap;
    }

    public RegionFilter getRegion() {
        return region;
    }

    public void setRegion(RegionFilter region) {
        this.region = region;
    }

    public HideSportsFilter getHideSports() {
        return hideSports;
    }

    public void setHideSports(HideSportsFilter hideSports) {
        this.hideSports = hideSports;
    }

    public HideIranWarFilter getHideIranWar() {
        return hideIranWar;
    }

    public void setHideIranWar(HideIranWarFilter hideIranWar) {
        this.hideIranWar = hideIranWar;
    }

    public HideMideastConflictFilter getHideMideastConflict() {
        return hideMideastConflict;
    }

    public void setHideMideastConflict(HideMideastConflictFilter hideMideastConflict) {
        this.hideMideastConflict = hideMideastConflict;
    }

    // ===== nested filter configs =====

    public static class StopwordFilter {
        private boolean enabled = true;
        /** When non-null/non-empty, overrides the server's default stopword list. */
        private List<String> words;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getWords() {
            return words;
        }

        public void setWords(List<String> words) {
            this.words = words;
        }
    }

    public static class PunctuationFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PluralFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class NounFilter {
        private boolean enabled = true;
        /** Tokens shorter than this are not considered nouns. */
        private int minLength = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }
    }

    public static class CapitalisationFilter {
        private boolean enabled = true;
        /**
         * If true, only words that appear capitalised mid-sentence survive.
         * If false, such words are simply boosted by {@link #boost}.
         */
        private boolean requireCapitalised = false;
        /** Multiplier applied to the score of words detected as proper nouns. */
        private double boost = 2.0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireCapitalised() {
            return requireCapitalised;
        }

        public void setRequireCapitalised(boolean requireCapitalised) {
            this.requireCapitalised = requireCapitalised;
        }

        public double getBoost() {
            return boost;
        }

        public void setBoost(double boost) {
            this.boost = boost;
        }
    }

    public static class TitleWeightFilter {
        private boolean enabled = true;
        private double titleWeight = 3.0;
        private double contentWeight = 1.0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getTitleWeight() {
            return titleWeight;
        }

        public void setTitleWeight(double titleWeight) {
            this.titleWeight = titleWeight;
        }

        public double getContentWeight() {
            return contentWeight;
        }

        public void setContentWeight(double contentWeight) {
            this.contentWeight = contentWeight;
        }
    }

    public static class MinSourcesFilter {
        private boolean enabled = true;
        private int minSources = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinSources() {
            return minSources;
        }

        public void setMinSources(int minSources) {
            this.minSources = minSources;
        }
    }

    /** Requires a topic to appear in feeds from at least {@code minCountries} countries. */
    public static class MinCountriesFilter {
        private boolean enabled = true;
        private int minCountries = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinCountries() {
            return minCountries;
        }

        public void setMinCountries(int minCountries) {
            this.minCountries = minCountries;
        }
    }

    public static class RecencyFilter {
        private boolean enabled = true;
        /** Articles older than this many hours are dropped entirely. */
        private int maxAgeHours = 24;
        /**
         * Half-life of the recency boost in hours (0 = age ignored for scoring, cutoff only;
         * smaller = newer articles count for much more).
         */
        private double halfLifeHours = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAgeHours() {
            return maxAgeHours;
        }

        public void setMaxAgeHours(int maxAgeHours) {
            this.maxAgeHours = maxAgeHours;
        }

        public double getHalfLifeHours() {
            return halfLifeHours;
        }

        public void setHalfLifeHours(double halfLifeHours) {
            this.halfLifeHours = halfLifeHours;
        }
    }

    public static class MinLengthFilter {
        private boolean enabled = true;
        private int minLength = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }
    }

    /** Drops pure-number / date tokens such as "250", "31st", "2026". */
    public static class NumericFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PhraseFilter {
        private boolean enabled = true;
        /** Longest phrase (in words) to generate. 1 = single words only. */
        private int maxWords = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxWords() {
            return maxWords;
        }

        public void setMaxWords(int maxWords) {
            this.maxWords = maxWords;
        }
    }

    public static class MultiWordOnlyFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** When enabled, a topic only adds to its score once per article (repeats ignored). */
    public static class CountOncePerArticleFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Excludes sport articles from the corpus when enabled. */
    public static class HideSportsFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Excludes Iran-war / Iran-conflict articles from the corpus when enabled. */
    public static class HideIranWarFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Excludes Israel / Lebanon / Gaza conflict articles from the corpus when enabled. */
    public static class HideMideastConflictFilter {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PhraseRollupFilter {
        private boolean enabled = true;
        /**
         * A shorter phrase is only rolled up into a longer one if that longer phrase was
         * itself mentioned in at least this many articles (guards against absorbing strong
         * short topics into a longer phrase that barely appears).
         */
        private int minContainerMentions = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinContainerMentions() {
            return minContainerMentions;
        }

        public void setMinContainerMentions(int minContainerMentions) {
            this.minContainerMentions = minContainerMentions;
        }
    }

    /**
     * Merges near-duplicate overlapping phrases that come from the same story (e.g. "alleged
     * drug boat", "drug boat kills", "strike alleged drug") into a single topic.
     */
    public static class MergeOverlapFilter {
        private boolean enabled = true;
        /**
         * Two phrases are merged when they share at least this fraction of their source
         * articles AND share at least one word. 0.5 = half the articles overlap.
         */
        private double minArticleOverlap = 0.5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getMinArticleOverlap() {
            return minArticleOverlap;
        }

        public void setMinArticleOverlap(double minArticleOverlap) {
            this.minArticleOverlap = minArticleOverlap;
        }
    }

    /**
     * Restricts the corpus to a chosen set of regions. When disabled, or when {@code regions}
     * is null/empty, all regions are included.
     */
    public static class RegionFilter {
        private boolean enabled = false;
        /** Region names to keep (e.g. "Australia", "Americas", "Europe", "Asia"). */
        private List<String> regions;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getRegions() {
            return regions;
        }

        public void setRegions(List<String> regions) {
            this.regions = regions;
        }
    }
}
