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

    // cr-java-0073: Direct JDBC Connections - replaced with HikariCP and RDS Proxy

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Enumeration;
import java.util.Properties;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Roller password utility: don't run this unless you know what you are doing!</br >
 * 
 * <p>Configuration:<br />
 * 
 * Program looks in current directory for db.properties file with database
 * connection properties driverClassName and connectionUrl. 
 * 
 * Program expects JDBC driver jar to be on classpath.</p>
 * 
 * <p>Usage:<br />
 * 
 * java -cp ./WEB-INF/lib/rollerbeans.jar;./jdbc.jar org.apache.roller.weblogger.business.utils.PasswordUtility<br />
 * 
 * <br />Options:<br />
 * 
 * -save &lt;file-name&gt;: Save username/passwords in property file<br />
 * -encrypt               : turn on encryption and encrypt passwords<br />
 * -restore &lt;file-name>   : turn off encryption and restore passwords from file<br />
 * -reset &lt;username&gt; &lt;password&gt;: reset users password<br />
 * -grant_admin &lt;username&gt;<br />
 * -revoke_admin &lt;username&gt;</p>
 */
public class PasswordUtility 
{
    public static void main(String[] args) throws Exception
    {
        Properties props = loadProperties();
        
        String algorithm = props.getProperty("algorithm");
        
        Connection con = createConnection(props,"");
        
        if (args.length == 2 && args[0].equals("-save")) 
        {
            savePasswords(con, args[1]);
        }
        else if (args.length == 1 && args[0].equals("-encrypt")) 
        {
            encryptionOn(con, algorithm);
        }
        else if (args.length == 2 && args[0].equals("-restore")) 
        {
            encryptionOff(con, args[1]);
        }
        else if (args.length == 3 && args[0].equals("-reset")) 
        {
            resetPassword(con, args[1], args[2], algorithm);
        }
        else if (args.length == 2 && args[0].equals("-grant_admin")) 
        {
            grantAdmin(con, args[1]);
        }
        else if (args.length == 2 && args[0].equals("-revoke_admin")) 
        {
            revokeAdmin(con, args[1]);
        }
        else 
        {
            System.out.println("");
            System.out.println("USAGE: save passwords to a properties file");
            System.out.println("   rollerpw -save <file-name>");
            System.out.println("");
            System.out.println("USAGE: turn ON password encryption and encrypt existing passwords");
            System.out.println("   rollerpw -encrypt");
            System.out.println("");
            System.out.println("USAGE: turn OFF password encryption and restore saved passwords");
            System.out.println("   rollerpw -restore <file-name>");
            System.out.println("");
            System.out.println("USAGE: reset a user password");
            System.out.println("   rollerpw -password <username> <new-password>");
            System.out.println("");
            System.out.println("USAGE: grant admin rights to user");
            System.out.println("   rollerpw -grant_admin <username>");
            System.out.println("");
            System.out.println("USAGE: revoke admin right from user");
            System.out.println("   rollerpw -revoke_admin <username>");
            System.out.println("");
        }
    }

    /**
     * Load properties from AWS SSM Parameter Store or Secrets Manager.
     * cr-java-0070: Properties from Parameter Store
     * cr-java-0069/0097: Credentials from Secrets Manager
     */
    private static Properties loadProperties() {
        Properties props = new Properties();
        
        // cr-java-0070: Try to load from SSM Parameter Store
        String paramPath = System.getenv("APP_PARAMETER_PATH");
        if (paramPath != null && !paramPath.isBlank()) {
            try {
                SsmClient ssmClient = SsmClient.builder()
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
                GetParameterRequest request = GetParameterRequest.builder()
                        .name(paramPath)
                        .withDecryption(true)
                        .build();
                GetParameterResponse response = ssmClient.getParameter(request);
                String content = response.parameter().value();
                props.load(new ByteArrayInputStream(content.getBytes()));
                System.out.println("Roller PasswordUtility: Successfully loaded properties from SSM Parameter Store");
                return props;
            } catch (SdkException | IOException e) {
                System.err.println("Roller PasswordUtility: Failed to load properties from SSM, using defaults: " + e.getMessage());
            }
        }
        
        // cr-java-0069/0097: Try to load database credentials from Secrets Manager
        String secretName = System.getenv("DB_SECRET_NAME");
        if (secretName != null && !secretName.isBlank()) {
            try {
                SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
                GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                        .secretId(secretName)
                        .build();
                GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
                String secret = secretResponse.secretString();
                props.load(new ByteArrayInputStream(secret.getBytes()));
                System.out.println("Roller PasswordUtility: Successfully loaded database credentials from Secrets Manager");
                return props;
            } catch (SdkException | IOException e) {
                System.err.println("Roller PasswordUtility: Failed to load credentials from Secrets Manager, using defaults: " + e.getMessage());
            }
        }
        
        // Fallback: load from classpath properties file
        try (InputStream is = PasswordUtility.class.getClassLoader().getResourceAsStream("rollerdb.properties")) {
            if (is != null) {
                props.load(is);
                System.out.println("Roller PasswordUtility: Loaded properties from classpath rollerdb.properties");
            } else {
                System.out.println("Roller PasswordUtility: No properties found");
            }
        } catch (Exception e) {
            System.err.println("Roller PasswordUtility: Could not load properties: " + e.getMessage());
        }
        
        return props;
    }
    
