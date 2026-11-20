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

package org.apache.roller.testing;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.derby.drda.NetworkServerControl;
import org.apache.roller.weblogger.business.startup.SQLScriptRunner;
import org.junit.jupiter.api.extension.*;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;


public class DerbyJunitExtension implements BeforeAllCallback, AfterAllCallback {

    private static Log log = LogFactory.getLog(DerbyJunitExtension.class);

    private static DerbyStartStopper derbyStartStopper =
        new DerbyStartStopper("target/derby-system", "target/dbscripts", "4224");

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        derbyStartStopper.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        try {
            derbyStartStopper.stop();
        } catch (Exception e) {
            log.warn("Error stopping Derby", e);
        }
    }
}


class DerbyStartStopper {
    private String databaseDir;
    private String databaseScriptsDir;
    private String port;


    DerbyStartStopper( String databaseDir, String databaseScriptsDir, String port ) {
        this.databaseDir = databaseDir;
        this.databaseScriptsDir = databaseScriptsDir;
        this.port = port;
    }

    public void start() throws Exception {

        System.out.println("==============");
        System.out.println("Starting Derby");
        System.out.println("==============");

        System.setProperty("derby.system.home", databaseDir);
        System.setProperty("derby.drda.portNumber", port);
        // Externalize Derby host to environment variable for container deployments
        String derbyHost = System.getenv().getOrDefault("DERBY_HOST", "localhost");
        System.setProperty("derby.drda.host", derbyHost);
        System.setProperty("derby.drda.maxThreads", "10");
        //System.setProperty("derby.drda.logConnections","true");

        NetworkServerControl server = new NetworkServerControl();
        server.start(new PrintWriter(System.out));
        try {
            Thread.sleep(2000);
        } catch (Exception ignored) {
        }
        System.out.println("Runtime Info: " + server.getRuntimeInfo());
        System.out.println("System Info:  " + server.getSysinfo());

        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String derbyUser = System.getenv().getOrDefault("DERBY_USER", "APP");
        String derbyPass = System.getenv().getOrDefault("DERBY_PASSWORD", "APP");
        // Externalize Derby database to environment variable for container deployments
        String derbyDbUrl = System.getenv().getOrDefault("DERBY_DB_URL", "jdbc:derby:rollerdb;create=true");
        Connection conn = DriverManager.getConnection(derbyDbUrl, derbyUser, derbyPass);

        // create roller tables

        SQLScriptRunner runner1 = new SQLScriptRunner(
            Paths.get(databaseScriptsDir, "droptables.sql").toString());
        runner1.runScript(conn, false);

        SQLScriptRunner runner = new SQLScriptRunner(
            Paths.get(databaseScriptsDir, "derby", "createdb.sql").toString());
        try {
            runner.runScript(conn, true);
        } catch (Exception ignored) {
            for (String message : runner.getMessages()) {
                System.out.println(message);
            }
            ignored.printStackTrace();
        }
    }

    public void stop() throws Exception {

        // Externalize Derby database host to environment variable for container deployments
        String derbyHost = System.getenv().getOrDefault("DERBY_HOST", "localhost");
        String driverURL = "jdbc:derby://" + derbyHost + ":" + port + "/rollerdb";
        String derbyUser = System.getenv().getOrDefault("DERBY_USER", "APP");
        String derbyPass = System.getenv().getOrDefault("DERBY_PASSWORD", "APP");

        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        Connection conn = DriverManager.getConnection(driverURL, derbyUser, derbyPass);

        // drop Roller tables
        SQLScriptRunner runner = new SQLScriptRunner(
            Paths.get(databaseScriptsDir, "droptables.sql").toString());
        runner.runScript(conn, false);

        System.out.println("==============");
        System.out.println("Stopping Derby");
        System.out.println("==============");

        try {
            DriverManager.getConnection(driverURL + ";shutdown=true");
        } catch (Exception ignored) {
        }

        System.setProperty("derby.system.home", databaseDir);

        // Network Derby
        System.setProperty("derby.drda.portNumber", port);
        System.setProperty("derby.drda.host", "localhost");
        System.setProperty("derby.drda.maxThreads", "10");
        //System.setProperty("derby.drda.logConnections","true");
        NetworkServerControl server = new NetworkServerControl();
        server.shutdown();

        try {
            while (true) {
               server.ping();
            }
        } catch (Exception expected) {}

        DriverManager.getConnection("jdbc:derby:;shutdown=true");

        try {Thread.sleep(2000);} catch (Exception ignored) {}
    }
}

