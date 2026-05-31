package com.trendingnews.app.service;

import com.trendingnews.app.model.Article;
import com.trendingnews.app.model.FilterSettings;
import com.trendingnews.app.model.TrendingResult;
import com.trendingnews.app.model.TrendingTopic;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the multi-word phrase generation and the sub-phrase rollup filter against a
 * small, in-memory corpus (real feeds are not reachable from the test environment).
 */
class TrendingServiceTest {

    /** A cache stub that returns a fixed list of articles. */
    private static ArticleCacheService cacheOf(List<Article> articles) {
        return new ArticleCacheService(null, null) {
            @Override
            public List<Article> getArticles() {
                return articles;
            }

            @Override
            public int getSourceCount() {
                return 3;
            }

            @Override
            public Instant getLastRefreshed() {
                return Instant.now();
            }
        };
    }

    private static Article article(String source, String title) {
        return new Article(title, "", "http://example.com/" + title.hashCode(), source, "Americas", Instant.now());
    }

    private static TrendingTopic find(TrendingResult r, String term) {
        return r.getTopics().stream().filter(t -> t.getTerm().equals(term)).findFirst().orElse(null);
    }

    private FilterSettings baseSettings() {
        // Disable recency/title weighting so the test is deterministic and not time-sensitive.
        FilterSettings s = FilterSettings.withDefaults();
        s.getRecency().setEnabled(false);
        s.getTitleWeight().setEnabled(false);
        s.getMinLength().setMinLength(3);
        // Multi-word-only is on by default; turn it off here so the single-word assertions in
        // most tests exercise the intended behaviour. The dedicated test enables it explicitly.
        s.getMultiWordOnly().setEnabled(false);
        return s;
    }

    @Test
    void surfacesMultiWordTopics() {
        List<Article> corpus = List.of(
                article("CNN", "Donald Trump rally draws crowds"),
                article("BBC", "Donald Trump speaks on economy"),
                article("NPR", "Donald Trump campaign update"));

        FilterSettings s = baseSettings();
        s.getPhrase().setEnabled(true);
        s.getPhrase().setMaxWords(3);
        s.getPhraseRollup().setEnabled(false);

        TrendingResult r = new TrendingService(cacheOf(corpus), new StopwordService()).compute(s);

        TrendingTopic phrase = find(r, "donald trump");
        assertNotNull(phrase, "expected the multi-word topic 'donald trump' to appear");
        assertTrue(phrase.getMentions() >= 3, "phrase should be counted in all three articles");
        // Single words still present when rollup is OFF.
        assertNotNull(find(r, "donald"), "single word 'donald' should also be present without rollup");
    }

    @Test
    void rollupAbsorbsShorterPhrasesIntoLongerOnesButLeavesSingleWords() {
        List<Article> corpus = List.of(
                article("CNN", "President Donald Trump rally"),
                article("BBC", "President Donald Trump economy"),
                article("NPR", "President Donald Trump campaign"));

        FilterSettings s = baseSettings();
        s.getPhrase().setEnabled(true);
        s.getPhrase().setMaxWords(3);
        s.getPhraseRollup().setEnabled(true);
        s.getPhraseRollup().setMinContainerMentions(2);

        TrendingService service = new TrendingService(cacheOf(corpus), new StopwordService());
        TrendingResult r = service.compute(s);

        // The 2-word phrase "donald trump" is a subset of "president donald trump" and is
        // rolled up into it.
        assertNotNull(find(r, "president donald trump"), "the longest phrase should survive");
        assertNull(find(r, "donald trump"), "the 2-word sub-phrase should be absorbed");

        // Single words are NOT rolled up — they remain as their own topics.
        assertNotNull(find(r, "donald"), "single word 'donald' must be kept");
        assertNotNull(find(r, "trump"), "single word 'trump' must be kept");
    }

    @Test
    void rollupCanBeDisabled() {
        List<Article> corpus = List.of(
                article("CNN", "Donald Trump rally"),
                article("BBC", "Donald Trump economy"));

        FilterSettings s = baseSettings();
        s.getPhrase().setEnabled(true);
        s.getPhraseRollup().setEnabled(false);

        TrendingResult r = new TrendingService(cacheOf(corpus), new StopwordService()).compute(s);
        // Both the words and the phrase coexist when rollup is off.
        assertNotNull(find(r, "donald"));
        assertNotNull(find(r, "donald trump"));
    }

    @Test
    void multiWordOnlySuppressesSingleWordTopics() {
        List<Article> corpus = List.of(
                article("CNN", "Donald Trump rally"),
                article("BBC", "Donald Trump economy"),
                article("NPR", "Donald Trump campaign"));

        FilterSettings s = baseSettings();
        s.getPhraseRollup().setEnabled(false); // keep single words around unless suppressed
        s.getMultiWordOnly().setEnabled(true);

        TrendingResult r = new TrendingService(cacheOf(corpus), new StopwordService()).compute(s);

        // Every surfaced topic must contain at least one space (2+ words).
        assertTrue(r.getTopics().size() > 0, "expected some multi-word topics");
        for (TrendingTopic t : r.getTopics()) {
            assertTrue(t.getTerm().contains(" "),
                    "multi-word-only must hide single words, but found: " + t.getTerm());
        }
        assertNotNull(find(r, "donald trump"));
        assertNull(find(r, "donald"), "single word 'donald' must be suppressed");
    }
}
