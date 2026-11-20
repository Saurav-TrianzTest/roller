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

package org.apache.roller.weblogger.business.startup;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;

/**
 * Reads dbscripts from the classpath.
 */
public class ClasspathDatabaseScriptProvider implements DatabaseScriptProvider {

    /**
     * @see org.apache.roller.weblogger.business.startup.DatabaseScriptProvider#getDatabaseScript(java.lang.String)
     */
    @Override
    public InputStream getDatabaseScript(String path) {
        String resourcePath = "/dbscripts/" + path;
        InputStream is = this.getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            String scriptsDir = System.getenv("DB_SCRIPTS_DIR");
            if (scriptsDir != null) {
                try {
                    return new java.io.FileInputStream(Paths.get(scriptsDir, path).toFile());
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return is;
    }

    /**
     * Gets the script url.
     * 
     * @param path
     *            the path
     * 
     * @return the script url
     */
    public URL getScriptURL(String path) {
        String resourcePath = "/dbscripts/" + path;
        return ClasspathDatabaseScriptProvider.class.getResource(resourcePath);
    }

}
