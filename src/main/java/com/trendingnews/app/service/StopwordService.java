package com.trendingnews.app.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * Holds the server-side default stopword list and lets it be updated at runtime.
 *
 * <p>The UI can also send a per-request word list with each trending query; this service
 * supplies the default when the request does not override it, and the "Manage word list"
 * panel persists changes here for the lifetime of the process.
 */
@Service
public class StopwordService {

    private static final List<String> DEFAULT_WORDS = Arrays.asList(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "when",
            "at", "by", "for", "with", "about", "against", "between", "into", "through",
            "during", "before", "after", "above", "below", "to", "from", "up", "down",
            "in", "out", "on", "off", "over", "under", "again", "further", "once",
            "is", "are", "was", "were", "be", "been", "being", "am", "do", "does", "did",
            "have", "has", "had", "having", "this", "that", "these", "those", "it", "its",
            "i", "you", "he", "she", "we", "they", "them", "his", "her", "their", "our",
            "my", "your", "as", "of", "so", "than", "too", "very", "can", "will", "just",
            "not", "no", "nor", "only", "own", "same", "such", "more", "most", "some",
            "what", "which", "who", "whom", "how", "why", "where", "all", "any", "both",
            "each", "few", "other", "new", "says", "said", "say", "get", "got", "one",
            "two", "now", "may", "also", "us", "could", "would", "should", "amp", "via",
            "read", "continue", "reading"
    );

    private final Set<String> words = new CopyOnWriteArraySet<>();

    public StopwordService() {
        words.addAll(DEFAULT_WORDS);
    }

    public Set<String> getWords() {
        // Preserve a stable, sorted view for the UI.
        return words.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Replace the entire list with the supplied words (lower-cased, blanks dropped). */
    public Set<String> replaceAll(List<String> newWords) {
        words.clear();
        if (newWords != null) {
            for (String w : newWords) {
                if (w != null && !w.isBlank()) {
                    words.add(w.trim().toLowerCase());
                }
            }
        }
        return getWords();
    }

    /** Restore the built-in default list. */
    public Set<String> resetToDefault() {
        words.clear();
        words.addAll(DEFAULT_WORDS);
        return getWords();
    }

    public List<String> getDefaultWords() {
        return DEFAULT_WORDS;
    }
}
