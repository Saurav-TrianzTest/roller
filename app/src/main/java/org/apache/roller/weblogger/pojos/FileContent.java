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

package org.apache.roller.weblogger.pojos;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;


/**
 * Represents the content of a file from file system or S3 object storage.
 *
 */
public class FileContent {
    
    // the physical java.io.File backing this resource
    private File resourceFile = null;
    
    // the input stream backing this resource (for S3)
    private InputStream resourceStream = null;
    
    // the length of the resource (for S3)
    private long length = -1;
    
    // the last modified time of the resource (for S3)
    private long lastModified = -1;
    
    // the file Id of the resource
    private String fileId = null;
    
    // the weblog the resource is attached to
    private Weblog weblog = null;
    
    
    public FileContent(Weblog weblog, String fileId, File file) {
        this.weblog = weblog;
        this.fileId = fileId;
        this.resourceFile = file;
    }
    
    public FileContent(Weblog weblog, String fileId, InputStream stream, long length, long lastModified) {
        this.weblog = weblog;
        this.fileId = fileId;
        this.resourceStream = stream;
        this.length = length;
        this.lastModified = lastModified;
    }
    
    public Weblog getWeblog() {
        return weblog;
    }
    
    public String getName() {
        if (resourceFile != null) {
            return resourceFile.getName();
        }
        return fileId;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public long getLastModified() {
        if (resourceFile != null) {
            return resourceFile.lastModified();
        }
        return lastModified;
    }
    
    public long getLength() {
        if (resourceFile != null) {
            return resourceFile.length();
        }
        return length;
    }
    
    /**
     * Returns the input stream for the underlying file.
     */
    public InputStream getInputStream() {
        if (resourceStream != null) {
            return resourceStream;
        }
        try {
            return new FileInputStream(resourceFile);
        } catch (java.io.FileNotFoundException ex) {
            // should never happen, rethrow as runtime exception
            throw new RuntimeException("Error constructing input stream", ex);
        }
    }
}
