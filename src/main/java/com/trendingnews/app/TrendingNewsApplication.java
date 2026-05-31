package com.trendingnews.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Trending News application.
 *
 * <p>The app polls news RSS feeds from around the world on a schedule, caches the
 * articles in memory, and exposes a tunable trending-topic pipeline over a small REST API.
 */
@SpringBootApplication
@EnableScheduling
public class TrendingNewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrendingNewsApplication.class, args);
    }
}
