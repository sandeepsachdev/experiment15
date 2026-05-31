package com.trendingnews.app.model;

import java.util.List;

/**
 * A trending topic (term) together with the score it earned and the articles that
 * mention it. Returned to the UI so the user can click through to the source stories.
 */
public class TrendingTopic {

    private final String term;
    private final double score;
    private final int mentions;
    private final int sourceCount;
    private final List<ArticleRef> articles;

    public TrendingTopic(String term, double score, int mentions, int sourceCount, List<ArticleRef> articles) {
        this.term = term;
        this.score = score;
        this.mentions = mentions;
        this.sourceCount = sourceCount;
        this.articles = articles;
    }

    public String getTerm() {
        return term;
    }

    public double getScore() {
        return score;
    }

    public int getMentions() {
        return mentions;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public List<ArticleRef> getArticles() {
        return articles;
    }

    /**
     * A lightweight, UI-facing view of an article that mentions a topic.
     */
    public static class ArticleRef {
        private final String title;
        private final String link;
        private final String sourceName;
        private final String region;
        private final String publishedAt;

        public ArticleRef(String title, String link, String sourceName, String region, String publishedAt) {
            this.title = title;
            this.link = link;
            this.sourceName = sourceName;
            this.region = region;
            this.publishedAt = publishedAt;
        }

        public String getTitle() {
            return title;
        }

        public String getLink() {
            return link;
        }

        public String getSourceName() {
            return sourceName;
        }

        public String getRegion() {
            return region;
        }

        public String getPublishedAt() {
            return publishedAt;
        }
    }
}
