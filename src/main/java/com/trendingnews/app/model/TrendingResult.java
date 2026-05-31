package com.trendingnews.app.model;

import java.util.List;
import java.util.Map;

/**
 * The full response for a trending query: the ranked topics plus metadata describing the
 * corpus they were computed from. The UI uses the metadata for the status bar and to
 * caption preview/snapshot panels.
 */
public class TrendingResult {

    private final List<TrendingTopic> topics;
    private final int totalArticles;
    private final int articlesConsidered;
    private final int sourceCount;
    private final Map<String, Integer> articlesByRegion;
    private final String lastRefreshed;

    public TrendingResult(List<TrendingTopic> topics, int totalArticles, int articlesConsidered,
                          int sourceCount, Map<String, Integer> articlesByRegion, String lastRefreshed) {
        this.topics = topics;
        this.totalArticles = totalArticles;
        this.articlesConsidered = articlesConsidered;
        this.sourceCount = sourceCount;
        this.articlesByRegion = articlesByRegion;
        this.lastRefreshed = lastRefreshed;
    }

    public List<TrendingTopic> getTopics() {
        return topics;
    }

    public int getTotalArticles() {
        return totalArticles;
    }

    public int getArticlesConsidered() {
        return articlesConsidered;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public Map<String, Integer> getArticlesByRegion() {
        return articlesByRegion;
    }

    public String getLastRefreshed() {
        return lastRefreshed;
    }
}
