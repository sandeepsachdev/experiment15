package com.trendingnews.app.model;

/**
 * Configuration for a single RSS/Atom news source.
 */
public class NewsSource {

    private final String name;
    private final String region;
    private final String country;
    private final String feedUrl;

    public NewsSource(String name, String region, String country, String feedUrl) {
        this.name = name;
        this.region = region;
        this.country = country;
        this.feedUrl = feedUrl;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public String getCountry() {
        return country;
    }

    public String getFeedUrl() {
        return feedUrl;
    }
}
