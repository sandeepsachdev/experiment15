package com.trendingnews.app.rss;

import com.trendingnews.app.model.NewsSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The curated set of worldwide RSS/Atom feeds the app polls.
 *
 * <p>Coverage: Australia (4), Americas (9, incl. 3 Canadian), Europe (10, incl. 6 British)
 * and Asia/Middle East (6). Each source also carries a country code so topics can be
 * required to appear across feeds from multiple countries.
 * Individual feeds occasionally change URLs or go offline; the poller degrades gracefully
 * and simply skips any source it cannot fetch.
 */
@Component
public class NewsSourceRegistry {

    private static final List<NewsSource> SOURCES = List.of(
            // ---------------- Australia ----------------
            new NewsSource("ABC News (AU)", "Australia", "Australia", "https://www.abc.net.au/news/feed/2942460/rss.xml"),
            new NewsSource("Sydney Morning Herald", "Australia", "Australia", "https://www.smh.com.au/rss/feed.xml"),
            new NewsSource("The Guardian (AU)", "Australia", "Australia", "https://www.theguardian.com/australia-news/rss"),
            new NewsSource("SBS News", "Australia", "Australia", "https://www.sbs.com.au/news/feed"),

            // ---------------- Americas ----------------
            new NewsSource("CNN", "Americas", "USA", "http://rss.cnn.com/rss/cnn_topstories.rss"),
            new NewsSource("New York Times", "Americas", "USA", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
            new NewsSource("NPR", "Americas", "USA", "https://feeds.npr.org/1001/rss.xml"),
            new NewsSource("Washington Post", "Americas", "USA", "https://feeds.washingtonpost.com/rss/world"),
            new NewsSource("Fox News", "Americas", "USA", "https://moxie.foxnews.com/google-publisher/latest.xml"),
            new NewsSource("USA Today", "Americas", "USA", "https://rssfeeds.usatoday.com/usatoday-NewsTopStories"),
            new NewsSource("CBC News (CA)", "Americas", "Canada", "https://www.cbc.ca/webfeed/rss/rss-topstories"),
            new NewsSource("Global News (CA)", "Americas", "Canada", "https://globalnews.ca/feed/"),
            new NewsSource("CTV News (CA)", "Americas", "Canada", "https://www.ctvnews.ca/rss/ctvnews-ca-top-stories-public-rss-1.822009"),

            // ---------------- Europe ----------------
            new NewsSource("BBC News", "Europe", "UK", "https://feeds.bbci.co.uk/news/rss.xml"),
            new NewsSource("The Guardian (UK)", "Europe", "UK", "https://www.theguardian.com/uk/rss"),
            new NewsSource("Deutsche Welle", "Europe", "Germany", "https://rss.dw.com/rdf/rss-en-all"),
            new NewsSource("France 24", "Europe", "France", "https://www.france24.com/en/rss"),
            new NewsSource("Euronews", "Europe", "France", "https://www.euronews.com/rss"),
            new NewsSource("The Independent", "Europe", "UK", "https://www.independent.co.uk/news/rss"),
            new NewsSource("Sky News", "Europe", "UK", "https://feeds.skynews.com/feeds/rss/world.xml"),
            new NewsSource("The Telegraph (UK)", "Europe", "UK", "https://www.telegraph.co.uk/news/rss.xml"),
            new NewsSource("Daily Mail (UK)", "Europe", "UK", "https://www.dailymail.co.uk/articles.rss"),
            new NewsSource("Metro (UK)", "Europe", "UK", "https://metro.co.uk/feed/"),

            // ---------------- Asia / Middle East ----------------
            new NewsSource("Al Jazeera", "Asia", "Qatar", "https://www.aljazeera.com/xml/rss/all.xml"),
            new NewsSource("Times of India", "Asia", "India", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"),
            new NewsSource("The Japan Times", "Asia", "Japan", "https://www.japantimes.co.jp/feed/"),
            new NewsSource("South China Morning Post", "Asia", "Hong Kong", "https://www.scmp.com/rss/91/feed"),
            new NewsSource("Channel NewsAsia", "Asia", "Singapore", "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml"),
            new NewsSource("The Straits Times", "Asia", "Singapore", "https://www.straitstimes.com/news/world/rss.xml")
    );

    public List<NewsSource> getSources() {
        return SOURCES;
    }
}