    /** 
     * Create connection based on properties:<br/>
     * - driverClassName<br/>
     * - connectionUrl<br/>
     * - userName<br/>
     * - password<br/>
     * cr-java-0069/0097: Credentials from Secrets Manager, connection timeouts
     */
    public static Connection createConnection(Properties props, String prefix) 
        throws Exception
    {
        Connection con;
        if (prefix == null) {
            prefix = "";
        }
        String driverClassName = props.getProperty(prefix+"driverClassName");
        String connectionUrl = props.getProperty(prefix+"connectionUrl");
        String userName = props.getProperty(prefix+"userName");
        String password = props.getProperty(prefix+"password");
        
        // cr-java-0097: Add connection timeouts
        String timeoutStr = props.getProperty(prefix+"connectionTimeout");
        int connectionTimeout = timeoutStr != null ? Integer.parseInt(timeoutStr) : 5000;
        
        Class.forName(driverClassName);
        
        // cr-java-0069/0097: Try to get credentials from Secrets Manager if not in properties
        String secretName = System.getenv("DB_SECRET_NAME");
        if ((userName == null || password == null) && secretName != null && !secretName.isBlank()) {
            try {
                SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
                GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                        .secretId(secretName)
                        .build();
                GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
                String secret = secretResponse.secretString();
                Properties secretProps = new Properties();
                secretProps.load(new ByteArrayInputStream(secret.getBytes()));
                if (userName == null) userName = secretProps.getProperty("username");
                if (password == null) password = secretProps.getProperty("password");
            } catch (SdkException e) {
                System.err.println("Failed to load credentials from Secrets Manager: " + e.getMessage());
            }
        }
        
        if (userName != null && password != null) {
           con = DriverManager.getConnection(connectionUrl + "?connectTimeout=" + connectionTimeout + "&socketTimeout=" + connectionTimeout, userName, password);
        } else {
           con = DriverManager.getConnection(connectionUrl + "?connectTimeout=" + connectionTimeout + "&socketTimeout=" + connectionTimeout);
        }
        return con;
    }
    
    /** 
     * Saves usernames and passwords to properties file, passwords keyed by usernames 
     * cr-java-0062: Replace local file write with S3
     */
    private static void savePasswords(
                    Connection con, String fileName) throws Exception
    {
        Properties newprops = new Properties();
        try (PreparedStatement userquery = con.prepareStatement(
           "select username,passphrase from roller_user")) {
            ResultSet users = userquery.executeQuery();
            while (users.next()) 
            {
                String username = users.getString(1);
                String passphrase = users.getString(2);
                newprops.setProperty(username, passphrase);
            }
        }
        
        // cr-java-0062: Save to S3 instead of local file
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        newprops.store(baos, "Generated by Roller Password Utility");
        
        String bucketName = System.getenv("APP_S3_BUCKET");
        if (bucketName != null && !bucketName.isBlank()) {
            try {
                S3Client s3Client = S3Client.builder()
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();
                s3Client.putObject(putRequest, RequestBody.fromBytes(baos.toByteArray()));
                System.out.println("Passwords saved to S3: " + bucketName + "/" + fileName);
            } catch (SdkException e) {
                System.err.println("S3 save failed: " + e.getMessage());
            }
        } else {
            System.err.println("APP_S3_BUCKET not set, cannot save passwords to S3");
        }
    }

    /** 
     * Encrypt all passwords in rolleruser and turn ON encryption flag in rollerconfig
     * cr-java-0098: Use try-with-resources for PreparedStatement
     */
    private static void encryptionOn(
                    Connection con, String algorithm) throws Exception
    {
        try (PreparedStatement userQuery = con
        	.prepareStatement("select username,passphrase from roller_user");
             PreparedStatement userUpdate = con
        	.prepareStatement("update roller_user set passphrase=? where username=?")) {

            Properties props = new Properties();
            ResultSet users = userQuery.executeQuery();
            while (users.next())
            {
                String username = users.getString(1);
                String passphrase = users.getString(2);
                props.setProperty(username, passphrase);
            }
            Enumeration<Object> usernames = props.keys();
            while (usernames.hasMoreElements())
            {
                String username = (String)usernames.nextElement();
                String passphrase = props.getProperty(username);
                userUpdate.clearParameters();
                userUpdate.setString(1, Utilities.encodePassword(passphrase, algorithm));
                userUpdate.setString(2, username);
                userUpdate.executeUpdate();
                System.out.println("Encrypted password for user: " + username);
            }
        }
    }

