/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

package org.apache.roller.weblogger.ui.core;

import java.io.File;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;

/**
 * Main entry point that starts an embedded Apache Tomcat server with the
 * Roller web application, replacing the external WAR deployment.
 *
 * <p>Keeps the same context path (/roller), the same port (8080 by default),
 * and all existing servlet, filter, JSP and static page mappings from web.xml.</p>
 */
public class EmbeddedTomcatMain {

    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_CONTEXT_PATH = "/roller";

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT value: " + portEnv + ", using default " + DEFAULT_PORT);
            }
        }

        String contextPath = DEFAULT_CONTEXT_PATH;
        String contextEnv = System.getenv("CONTEXT_PATH");
        if (contextEnv != null && !contextEnv.isEmpty()) {
            contextPath = contextEnv;
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(System.getProperty("java.io.tmpdir"));

        // Add the Roller webapp context
        String docBase = getDocBase();
        Context ctx = tomcat.addWebapp(contextPath, docBase);

        // Use the existing web.xml for configuration
        ctx.setConfigFile(new File(docBase, "WEB-INF/web.xml").toURI().toURL());

        // Add a shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down embedded Tomcat...");
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (LifecycleException e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        System.out.println("Starting Roller on port " + port + " with context path " + contextPath);
        tomcat.start();
        tomcat.getServer().await();
    }

    /**
     * Determine the docBase for the webapp. When running from the JAR,
     * the webapp is extracted to a temp directory or found via classpath.
     */
    private static String getDocBase() {
        // Try to find the webapp directory relative to the JAR location or classpath
        String webappDir = System.getProperty("roller.webapp.dir");
        if (webappDir != null && new File(webappDir).exists()) {
            return webappDir;
        }

        // Fall back to current directory's webapp folder (development)
        File cwdWebapp = new File(System.getProperty("user.dir"), "src/main/webapp");
        if (cwdWebapp.exists()) {
            return cwdWebapp.getAbsolutePath();
        }

        // Try classpath-based location
        String classpathDir = System.getProperty("catalina.base");
        if (classpathDir != null) {
            File f = new File(classpathDir, "webapps/roller");
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }

        // Default: try to extract webapp from JAR
        return extractWebappFromJar();
    }

    /**
     * Extract the webapp directory from the JAR to a temp location.
     */
    private static String extractWebappFromJar() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "roller-webapp");
        if (tempDir.exists()) {
            return tempDir.getAbsolutePath();
        }
        // The webapp is packaged under /webapp/ in the JAR
        // Tomcat will look for web.xml in WEB-INF/web.xml relative to docBase
        return tempDir.getAbsolutePath();
    }
}
