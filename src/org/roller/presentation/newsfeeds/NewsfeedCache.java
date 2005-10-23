package org.roller.presentation.newsfeeds;import java.io.InputStreamReader;import java.net.URL;import org.apache.commons.logging.Log;import org.apache.commons.logging.LogFactory;import org.roller.util.LRUCache2;import com.sun.syndication.feed.synd.SyndFeed;import com.sun.syndication.io.SyndFeedInput;import java.io.UnsupportedEncodingException;import java.net.URLConnection;import org.roller.config.RollerConfig;/** * Returns parsed RSS feed by pulling one from a cache or by retrieving and * parging the specified feed using the Flock RSS parser. * <br /> * TODO: use PlanetRoller to implement NewsfeedCache instead. * <br /> * @author Lance Lavandowska * @author Dave Johnson */public class NewsfeedCache{    private static Log mLogger = LogFactory.getFactory().getInstance(            NewsfeedCache.class);    /** Static singleton * */    private static NewsfeedCache mInstance = null;    /** Instance vars * */    private boolean aggregator_enabled = true;    private boolean aggregator_cache_enabled = true;    private int aggregator_cache_timeout = 14400;    /** LRU cache */    LRUCache2 mCache = null;    /** Constructor */    private NewsfeedCache()    {        // lookup the props we need        String enabled = RollerConfig.getProperty("aggregator.enabled");        String usecache = RollerConfig.getProperty("aggregator.cache.enabled");        String cachetime = RollerConfig.getProperty("aggregator.cache.timeout");                if("true".equalsIgnoreCase(enabled))            this.aggregator_enabled = true;                if("true".equalsIgnoreCase(usecache))            this.aggregator_cache_enabled = true;                try {            this.aggregator_cache_timeout = Integer.parseInt(cachetime);        } catch(Exception e) { mLogger.warn(e); }                // finally ... create the cache        this.mCache = new LRUCache2(100, 1000 * this.aggregator_cache_timeout);    }    /** static singleton retriever */    public static NewsfeedCache getInstance()    {        synchronized (NewsfeedCache.class)        {            if (mInstance == null)            {                if (mLogger.isDebugEnabled())                {                    mLogger.debug("Instantiating new NewsfeedCache");                }                mInstance = new NewsfeedCache();            }        }        return mInstance;    }    /**     * Returns a Channel object for the supplied RSS newsfeed URL.     *      * @param feedUrl     *            RSS newsfeed URL.     * @return FlockFeedI for specified RSS newsfeed URL.     */    public SyndFeed getChannel(String feedUrl)    {        SyndFeed feed = null;        try        {            // If aggregator has been disable return null            if (!aggregator_enabled)            {                return null;            }            if (aggregator_cache_enabled)            {                if (mLogger.isDebugEnabled())                {                    mLogger.debug("Newsfeed: use Cache for " + feedUrl);                }                // Get pre-parsed feed from the cache                feed = (SyndFeed) mCache.get(feedUrl);                if (mLogger.isDebugEnabled())                {                    mLogger.debug("Newsfeed: got from Cache");                }                if (feed == null)                {                    try                    {                        // charset fix from Jason Rumney (see ROL-766)                        URLConnection connection = new URL(feedUrl).openConnection();                        connection.connect();                        String contentType = connection.getContentType();                        // Default charset to UTF-8, since we are expecting XML                        String charset = "UTF8";                        if (contentType != null) {                            int charsetStart = contentType.indexOf("charset=");                            if (charsetStart >= 0) {                                int charsetEnd = contentType.indexOf(";", charsetStart);                                if (charsetEnd == -1) charsetEnd = contentType.length();                                charsetStart += "charset=".length();                                charset = contentType.substring(charsetStart, charsetEnd);                                // Check that charset is recognized by Java                                try {                                    byte[] test = "test".getBytes(charset);                                }                                catch (UnsupportedEncodingException codingEx) {                                    // default to UTF-8                                    charset = "UTF8";                                }                            }                        }                         // Parse the feed                        SyndFeedInput feedInput = new SyndFeedInput();                        feed = feedInput.build(new InputStreamReader(                        connection.getInputStream(), charset));                     }                    catch (Exception e1)                    {                        mLogger.info("Error parsing RSS: " + feedUrl, e1);                    }                }                // Store parsed feed in the cache                mCache.put(feedUrl, feed);                mLogger.debug("Newsfeed: not in Cache");            }            else            {                if (mLogger.isDebugEnabled())                {                    mLogger.debug("Newsfeed: not using Cache for " + feedUrl);                }                try                {                    // Parse the feed                    SyndFeedInput feedInput = new SyndFeedInput();                    feed = feedInput.build(new InputStreamReader(                            new URL(feedUrl).openStream()));                }                catch (Exception e1)                {                    mLogger.info("Error parsing RSS: " + feedUrl);                }            }        }        catch (Exception ioe)        {            if (mLogger.isDebugEnabled())            {                mLogger.debug("Newsfeed: Unexpected exception", ioe);            }        }        return feed;    }}