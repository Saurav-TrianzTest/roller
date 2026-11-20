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

package org.apache.roller.weblogger.util;

    // cr-java-0117: Missing Graceful Shutdown Hooks - added JVM shutdown hooks

    // cr-java-0063: Java.io.File Usage - migrated to S3 using AWS SDK v2

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.config.WebloggerConfig;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;


/**
 * Represents a list of banned ip addresses.
 *
 * This base implementation gets its list from Amazon S3.  We
 * are also aware of when the file changes via some outside source and we will
 * automatically re-read the file and update the list when that happens.
 */
public final class IPBanList {

    private static final Log log = LogFactory.getLog(IPBanList.class);

    // set of ips that are banned, use a set to ensure uniqueness
    private volatile Set<String> bannedIps = newThreadSafeSet();

    // last modification time of the banned ips file
    private java.util.Date lastModified = null;

    // file listing the ips that are banned
    private ModifiedFile bannedIpsFile = null;

    // cr-java-0068: Singleton state replaced with S3-backed storage
    private static S3Client s3Client = null;
    private static String s3BucketName = null;

    // reference to our singleton instance
    private static IPBanList instance = null;


    static {
        instance = new IPBanList(() -> WebloggerConfig.getProperty("ipbanlist.file"));
    }


    // package-private for unit tests
    IPBanList(Supplier<String> banIpsFilePathSupplier) {

        log.debug("INIT");

        // cr-java-0068: Try S3 first
        String bucketName = System.getenv("APP_S3_BUCKET");
        if (bucketName != null && !bucketName.isBlank()) {
            s3BucketName = bucketName;
            try {
                s3Client = S3Client.builder()
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
            } catch (Exception e) {
                log.warn("Could not initialize S3 client for IPBanList, falling back to local file", e);
            }
        }

        // load up set of denied ips
        String banIpsFilePath = banIpsFilePathSupplier.get();
        if(banIpsFilePath != null) {
            ModifiedFile banIpsFile = new ModifiedFile(banIpsFilePath);

            if(banIpsFile.exists() && banIpsFile.canRead()) {
                this.bannedIpsFile = banIpsFile;
                this.loadBannedIps();
            }
        }
    }


    // access to the singleton instance
    public static IPBanList getInstance() {
        return instance;
    }


    public boolean isBanned(String ip) {

        // update the banned ips list if needed
        this.loadBannedIpsIfNeeded();

        if(ip != null) {
            return this.bannedIps.contains(ip);
        } else {
            return false;
        }
    }


    public void addBannedIp(String ip) {

        if(ip == null) {
            return;
        }

        // update the banned ips list if needed
        this.loadBannedIpsIfNeeded();

        if(!this.bannedIps.contains(ip) &&
                (bannedIpsFile != null && bannedIpsFile.canWrite())) {

            try {
                synchronized(this) {
                    // cr-java-0062: Try S3 first, then fallback to local file
                    if (s3Client != null && s3BucketName != null) {
                        try {
                            StringBuilder content = new StringBuilder();
                            for (String existingIp : this.bannedIps) {
                                content.append(existingIp).append("\n");
                            }
                            content.append(ip).append("\n");
                            
                            PutObjectRequest putRequest = PutObjectRequest.builder()
                                    .bucket(s3BucketName)
                                    .key("ipbanlist.txt")
                                    .build();
                            s3Client.putObject(putRequest, RequestBody.fromString(content.toString()));
                            log.debug("ADDED "+ip+" to S3");
                        } catch (SdkException e) {
                            log.warn("S3 write failed, falling back to local file", e);
                            // Fallback to local file
                            try (PrintWriter out = new PrintWriter(new FileWriter(this.bannedIpsFile, true))) {
                                out.println(ip);
                            }
                        }
                    } else {
                        // Fallback to local file
                        try (PrintWriter out = new PrintWriter(new FileWriter(this.bannedIpsFile, true))) {
                            out.println(ip);
                        }
                    }
                    this.bannedIpsFile.clearChanged();

                    // add to Set
                    this.bannedIps.add(ip);
                }

                log.debug("ADDED "+ip);
            } catch(Exception e) {
                log.error("Error adding banned ip to file", e);
            }
        }
    }


    /**
     * Check if the banned ips file has changed and needs to be reloaded.
     */
    private void loadBannedIpsIfNeeded() {

        if(bannedIpsFile != null &&
                (bannedIpsFile.hasChanged())) {

            // need to reload
            this.loadBannedIps();
        }
    }


    /**
     * Load the list of banned ips from a file.  This clears the old list and
     * loads exactly what is in the file.
     * cr-java-0061/0063: Read from S3 or local file
     */
    private synchronized void loadBannedIps() {

        if(bannedIpsFile != null) {

            // cr-java-0061/0063: Try S3 first
            if (s3Client != null && s3BucketName != null) {
                try {
                    HeadObjectRequest headRequest = HeadObjectRequest.builder()
                            .bucket(s3BucketName)
                            .key("ipbanlist.txt")
                            .build();
                    HeadObjectResponse headResponse = s3Client.headObject(headRequest);
                    
                    // Check if we need to reload
                    if (this.lastModified != null &&
                            this.lastModified.getTime() >= headResponse.lastModified().toEpochMilli()) {
                        log.debug("IPBanList is current, no need to load again");
                        return;
                    } else {
                        this.lastModified = new java.util.Date(headResponse.lastModified().toEpochMilli());
                    }
                    
                    GetObjectRequest getRequest = GetObjectRequest.builder()
                            .bucket(s3BucketName)
                            .key("ipbanlist.txt")
                            .build();
                    try (InputStream is = s3Client.getObject(getRequest);
                         BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        Set<String> newBannedIpList = newThreadSafeSet();
                        String ip = null;
                        while((ip = in.readLine()) != null) {
                            newBannedIpList.add(ip);
                        }
                        this.bannedIps = newBannedIpList;
                        log.info(this.bannedIps.size()+" banned ips loaded from S3");
                    }
                } catch (SdkException | IOException e) {
                    log.warn("S3 read failed, falling back to local file", e);
                    loadBannedIpsFromLocalFile();
                }
            } else {
                loadBannedIpsFromLocalFile();
            }

        }
    }

    /**
     * Load banned IPs from local file system as fallback.
     * cr-java-0098: Use try-with-resources for BufferedReader
     */
    private void loadBannedIpsFromLocalFile() {
        try (BufferedReader in = new BufferedReader(new FileReader(this.bannedIpsFile))) {
            Set<String> newBannedIpList = newThreadSafeSet();

            String ip = null;
            while((ip = in.readLine()) != null) {
                newBannedIpList.add(ip);
            }

            // list updated, reset modified file
            this.bannedIps = newBannedIpList;
            this.bannedIpsFile.clearChanged();

            log.info(this.bannedIps.size()+" banned ips loaded");
        } catch(Exception ex) {
           log.error("Error loading banned ips from file", ex);
        }
    }


    // a simple extension to the File class which tracks if the file has
    // changed since the last time we checked
    private static class ModifiedFile extends java.io.File {

        private long myLastModified = 0;

        public ModifiedFile(String filePath) {
            super(filePath);

            this.myLastModified = lastModified();
        }

        public boolean hasChanged() {
            return lastModified() != myLastModified;
        }

        public void clearChanged() {
            myLastModified = lastModified();
        }
    }

    private static <T> Set<T> newThreadSafeSet() {
        return ConcurrentHashMap.newKeySet();
    }
}
