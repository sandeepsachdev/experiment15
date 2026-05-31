package com.trendingnews.app.model;

import java.time.Instant;

/**
 * A single news article harvested from an RSS/Atom feed.
 */
public class Article {

    private final String title;
    private final String description;
    private final String link;
    private final String sourceName;
    private final String region;
    private final Instant publishedAt;

    public Article(String title, String description, String link,
                   String sourceName, String region, Instant publishedAt) {
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.link = link == null ? "" : link;
        this.sourceName = sourceName;
        this.region = region;
        this.publishedAt = publishedAt;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
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

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
