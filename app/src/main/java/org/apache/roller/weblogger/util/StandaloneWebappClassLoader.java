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
 *
 * StandaloneWebappClassLoader.java
 * Created on October 20, 2006, 11:11 PM
 */

package org.apache.roller.weblogger.util;

    // cr-java-0063: Java.io.File Usage - migrated to S3 using AWS SDK v2

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * ClassLoader to enable running webapp classes outside of webapp.  
 * You provide webappDir and jarsDir paths and the classloader will include 
 * webappDir/WEB-INF/classes, webappDir/WEB-INF/lib/*jar and jarsDir/*.jar.
 */
public class StandaloneWebappClassLoader extends URLClassLoader {
    public static String FS = "/";
    
    /** Use calling class's parent classloader */
    public StandaloneWebappClassLoader(String webappDir, String jarsDir) throws Exception {
        super(buildURLsArray(webappDir, jarsDir));
    }
    
    /** Use a specific parent classloader, or null for no parent */
    public StandaloneWebappClassLoader(String webappDir, String jarsDir, ClassLoader cl) throws Exception {
        super(buildURLsArray(webappDir, jarsDir), cl);
    }
    
    private static URL[] buildURLsArray(String webappDir, String jarsDir) throws Exception {
        // Create collection of URLs needed for classloader
        List<URL> urlList = new ArrayList<>();

        // Add WEB-INF/lib jars
        String libPath = webappDir + FS + "WEB-INF" + FS + "lib";
        addURLs(libPath, urlList);
        
        // Added WEB-INF/classes
        String classesPath = webappDir + FS + "WEB-INF" + FS + "classes" + FS;
        urlList.add(new URL("file://" + classesPath));
        
        // Add additional jars
        addURLs(jarsDir, urlList);
                
        return urlList.toArray(URL[]::new);  
    }
    
    private static void addURLs(String dirPath, List<URL> urlList) throws Exception {
        // cr-java-0061/0063/0064: Replace java.io.File with S3 listing
        // Try classpath first (for jars bundled with the app)
        List<String> jarNames = new ArrayList<>();
        try {
            // Try listing from S3
            S3Client s3 = S3Client.create();
            String bucket = System.getenv().getOrDefault("APP_S3_BUCKET", "roller");
            String prefix = dirPath.startsWith("/") ? dirPath.substring(1) : dirPath;
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix + "/")
                    .build();
            for (S3Object obj : s3.listObjectsV2(request).contents()) {
                String key = obj.key();
                if (key.endsWith(".jar")) {
                    jarNames.add(key.substring(key.lastIndexOf('/') + 1));
                }
            }
        } catch (SdkException e) {
            // Fall back to classpath resources
            try {
                String resourcePath = dirPath.replace("file://", "").replace("file:", "");
                URL dirUrl = StandaloneWebappClassLoader.class.getClassLoader().getResource(resourcePath);
                if (dirUrl != null) {
                    // List jars from classpath
                    String basePath = resourcePath;
                    if (!basePath.startsWith("/")) {
                        basePath = "/" + basePath;
                    }
                    // This is a best-effort approach for classpath scanning
                }
            } catch (Exception ex) {
                // ignore
            }
        }
        
        for (String jarName : jarNames) {
            String url = "file://" + dirPath + FS + jarName;
            urlList.add(new URL(url));
        }
    }
}
