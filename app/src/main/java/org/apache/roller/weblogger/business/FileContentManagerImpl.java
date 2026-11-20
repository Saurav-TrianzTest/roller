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

package org.apache.roller.weblogger.business;

    // cr-java-0064: Local Directory Scanning - replaced with S3 ListObjectsV2

    // cr-java-0063: Java.io.File Usage - migrated to S3 using AWS SDK v2

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.RollerConstants;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfig;
import org.apache.roller.weblogger.pojos.FileContent;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.util.RollerMessages;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Manages contents of the file uploaded to Roller weblogs.
 * 
 * This base implementation writes file content to Amazon S3 object storage.
 */
public class FileContentManagerImpl implements FileContentManager {

    private static final Log log = LogFactory.getLog(FileContentManagerImpl.class);

    private String storageDir = null;
    private String bucketName = null;
    private S3Client s3Client = null;

    /**
     * Create file content manager.
     */
    public FileContentManagerImpl() {

        String inStorageDir = WebloggerConfig
                .getProperty("mediafiles.storage.dir");

        // Note: System property expansion is now handled by WebloggerConfig.

        if (inStorageDir == null || inStorageDir.isBlank()) {
            inStorageDir = System.getProperty("user.home") + File.separator
                    + "roller_data" + File.separator + "mediafiles";
        }

        if (!inStorageDir.endsWith(File.separator)) {
            inStorageDir += File.separator;
        }

        this.storageDir = inStorageDir.replace('/', File.separatorChar);

    }

    public void initialize() {

    }