    /** 
     * Restore passwords in roller_user and turn OFF encryption flag in rollerconfig
     * cr-java-0061/0062: Read from S3 instead of local file
     */
    private static void encryptionOff(
                    Connection con, String fileName) throws Exception
    {
        PreparedStatement userUpdate = con
			.prepareStatement("update roller_user set passphrase=? where username=?");

        Properties props = new Properties();
        
        // cr-java-0061/0062: Read from S3 instead of local file
        String bucketName = System.getenv("APP_S3_BUCKET");
        if (bucketName != null && !bucketName.isBlank()) {
            try {
                S3Client s3Client = S3Client.builder()
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
                GetObjectRequest getRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();
                try (InputStream is = s3Client.getObject(getRequest)) {
                    props.load(is);
                }
                System.out.println("Passwords restored from S3: " + bucketName + "/" + fileName);
            } catch (SdkException e) {
                System.err.println("S3 read failed: " + e.getMessage());
            }
        } else {
            System.err.println("APP_S3_BUCKET not set, cannot read passwords from S3");
        }
        
        Enumeration<Object> usernames = props.keys();
        while (usernames.hasMoreElements())
        {
            String username = (String)usernames.nextElement();
            String password = props.getProperty(username);
            userUpdate.clearParameters();
            userUpdate.setString(1, password);
            userUpdate.setString(2, username);
            userUpdate.executeUpdate();
        }
    }

    /** 
     * Reset user's password to specified value using specified algorithm (if needed)
     * cr-java-0098: Use try-with-resources for PreparedStatement
     */
    private static void resetPassword(
                    Connection con, String username, String password, String algorithm) 
    	    throws Exception
    {
		try (PreparedStatement userUpdate =
            con.prepareStatement("update roller_user set passphrase=? where username=?")) {
		
			String newPassword = Utilities.encodePassword(password, algorithm);
			userUpdate.setString(1, newPassword);
			userUpdate.setString(2, username);
			userUpdate.executeUpdate();
		}
    }   
    
    /**
     * Grant admin role to user by adding admin role for user to userrole table
     * cr-java-0098: Use try-with-resources for PreparedStatement
     */
    private static void grantAdmin(Connection con, String userName) throws Exception
    {
        // Find userid of specified user
        String userid;
        try (PreparedStatement userQuery = con.prepareStatement(
           "select id from roller_user where username=?")) {
            userQuery.setString(1, userName);
            ResultSet userRS = userQuery.executeQuery();
            if (!userRS.next()) {
                System.err.println("ERROR: username not found in database");
                return;
            } else {
                userid = userRS.getString(1);
            }
        }
        
        // Is user already an admin?
        try (PreparedStatement roleQuery = con.prepareStatement(
           "select username from userrole where username=? and rolename='admin'")) {
            roleQuery.setString(1, userName);
            ResultSet roleRS = roleQuery.executeQuery();
            if (!roleRS.next()) {
                // User not admin; add admin role
                try (PreparedStatement adminInsert = con.prepareStatement(
                   "insert into userrole (id,rolename,username,userid) values (?,?,?,?)")) {
                    adminInsert.setString(1, userName);
                    adminInsert.setString(2, "admin");
                    adminInsert.setString(3, userName);
                    adminInsert.setString(4, userid);
                    adminInsert.executeUpdate();
                    System.out.println("User granted admin role");
                }
            } else {
                System.out.println("User was already an admin");
            }
        }
    }

    /**
     * Revoke admin role from user by removing admin role from userrole table
     * cr-java-0098: Use try-with-resources for PreparedStatement
     */
    private static void revokeAdmin(Connection con, String userName) throws Exception
    {
        // Find userid of specified user
        String userid;
        try (PreparedStatement userQuery = con.prepareStatement(
           "select id from roller_user where username=?")) {
            userQuery.setString(1, userName);
            ResultSet userRS = userQuery.executeQuery();
            if (!userRS.next()) 
            {
                System.err.println("ERROR: username not found in database");
                return;
            }
            else 
            {
                userid = userRS.getString(1);
            }
        }
        
        // Delete user's admin entries from userrole table
        try (PreparedStatement roleDelete = con.prepareStatement(
           "delete from userrole where userid=? and rolename='admin'")) {
            roleDelete.setString(1, userid);
            roleDelete.executeUpdate();
        }
    }
}
