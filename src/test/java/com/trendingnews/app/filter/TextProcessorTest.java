package com.trendingnews.app.filter;

import com.trendingnews.app.filter.TextProcessor.RawToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the token-level transformations that back the filter pipeline.
 */
class TextProcessorTest {

    @Test
    void stripsPunctuation() {
        assertEquals("covid", TextProcessor.stripPunctuation("covid,"));
        assertEquals("US", TextProcessor.stripPunctuation("(U.S.)")); // dots removed, letters kept
        assertEquals("don't", TextProcessor.stripPunctuation("\"don't\""));
        assertEquals("end-of-year", TextProcessor.stripPunctuation("end-of-year!"));
    }

    @Test
    void singularizesCommonPlurals() {
        assertEquals("bank", TextProcessor.singularize("banks"));
        assertEquals("city", TextProcessor.singularize("cities"));
        assertEquals("box", TextProcessor.singularize("boxes"));
        assertEquals("dish", TextProcessor.singularize("dishes"));
        // Words that should not be touched.
        assertEquals("virus", TextProcessor.singularize("virus"));
        assertEquals("press", TextProcessor.singularize("press"));
        assertEquals("analysis", TextProcessor.singularize("analysis"));
    }

    @Test
    void detectsProperNounsByMidSentenceCapitalisation() {
        // tokens: [the(0) President(1) met(2) Apple(3) chief(4) today(5)]
        List<RawToken> tokens = TextProcessor.tokenize("the President met Apple chief today");
        // "the" starts the clause -> not proper even if capitalised
        assertFalse(TextProcessor.isProperNounCandidate(tokens.get(0)));
        // "President" is mid-sentence and capitalised -> proper noun candidate
        assertTrue(TextProcessor.isProperNounCandidate(tokens.get(1)));
        assertTrue(TextProcessor.isProperNounCandidate(tokens.get(3))); // Apple
        assertFalse(TextProcessor.isProperNounCandidate(tokens.get(2))); // met (lowercase)
        assertFalse(TextProcessor.isProperNounCandidate(tokens.get(5))); // today (lowercase)
    }

    @Test
    void clauseStartResetsAfterSentencePunctuation() {
        List<RawToken> tokens = TextProcessor.tokenize("Markets fell. Stocks rose later.");
        // "Stocks" follows a full stop, so it starts a clause and is not a proper-noun signal.
        RawToken stocks = tokens.stream().filter(t -> t.raw().startsWith("Stocks")).findFirst().orElseThrow();
        assertTrue(stocks.startsClause());
        assertFalse(TextProcessor.isProperNounCandidate(stocks));
    }

    @Test
    void nounHeuristicRejectsAdverbsKeepsNouns() {
        assertFalse(TextProcessor.looksLikeNoun("quickly"));   // -ly adverb
        assertFalse(TextProcessor.looksLikeNoun("beautiful")); // -ful adjective
        assertTrue(TextProcessor.looksLikeNoun("government")); // -ment noun
        assertTrue(TextProcessor.looksLikeNoun("bank"));       // plain noun, kept
    }
}
