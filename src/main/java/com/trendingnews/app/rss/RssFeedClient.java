package com.trendingnews.app.rss;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.trendingnews.app.model.Article;
import com.trendingnews.app.model.NewsSource;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Fetches a single RSS/Atom feed and converts its entries into {@link Article}s.
 *
 * <p>Uses a real browser-like User-Agent and short timeouts because several publishers
 * reject the default Java agent or hang. HTML in titles/descriptions is stripped to text.
 */
@Component
public class RssFeedClient {

    private static final Logger log = LoggerFactory.getLogger(RssFeedClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; TrendingNewsBot/1.0; +https://github.com/trending-news)";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;

    public List<Article> fetch(NewsSource source) {
        List<Article> articles = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(source.getFeedUrl());
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);

            try (XmlReader reader = new XmlReader(connection)) {
                SyndFeed feed = new SyndFeedInput().build(reader);
                for (SyndEntry entry : feed.getEntries()) {
                    articles.add(toArticle(entry, source));
                }
            }
            log.info("Fetched {} articles from {}", articles.size(), source.getName());
        } catch (Exception e) {
            log.warn("Failed to fetch feed '{}' ({}): {}", source.getName(), source.getFeedUrl(), e.toString());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return articles;
    }

    private Article toArticle(SyndEntry entry, NewsSource source) {
        String title = clean(entry.getTitle());

        String description = "";
        if (entry.getDescription() != null) {
            description = clean(entry.getDescription().getValue());
        } else if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            description = clean(entry.getContents().get(0).getValue());
        }

        Instant published = null;
        Date date = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
        if (date != null) {
            published = date.toInstant();
        }

        return new Article(title, description, entry.getLink(), source.getName(), source.getRegion(), published);
    }

    /** Strip HTML markup and collapse whitespace. */
    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return Jsoup.parse(raw).text().trim();
    }
}
