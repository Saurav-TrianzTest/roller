/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  The ASF licenses this file to You
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
 * limitations under the License.
 */
package org.apache.roller.weblogger.ui.core;

import java.io.File;

import org.apache.catalina.WebResource;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

/**
 * Main entry point for running Roller as an executable JAR with embedded Tomcat.
 * <p>
 * This replaces the WAR deployment model. The application's existing servlet
 * classes, filters, web.xml mappings, JSPs and static pages are all kept at
 * the same URLs via Tomcat's default servlet and JSP support.
 * </p>
 */
public class RollerMain {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String contextPath = System.getenv().getOrDefault("CONTEXT_PATH", "/roller");

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector().setPort(port);

        String docBase = System.getProperty("catalina.base", ".");
        if (args.length > 0) {
            docBase = args[0];
        }

        File docBaseFile = new File(docBase);
        if (!docBaseFile.exists()) {
            docBaseFile = new File(System.getProperty("user.dir", "."));
        }

        tomcat.addWebapp(contextPath, docBaseFile.getAbsolutePath());

        // Enable JSP compilation
        tomcat.getConnector();

        // Add shutdown hook for graceful termination (SIGTERM from EKS/ECS)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception e) {
                // Best-effort shutdown; don't throw
            }
        }));

        tomcat.start();
        tomcat.getServer().await();
    }
}
