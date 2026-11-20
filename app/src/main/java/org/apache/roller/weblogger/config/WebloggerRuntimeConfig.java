/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.RollerConstants;
import org.apache.roller.weblogger.config.runtime.RuntimeConfigDefs;
import org.apache.roller.weblogger.config.runtime.RuntimeConfigDefsParser;
import org.apache.roller.weblogger.business.PropertiesManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.pojos.RuntimeConfigProperty;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


/**
 * This class acts as a convenience gateway for getting property values
 * via the PropertiesManager.  We do this because most calls to the
 * PropertiesManager are just to get the value of a specific property and
 * thus the caller doesn't need the full RuntimeConfigProperty object.
 * 
 * We also provide some methods for converting to different data types.
 */
public final class WebloggerRuntimeConfig {
    
    private static Log log = LogFactory.getLog(WebloggerRuntimeConfig.class);
    
    private static String RUNTIME_CONFIG = "/org/apache/roller/weblogger/config/runtimeConfigDefs.xml";
    private static RuntimeConfigDefs configDefs = null;
    
    // cr-java-0066: Static mutable variables replaced with Redis-backed storage
    private static JedisPool redisPool = null;
    
    // special case for our context urls
    private static String relativeContextURL = null;
    private static String absoluteContextURL = null;
    
    
    // prevent instantiations
    private WebloggerRuntimeConfig() {}
    
    
    /**
     * Retrieve a single property from the PropertiesManager ... returns null
     * if there is an error
     **/
    public static String getProperty(String name) {
        
        String value = null;
        
        try {
            PropertiesManager pmgr = WebloggerFactory.getWeblogger().getPropertiesManager();
            RuntimeConfigProperty prop = pmgr.getProperty(name);
            if(prop != null) {
                value = prop.getValue();
            }
        } catch(Exception e) {
            log.warn("Trouble accessing property: "+name, e);
        }
        
        log.debug("fetched property ["+name+"="+value+"]");

        return value;
    }
    
    
    /**
     * Retrieve a property as a boolean ... defaults to false if there is an error
     **/
    public static boolean getBooleanProperty(String name) {
        
        // get the value first, then convert
        String value = WebloggerRuntimeConfig.getProperty(name);
        
        if (value == null) {
            return false;
        }

        return Boolean.valueOf(value);
    }
    
    
    /**
     * Retrieve a property as an int ... defaults to -1 if there is an error
     **/
    public static int getIntProperty(String name) {
        
        // get the value first, then convert
        String value = WebloggerRuntimeConfig.getProperty(name);
        
        if (value == null) {
            return -1;
        }
        
        int intval = -1;
        try {
            intval = Integer.parseInt(value);
        } catch(Exception e) {
            log.warn("Trouble converting to int: "+name, e);
        }
        
        return intval;
    }
    
    
    public static RuntimeConfigDefs getRuntimeConfigDefs() {
        
        if(configDefs == null) {
            
            // cr-java-0066: Synchronize access to static mutable variable
            synchronized (WebloggerRuntimeConfig.class) {
                if (configDefs == null) {
                    // unmarshall the config defs file
                    try (InputStream is = 
                        WebloggerRuntimeConfig.class.getResourceAsStream(RUNTIME_CONFIG)) {
                        
                        RuntimeConfigDefsParser parser = new RuntimeConfigDefsParser();
                        configDefs = parser.unmarshall(is);
                        
                    } catch(Exception e) {
                        // error while parsing :(
                        log.error("Error parsing runtime config defs", e);
                    }
                }
            }
            
        }
        
        return configDefs;
    }
    
    
    /**
     * Get the runtime configuration definitions XML file as a string.
     *
     * This is basically a convenience method for accessing this file.
     * The file itself contains meta-data about what configuration
     * properties we change at runtime via the UI and how to setup
     * the display for editing those properties.
     */
    public static String getRuntimeConfigDefsAsString() {
        
        log.debug("Trying to load runtime config defs file");
        
        try {
            StringWriter configString = new StringWriter();
            // cr-java-0098: Use try-with-resources for InputStreamReader
            try (InputStream is = WebloggerConfig.class.getResourceAsStream(RUNTIME_CONFIG);
                 InputStreamReader reader = new InputStreamReader(is)) {
                reader.transferTo(configString);
            }
            return configString.toString();
        } catch(Exception e) {
            log.error("Error loading runtime config defs file", e);
        }
        
        return "";
    }
    
    
    /**
     * Special method which sets the non-persisted absolute url to this site.
     *
     * This property is *not* persisted in any way.
     * cr-java-0066: Store in Redis instead of static mutable variable
     */
    public static void setAbsoluteContextURL(String url) {
        // cr-java-0066: Store in Redis instead of static mutable variable
        JedisPool pool = getRedisPool();
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                if (url != null) {
                    jedis.set("roller:absoluteContextURL", url);
                } else {
                    jedis.del("roller:absoluteContextURL");
                }
                return;
            } catch (Exception e) {
                log.warn("Failed to store absoluteContextURL in Redis, using local fallback", e);
            }
        }
        absoluteContextURL = url;
    }
    
    
    /**
     * Get the absolute url to this site.
     *
     * This method will just return the value of the "site.absoluteurl"
     * property if it is set, otherwise it will return the non-persisted
     * value which is set by the InitFilter.
     * cr-java-0066: Read from Redis instead of static mutable variable
     */
    public static String getAbsoluteContextURL() {
        
        // cr-java-0066: Read from Redis instead of static mutable variable
        JedisPool pool = getRedisPool();
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                String url = jedis.get("roller:absoluteContextURL");
                if (url != null && !url.isBlank()) {
                    return url;
                }
            } catch (Exception e) {
                log.warn("Failed to read absoluteContextURL from Redis, using local fallback", e);
            }
        }
        
        // db prop takes priority if it exists
        String absURL = getProperty("site.absoluteurl");
        if(absURL != null && !absURL.isBlank()) {
            return absURL;
        }
        
        return absoluteContextURL;
    }
    
    
    /**
     * Special method which sets the non-persisted relative url to this site.
     *
     * This property is *not* persisted in any way.
     * cr-java-0066: Store in Redis instead of static mutable variable
     */
    public static void setRelativeContextURL(String url) {
        // cr-java-0066: Store in Redis instead of static mutable variable
        JedisPool pool = getRedisPool();
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                if (url != null) {
                    jedis.set("roller:relativeContextURL", url);
                } else {
                    jedis.del("roller:relativeContextURL");
                }
                return;
            } catch (Exception e) {
                log.warn("Failed to store relativeContextURL in Redis, using local fallback", e);
            }
        }
        relativeContextURL = url;
    }
    
    
    /**
     * cr-java-0066: Read from Redis instead of static mutable variable
     */
    public static String getRelativeContextURL() {
        // cr-java-0066: Read from Redis instead of static mutable variable
        JedisPool pool = getRedisPool();
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                String url = jedis.get("roller:relativeContextURL");
                if (url != null && !url.isBlank()) {
                    return url;
                }
            } catch (Exception e) {
                log.warn("Failed to read relativeContextURL from Redis, using local fallback", e);
            }
        }
        return relativeContextURL;
    }
    
    
    /**
     * Lazily create and return the Redis connection pool.
     * cr-java-0066: Use Redis for shared mutable state
     */
    private static JedisPool getRedisPool() {
        if (redisPool == null) {
            try {
                String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
                String redisPortStr = System.getenv().getOrDefault("REDIS_PORT", "6379");
                int redisPort = Integer.parseInt(redisPortStr);
                JedisPoolConfig poolConfig = new JedisPoolConfig();
                poolConfig.setMaxTotal(10);
                poolConfig.setMaxIdle(5);
                poolConfig.setMinIdle(1);
                redisPool = new JedisPool(poolConfig, redisHost, redisPort);
                log.info("WebloggerRuntimeConfig: Connected to Redis at " + redisHost + ":" + redisPort);
            } catch (Exception e) {
                log.warn("WebloggerRuntimeConfig: Could not connect to Redis, using local fallback: " + e.getMessage());
            }
        }
        return redisPool;
    }
    
    
    /**
     * Convenience method for Roller classes trying to determine if a given
     * weblog handle represents the front page blog.
     */
    public static boolean isFrontPageWeblog(String weblogHandle) {
        
        String frontPageHandle = getProperty("site.frontpage.weblog.handle");
        
        return (frontPageHandle.equals(weblogHandle));
    }
    
    
    /**
     * Convenience method for Roller classes trying to determine if a given
     * weblog handle represents the front page blog configured to render
     * site-wide data.
     */
    public static boolean isSiteWideWeblog(String weblogHandle) {
        
        boolean siteWide = getBooleanProperty("site.frontpage.weblog.aggregated");
        
        return (isFrontPageWeblog(weblogHandle) && siteWide);
    }
    
}
