package com.trendingnews.app.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stateless helpers that implement the individual token-level transformations the
 * filter pipeline relies on: tokenisation with capitalisation awareness, punctuation
 * stripping, plural collapsing and a lightweight noun heuristic.
 */
public final class TextProcessor {

    private TextProcessor() {
    }

    /** Noun-like word endings used by the (optional) noun-detection filter. */
    private static final String[] NOUN_SUFFIXES = {
            "tion", "sion", "ment", "ness", "ity", "ship", "ism", "ance", "ence",
            "age", "hood", "dom", "ist", "ician", "ology", "graphy"
    };

    /** Endings that strongly suggest a word is NOT a noun (adverb/adjective/verb). */
    private static final String[] NON_NOUN_SUFFIXES = {
            "ly", "ous", "ful", "ive", "able", "ible", "ical", "ing", "ized", "ised"
    };

    /**
     * A token as it was found in the source text, before normalisation.
     *
     * @param raw          the original token, with its original case and trailing punctuation
     * @param startsClause true when this token begins the field or follows sentence-ending
     *                     punctuation; such capitalisation is NOT a proper-noun signal
     */
    public record RawToken(String raw, boolean startsClause) {
    }

    /**
     * Split text into whitespace-delimited tokens, marking which ones begin a clause so the
     * capitalisation filter can tell "Apple" (proper noun) from a sentence-initial "Apple".
     */
    public static List<RawToken> tokenize(String text) {
        List<RawToken> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String[] parts = text.trim().split("\\s+");
        boolean clauseStart = true;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            tokens.add(new RawToken(part, clauseStart));
            // The next token starts a clause if this one ended a sentence.
            char last = part.charAt(part.length() - 1);
            clauseStart = (last == '.' || last == '!' || last == '?' || last == ':' || last == ';');
        }
        return tokens;
    }

    /** True when a raw token looks like a mid-clause proper noun (capitalised, not clause-initial). */
    public static boolean isProperNounCandidate(RawToken token) {
        if (token.startsClause()) {
            return false;
        }
        for (int i = 0; i < token.raw().length(); i++) {
            char c = token.raw().charAt(i);
            if (Character.isLetter(c)) {
                return Character.isUpperCase(c);
            }
            if (Character.isDigit(c)) {
                return false;
            }
        }
        return false;
    }

    /** Remove characters that are not letters/digits (keeping internal apostrophes & hyphens). */
    public static String stripPunctuation(String token) {
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '\'' || c == '-') {
                sb.append(c);
            }
        }
        // Trim stray leading/trailing apostrophes or hyphens left behind.
        int start = 0;
        int end = sb.length();
        while (start < end && (sb.charAt(start) == '\'' || sb.charAt(start) == '-')) {
            start++;
        }
        while (end > start && (sb.charAt(end - 1) == '\'' || sb.charAt(end - 1) == '-')) {
            end--;
        }
        return sb.substring(start, end);
    }

    /** Collapse a handful of common English plural forms to their singular. */
    public static String singularize(String word) {
        if (word.length() <= 3) {
            return word;
        }
        if (word.endsWith("ss") || word.endsWith("us") || word.endsWith("is")) {
            return word; // e.g. "press", "virus", "analysis"
        }
        if (word.endsWith("ies") && word.length() > 4) {
            return word.substring(0, word.length() - 3) + "y"; // cities -> city
        }
        if (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes")
                || word.endsWith("ses") || word.endsWith("zes")) {
            return word.substring(0, word.length() - 2); // boxes -> box, dishes -> dish
        }
        if (word.endsWith("s")) {
            return word.substring(0, word.length() - 1); // banks -> bank
        }
        return word;
    }

    /**
     * Lightweight noun heuristic: accept words with noun-like endings, reject words with
     * clear adverb/adjective/verb endings, and keep everything else (short common nouns
     * such as "bank" or "fire" have no distinctive suffix).
     */
    public static boolean looksLikeNoun(String word) {
        for (String suffix : NOUN_SUFFIXES) {
            if (word.endsWith(suffix)) {
                return true;
            }
        }
        for (String suffix : NON_NOUN_SUFFIXES) {
            if (word.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    /** Convenience: lower-case set membership test. */
    public static boolean isStopword(String word, Set<String> stopwords) {
        return stopwords.contains(word);
    }

    /**
     * True when {@code needle} appears as a contiguous run of words inside {@code haystack}.
     * Used by the phrase-rollup filter to decide when a shorter phrase is a subset of a
     * longer one (e.g. {@code ["donald","trump"]} is contained in
     * {@code ["former","president","donald","trump"]}).
     */
    public static boolean containsContiguous(String[] haystack, String[] needle) {
        if (needle.length == 0 || needle.length > haystack.length) {
            return false;
        }
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (!haystack[start + j].equals(needle[j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
