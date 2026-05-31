package com.trendingnews.app.service;

import com.trendingnews.app.model.Article;
import com.trendingnews.app.model.FilterSettings;
import com.trendingnews.app.model.TrendingResult;
import com.trendingnews.app.model.TrendingTopic;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        // Hide-sports, hide-Iran-war and hide-mideast-conflict are on by default; disable them
        // here so test corpora aren't unexpectedly filtered. Dedicated tests enable them.
        s.getHideSports().setEnabled(false);
        s.getHideIranWar().setEnabled(false);
        s.getHideMideastConflict().setEnabled(false);
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

    @Test
    void blockedBoilerplatePhraseNeverSurfaces() {
        List<Article> corpus = List.of(
                article("BBC", "Latest News Bulletin from around the world"),
                article("BBC", "Latest News Bulletin evening edition"),
                article("BBC", "Latest News Bulletin morning edition"));

        FilterSettings s = baseSettings();
        s.getPhrase().setEnabled(true);
        s.getPhrase().setMaxWords(3);
        s.getStopwords().setEnabled(false); // ensure the words themselves aren't filtered out

        TrendingResult r = new TrendingService(cacheOf(corpus), new StopwordService()).compute(s);

        assertNull(find(r, "latest news bulletin"),
                "the boilerplate phrase 'latest news bulletin' must never surface");
    }

    @Test
    void hideSportsExcludesSportArticles() {
        List<Article> corpus = List.of(
                article("BBC", "Arsenal football title"),
                article("CNN", "Election results overnight"));

        FilterSettings s = baseSettings();
        s.getMultiWordOnly().setEnabled(false);
        s.getPhrase().setEnabled(false); // single words only, so terms aren't crowded out of top-N
        s.getHideSports().setEnabled(true);

        TrendingService service = new TrendingService(cacheOf(corpus), new StopwordService());
        TrendingResult r = service.compute(s);

        // The sport article is dropped, so its words don't appear...
        assertNull(find(r, "arsenal"), "sport article should be excluded");
        assertNull(find(r, "football"));
        // ...but the non-sport article still contributes.
        assertNotNull(find(r, "election"), "non-sport article should remain");
        assertEquals(1, r.getArticlesConsidered(), "only the non-sport article is considered");

        // With the filter off, both articles are considered again.
        s.getHideSports().setEnabled(false);
        TrendingResult r2 = service.compute(s);
        assertEquals(2, r2.getArticlesConsidered());
        assertNotNull(find(r2, "arsenal"), "sport article should be back when the filter is off");
    }

    @Test
    void hideIranWarExcludesOnlyIranConflictArticles() {
        List<Article> corpus = List.of(
                article("BBC", "Iran missile strike escalates conflict"),
                article("CNN", "Iran unveils new cultural festival in Tehran"),
                article("NPR", "Local council approves budget"));

        FilterSettings s = baseSettings();
        s.getMultiWordOnly().setEnabled(false);
        s.getPhrase().setEnabled(false);
        s.getHideIranWar().setEnabled(true);

        TrendingService service = new TrendingService(cacheOf(corpus), new StopwordService());
        TrendingResult r = service.compute(s);

        // The Iran-war article is dropped (Iran + conflict terms), leaving 2 considered...
        assertEquals(2, r.getArticlesConsidered(), "the Iran war article should be excluded");
        assertNull(find(r, "missile"), "Iran war article should be excluded");
        // ...but a general Iran article (no war terms) survives...
        assertNotNull(find(r, "festival"), "non-war Iran article should remain");
        // ...and unrelated articles are untouched.
        assertNotNull(find(r, "council"));
    }

    @Test
    void hideMideastConflictExcludesOnlyConflictArticles() {
        List<Article> corpus = List.of(
                article("BBC", "Israel airstrike on Gaza kills dozens"),
                article("CNN", "Hezbollah rockets strike northern Lebanon"),
                article("NPR", "Israel unveils new tech startup festival"),
                article("AP", "Local council approves budget"));

        FilterSettings s = baseSettings();
        s.getMultiWordOnly().setEnabled(false);
        s.getPhrase().setEnabled(false);
        s.getHideMideastConflict().setEnabled(true);

        TrendingService service = new TrendingService(cacheOf(corpus), new StopwordService());
        TrendingResult r = service.compute(s);

        // The two conflict articles (place + war terms) are dropped, leaving 2 considered...
        assertEquals(2, r.getArticlesConsidered(), "Israel/Gaza and Lebanon conflict articles excluded");
        assertNull(find(r, "airstrike"));
        assertNull(find(r, "rockets"));
        // ...a general Israel article (no war terms) survives...
        assertNotNull(find(r, "startup"), "non-conflict Israel article should remain");
        // ...and unrelated articles are untouched.
        assertNotNull(find(r, "council"));
    }
}
