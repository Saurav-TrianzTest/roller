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

package org.apache.roller.weblogger.business.themes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.pojos.ThemeResource;


/**
 * A FileManagerImpl specific implementation of a ThemeResource.
 *
 * ThemeResources from the FileManagerImpl are backed by a java.io.File
 * object which represents the resource on a filesystem.
 *
 * This class is internal to the FileManagerImpl class because there should
 * not be any external classes which need to construct their own instances
 * of this class.
 */
public class SharedThemeResourceFromDir 
        implements ThemeResource, Serializable {
    
    private static final Log log = LogFactory.getLog(SharedThemeResourceFromDir.class);
    
    // the physical java.io.File backing this resource
    private File resourceFile = null;
    
    // bytes backing this resource (for S3/cloud storage)
    private byte[] resourceBytes = null;
    
    // the relative path of the resource within the theme
    private String relativePath = null;
    
    // last modified time for byte-backed resources
    private long lastModified = 0;
    
    
    public SharedThemeResourceFromDir(String path, File file) {
        relativePath = path;
        resourceFile = file;
        if (file != null) {
            this.lastModified = file.lastModified();
        }
    }
    
    // cr-java-0061/0063: Constructor for S3/cloud-backed resources
    public SharedThemeResourceFromDir(String path, byte[] bytes) {
        relativePath = path;
        resourceBytes = bytes;
        this.lastModified = System.currentTimeMillis();
    }
    
    
    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(ThemeResource other) {
        return getPath().compareTo(other.getPath());
    }
    
    
    public ThemeResource[] getChildren() {
        return null;
    }
    
    
    @Override
    public String getName() {
        if (resourceFile != null) {
            return resourceFile.getName();
        }
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;
    }
    
    @Override
    public String getPath() {
        return relativePath;
    }
    
    @Override
    public long getLastModified() {
        if (resourceFile != null) {
            return resourceFile.lastModified();
        }
        return lastModified;
    }
    
    @Override
    public long getLength() {
        if (resourceFile != null) {
            return resourceFile.length();
        }
        return resourceBytes != null ? resourceBytes.length : 0;
    }
    
    @Override
    public boolean isDirectory() {
        if (resourceFile != null) {
            return resourceFile.isDirectory();
        }
        return false;
    }
    
    public boolean isFile() {
        if (resourceFile != null) {
            return resourceFile.isFile();
        }
        return resourceBytes != null;
    }
    
    @Override
    public InputStream getInputStream() {
        try {
            if (resourceFile != null) {
                return new FileInputStream(resourceFile);
            }
            if (resourceBytes != null) {
                return new ByteArrayInputStream(resourceBytes);
            }
            throw new java.io.FileNotFoundException("No resource data for " + relativePath);
        } catch (Exception e) {
            log.warn("Unable to get input stream for " + relativePath, e);
            return null;
        }
    }
    
}