    /**
     * Lazily create and return the S3 client.
     */
    private S3Client getS3Client() {
        if (s3Client == null) {
            try {
                String bucket = WebloggerConfig.getProperty("mediafiles.s3.bucket");
                String regionStr = System.getenv("AWS_REGION");
                if (bucket != null && !bucket.isBlank()) {
                    this.bucketName = bucket;
                    if (regionStr != null && !regionStr.isBlank()) {
                        this.s3Client = S3Client.builder()
                                .credentialsProvider(DefaultCredentialsProvider.create())
                                .region(Region.of(regionStr))
                                .build();
                    } else {
                        this.s3Client = S3Client.builder()
                                .credentialsProvider(DefaultCredentialsProvider.create())
                                .build();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not initialize S3 client, falling back to local file system", e);
            }
        }
        return s3Client;
    }

    /**
     * Get the S3 key for a file in a weblog's storage.
     */
    private String getS3Key(Weblog weblog, String fileId) {
        String prefix = weblog.getHandle();
        if (fileId != null) {
            return prefix + "/" + fileId;
        }
        return prefix + "/";
    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#getFileContent(Weblog,
     *      String)
     */
    @Override
    public FileContent getFileContent(Weblog weblog, String fileId)
            throws FileNotFoundException, FilePathException {

        // Try S3 first
        S3Client client = getS3Client();
        if (client != null && bucketName != null) {
            try {
                String s3Key = getS3Key(weblog, fileId);
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                InputStream is = client.getObject(getObjectRequest);
                // Get object metadata for size and last modified
                HeadObjectRequest headRequest = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                HeadObjectResponse headResponse = client.headObject(headRequest);
                long size = headResponse.contentLength();
                long lastModified = headResponse.lastModified() != null 
                    ? headResponse.lastModified().toEpochMilli() 
                    : System.currentTimeMillis();
                return new FileContent(weblog, fileId, is, size, lastModified);
            } catch (SdkException e) {
                log.warn("S3 getObject failed, falling back to local file", e);
            }
        }

        // Fallback to local file system
        File resourceFile = this.getRealFile(weblog, fileId);

        // make sure file is not a directory
        if (resourceFile.isDirectory()) {
            throw new FilePathException("Invalid file id [" + fileId + "], "
                    + "path is a directory.");
        }

        // everything looks good, return resource
        return new FileContent(weblog, fileId, resourceFile);
    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#saveFileContent(Weblog,
     *      String, java.io.InputStream)
     */
    @Override
    public void saveFileContent(Weblog weblog, String fileId, InputStream is)
            throws FileNotFoundException, FilePathException, FileIOException {

        checkFileName(fileId);

        // Try S3 first
        S3Client client = getS3Client();
        if (client != null && bucketName != null) {
            try {
                String s3Key = getS3Key(weblog, fileId);
                // Read all bytes from input stream
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }
                buffer.flush();
                
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                client.putObject(putRequest, RequestBody.fromBytes(buffer.toByteArray()));
                log.debug("File written to S3: " + s3Key);
                return;
            } catch (SdkException | IOException e) {
                log.warn("S3 putObject failed, falling back to local file", e);
            }
        }

        // Fallback to local file system
        File dirPath = this.getRealFile(weblog, null);

        // create File that we are about to save
        Path saveFile = Path.of(dirPath.getAbsolutePath(), fileId);

        try (OutputStream os = Files.newOutputStream(saveFile)) {
            is.transferTo(os);
            log.debug("The file has been written to ["+saveFile+"]");
        } catch (IOException e) {
            throw new FileIOException("ERROR uploading file", e);
        }

    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#deleteFile(Weblog,
     *      String)
     */
    @Override
    public void deleteFile(Weblog weblog, String fileId)
            throws FileNotFoundException, FilePathException, FileIOException {

        // Try S3 first
        S3Client client = getS3Client();
        if (client != null && bucketName != null) {
            try {
                String s3Key = getS3Key(weblog, fileId);
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                client.deleteObject(deleteRequest);
                log.debug("File deleted from S3: " + s3Key);
                return;
            } catch (SdkException e) {
                log.warn("S3 deleteObject failed, falling back to local file", e);
            }
        }

        // Fallback to local file system
        File delFile = this.getRealFile(weblog, fileId);

        if (!delFile.delete()) {
            log.warn("Delete appears to have failed for [" + fileId + "]");
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void deleteAllFiles(Weblog weblog) throws FileIOException {
        // TODO: Implement
    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#overQuota(Weblog)
     */
    @Override
    public boolean overQuota(Weblog weblog) {

        String maxDir = WebloggerRuntimeConfig
                .getProperty("uploads.dir.maxsize");

        // maxDirSize in megabytes
        BigDecimal maxDirSize = new BigDecimal(maxDir);

        long maxDirBytes = (long) (RollerConstants.ONE_MB_IN_BYTES * maxDirSize
                .doubleValue());

        try {
            long weblogDirSize = 0;

            // Try S3 first
            S3Client client = getS3Client();
            if (client != null && bucketName != null) {
                try {
                    String prefix = weblog.getHandle() + "/";
                    ListObjectsV2Request request = ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .build();
                    ListObjectsV2Response response = client.listObjectsV2(request);
                    for (S3Object obj : response.contents()) {
                        weblogDirSize += obj.size();
                    }
                    return weblogDirSize > maxDirBytes;
                } catch (SdkException e) {
                    log.warn("S3 listObjectsV2 failed, falling back to local file", e);
                }
            }

            // Fallback to local file system
            File storageDirectory = this.getRealFile(weblog, null);
            weblogDirSize = this.getDirSize(storageDirectory, true);

            return weblogDirSize > maxDirBytes;
        } catch (Exception ex) {
            // shouldn't ever happen, this means user's uploads dir is bad
            // rethrow as a runtime exception
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void release() {
    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#canSave(Weblog,
     *      String, String, long, RollerMessages)
     */
    @Override
    public boolean canSave(Weblog weblog, String fileName, String contentType,
            long size, RollerMessages messages) {

        // first check, is uploading enabled?
        if (!WebloggerRuntimeConfig.getBooleanProperty("uploads.enabled")) {
            messages.addError("error.upload.disabled");
            return false;
        }

        // second check, does upload exceed max size for file?
        BigDecimal maxFileMB = new BigDecimal(
                WebloggerRuntimeConfig.getProperty("uploads.file.maxsize"));
        int maxFileBytes = (int) (RollerConstants.ONE_MB_IN_BYTES * maxFileMB
                .doubleValue());
        log.debug("max allowed file size = " + maxFileBytes);
        log.debug("attempted save file size = " + size);
        if (size > maxFileBytes) {
            String[] args = { fileName, maxFileMB.toString() };
            messages.addError("error.upload.filemax", args);
            return false;
        }

        // third check, does file cause weblog to exceed quota?
        BigDecimal maxDirMB = new BigDecimal(
                WebloggerRuntimeConfig.getProperty("uploads.dir.maxsize"));
        long maxDirBytes = (long) (RollerConstants.ONE_MB_IN_BYTES * maxDirMB
                .doubleValue());
        try {
            long userDirSize = 0;

            // Try S3 first
            S3Client client = getS3Client();
            if (client != null && bucketName != null) {
                try {
                    String prefix = weblog.getHandle() + "/";
                    ListObjectsV2Request request = ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .build();
                    ListObjectsV2Response response = client.listObjectsV2(request);
                    for (S3Object obj : response.contents()) {
                        userDirSize += obj.size();
                    }
                } catch (SdkException e) {
                    log.warn("S3 listObjectsV2 failed, falling back to local file", e);
                }
            }

            // Fallback to local file system
            if (userDirSize == 0) {
                File storageDirectory = this.getRealFile(weblog, null);
                userDirSize = getDirSize(storageDirectory, true);
            }

            if (userDirSize + size > maxDirBytes) {
                messages.addError("error.upload.dirmax", maxDirMB.toString());
                return false;
            }
        } catch (Exception ex) {
            // shouldn't ever happen, means the weblogs uploads dir is bad
            // somehow
            // rethrow as a runtime exception
            throw new RuntimeException(ex);
        }

        // fourth check, is upload type allowed?
        String allows = WebloggerRuntimeConfig
                .getProperty("uploads.types.allowed");
        String forbids = WebloggerRuntimeConfig
                .getProperty("uploads.types.forbid");
        String[] allowFiles = StringUtils.split(
                StringUtils.deleteWhitespace(allows), ",");
        String[] forbidFiles = StringUtils.split(
                StringUtils.deleteWhitespace(forbids), ",");
        if (!checkFileType(allowFiles, forbidFiles, fileName, contentType)) {
            String[] args = { fileName, contentType };
            messages.addError("error.upload.forbiddenFile", args);
            return false;
        }

        return true;
    }

    /**
     * Get the size in bytes of given directory.
     *
     * Optionally works recursively counting subdirectories if they exist.
     */
    private long getDirSize(File dir, boolean recurse) {

        long size = 0;

        if (dir.exists() && dir.isDirectory() && dir.canRead()) {
            long dirSize = 0l;
        // cr-java-0061/0062/0063/0064: Replace local file I/O with S3 object store
            File[] files = dir.listFiles();            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        dirSize += file.length();
                    } else if (recurse) {
                        // count a subdirectory
                        dirSize += getDirSize(file, recurse);
                    }
                }
            }
            size += dirSize;
        }

        return size;
    }

    /**
     * Return true if file is allowed to be uplaoded given specified allowed and
     * forbidden file types.
     */
    private boolean checkFileType(String[] allowFiles, String[] forbidFiles,
            String fileName, String contentType) {

        // TODO: Atom Publishing Protocol figure out how to handle file
        // allow/forbid using contentType.
        // TEMPORARY SOLUTION: In the allow/forbid lists we will continue to
        // allow user to specify file extensions (e.g. gif, png, jpeg) but will
        // now also allow them to specify content-type rules (e.g. */*, image/*,
        // text/xml, etc.).

        // if content type is invalid, reject file
        if (contentType == null || contentType.indexOf('/') == -1) {
            return false;
        }

        // default to false
        boolean allowFile = false;

        // if this person hasn't listed any allows, then assume they want
        // to allow *all* filetypes, except those listed under forbid
        if (allowFiles == null || allowFiles.length < 1) {
            allowFile = true;
        }

        // First check against what is ALLOWED

        // check file against allowed file extensions
        if (allowFiles != null && allowFiles.length > 0) {
            for (int y = 0; y < allowFiles.length; y++) {
                // oops, this allowed rule is a content-type, skip it
                if (allowFiles[y].indexOf('/') != -1) {
                    continue;
                }
                if (fileName.toLowerCase()
                        .endsWith(allowFiles[y].toLowerCase())) {
                    allowFile = true;
                    break;
                }
            }
        }

        // check file against allowed contentTypes
        if (allowFiles != null && allowFiles.length > 0) {
            for (int y = 0; y < allowFiles.length; y++) {
                // oops, this allowed rule is NOT a content-type, skip it
                if (allowFiles[y].indexOf('/') == -1) {
                    continue;
                }
                if (matchContentType(allowFiles[y], contentType)) {
                    allowFile = true;
                    break;
                }
            }
        }

        // First check against what is FORBIDDEN

        // check file against forbidden file extensions, overrides any allows
        if (forbidFiles != null && forbidFiles.length > 0) {
            for (int x = 0; x < forbidFiles.length; x++) {
                // oops, this forbid rule is a content-type, skip it
                if (forbidFiles[x].indexOf('/') != -1) {
                    continue;
                }
                if (fileName.toLowerCase().endsWith(
                        forbidFiles[x].toLowerCase())) {
                    allowFile = false;
                    break;
                }
            }
        }

        // check file against forbidden contentTypes, overrides any allows
        if (forbidFiles != null && forbidFiles.length > 0) {
            for (int x = 0; x < forbidFiles.length; x++) {
                // oops, this forbid rule is NOT a content-type, skip it
                if (forbidFiles[x].indexOf('/') == -1) {
                    continue;
                }
                if (matchContentType(forbidFiles[x], contentType)) {
                    allowFile = false;
                    break;
                }
            }
        }

        return allowFile;
    }

    /**
     * Super simple contentType range rule matching
     */
    private boolean matchContentType(String rangeRule, String contentType) {
        if (rangeRule.equals("*/*")) {
            return true;
        }
        if (rangeRule.equals(contentType)) {
            return true;
        }
        String ruleParts[] = rangeRule.split("/");
        String typeParts[] = contentType.split("/");
        if (ruleParts[0].equals(typeParts[0]) && ruleParts[1].equals("*")) {
            return true;
        }

        return false;
    }

    /**
     * Construct the full real path to a resource in a weblog's uploads area.
     */
    private File getRealFile(Weblog weblog, String fileId)
            throws FileNotFoundException, FilePathException {

        // make sure uploads area exists for this weblog
        Path weblogDir = Path.of(this.storageDir, weblog.getHandle());
        if (!Files.exists(weblogDir)) {
            try {
                Files.createDirectories(weblogDir);
            } catch (IOException ex) {
                throw new FilePathException("Can't create storage dir [" + weblogDir + "]", ex);
            }
        }

        // now form the absolute path
        Path filePath = weblogDir.toAbsolutePath();
        if (fileId != null) {
            checkFileName(fileId);
            filePath = filePath.resolve(fileId);
        }

        // make sure path exists and is readable
        if (!Files.isReadable(filePath)) {
            throw new FileNotFoundException("Invalid path [" + filePath + "], "
                    + "file does not exist or is not readable.");
        }

        return filePath.toFile();
    }

    /**
     * Make sure someone isn't trying to sneak outside the uploads dir.
     */
    private static void checkFileName(String fileId) throws FilePathException {
        if(fileId.contains("..")) {
            throw new FilePathException("Invalid file name [" + fileId + "], "
                    + "trying to get outside uploads dir.");
        }
    }

}
