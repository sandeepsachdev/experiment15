package com.trendingnews.app.rss;

import com.trendingnews.app.model.NewsSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The curated set of worldwide RSS/Atom feeds the app polls.
 *
 * <p>Coverage: Australia (4), Americas (6), Europe (7) and Asia/Middle East (6).
 * Individual feeds occasionally change URLs or go offline; the poller degrades gracefully
 * and simply skips any source it cannot fetch.
 */
@Component
public class NewsSourceRegistry {

    private static final List<NewsSource> SOURCES = List.of(
            // ---------------- Australia ----------------
            new NewsSource("ABC News (AU)", "Australia", "https://www.abc.net.au/news/feed/2942460/rss.xml"),
            new NewsSource("Sydney Morning Herald", "Australia", "https://www.smh.com.au/rss/feed.xml"),
            new NewsSource("The Guardian (AU)", "Australia", "https://www.theguardian.com/australia-news/rss"),
            new NewsSource("SBS News", "Australia", "https://www.sbs.com.au/news/feed"),

            // ---------------- Americas ----------------
            new NewsSource("CNN", "Americas", "http://rss.cnn.com/rss/cnn_topstories.rss"),
            new NewsSource("New York Times", "Americas", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
            new NewsSource("NPR", "Americas", "https://feeds.npr.org/1001/rss.xml"),
            new NewsSource("Washington Post", "Americas", "https://feeds.washingtonpost.com/rss/world"),
            new NewsSource("Fox News", "Americas", "https://moxie.foxnews.com/google-publisher/latest.xml"),
            new NewsSource("USA Today", "Americas", "https://rssfeeds.usatoday.com/usatoday-NewsTopStories"),

            // ---------------- Europe ----------------
            new NewsSource("BBC News", "Europe", "https://feeds.bbci.co.uk/news/rss.xml"),
            new NewsSource("The Guardian (UK)", "Europe", "https://www.theguardian.com/uk/rss"),
            new NewsSource("Deutsche Welle", "Europe", "https://rss.dw.com/rdf/rss-en-all"),
            new NewsSource("France 24", "Europe", "https://www.france24.com/en/rss"),
            new NewsSource("Euronews", "Europe", "https://www.euronews.com/rss"),
            new NewsSource("The Independent", "Europe", "https://www.independent.co.uk/news/rss"),
            new NewsSource("Sky News", "Europe", "https://feeds.skynews.com/feeds/rss/world.xml"),

            // ---------------- Asia / Middle East ----------------
            new NewsSource("Al Jazeera", "Asia", "https://www.aljazeera.com/xml/rss/all.xml"),
            new NewsSource("Times of India", "Asia", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"),
            new NewsSource("The Japan Times", "Asia", "https://www.japantimes.co.jp/feed/"),
            new NewsSource("South China Morning Post", "Asia", "https://www.scmp.com/rss/91/feed"),
            new NewsSource("Channel NewsAsia", "Asia", "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml"),
            new NewsSource("The Straits Times", "Asia", "https://www.straitstimes.com/news/world/rss.xml")
    );

    public List<NewsSource> getSources() {
        return SOURCES;
    }
}
