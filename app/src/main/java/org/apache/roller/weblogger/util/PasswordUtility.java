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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Enumeration;
import java.util.Properties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
        Properties props = new Properties();
        // Read DB config from environment variables (cr-java-0070)
        String driverClassName = System.getenv("DB_DRIVER");
        String connectionUrl = System.getenv("DB_URL");
        String userName = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        String algorithm = System.getenv("DB_ALGORITHM");
        
        Connection con = createConnection(driverClassName, connectionUrl, userName, password);
        
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
     * Create connection based on properties:<br/>
     * - driverClassName<br/>
     * - connectionUrl<br/>
     * - userName<br/>
     * - password<br/>
     */
    public static Connection createConnection(String driverClassName, String connectionUrl, 
            String userName, String password) 
        throws Exception
    {
        Connection con;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionUrl);
        if (userName != null) {
            config.setUsername(userName);
        }
        if (password != null) {
            config.setPassword(password);
        }
        // cr-java-0097: Add connection and socket timeouts (5 seconds each = 5000ms)
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(5000);
        config.setMaxLifetime(300000);
        config.setMaximumPoolSize(5);
        config.setInitializationFailTimeout(-1);
        if (driverClassName != null) {
            config.setDriverClassName(driverClassName);
        }
        HikariDataSource dataSource = new HikariDataSource(config);
        con = dataSource.getConnection();
        return con;
    }
    
    /** 
     * Saves usernames and passwords to properties file, passwords keyed by usernames 
     */
    private static void savePasswords(
                    Connection con, String fileName) throws Exception
    {
        Properties newprops = new Properties();
        PreparedStatement userquery = null;
        ResultSet users = null;
        try {
            userquery = con.prepareStatement(
               "select username,passphrase from roller_user");
            users = userquery.executeQuery();
            while (users.next()) 
            {
                String username = users.getString(1);
                String passphrase = users.getString(2);
                newprops.setProperty(username, passphrase);
            }
        } finally {
            if (users != null) users.close();
            if (userquery != null) userquery.close();
        }
        // cr-java-0062: Replace local file write with S3 upload
        byte[] data = newprops.toString().getBytes();
        S3Client s3Client = S3Client.create();
        String bucketName = System.getenv("APP_S3_BUCKET");
        s3Client.putObject(PutObjectRequest.builder()
            .bucket(bucketName)
            .key("passwords/" + fileName)
            .contentType("text/plain")
            .build(), RequestBody.fromBytes(data));
    }

    /** 
     * Encrypt all passwords in rolleruser and turn ON encryption flag in rollerconfig
     */
    private static void encryptionOn(
                    Connection con, String algorithm) throws Exception
    {
        PreparedStatement userQuery = con
        	.prepareStatement("select username,passphrase from roller_user");
        PreparedStatement userUpdate = con
        	.prepareStatement("update roller_user set passphrase=? where username=?");

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

    /** 
     * Restore passwords in roller_user and turn OFF encryption flag in rollerconfig
     */
    private static void encryptionOff(
                    Connection con, String fileName) throws Exception
    {
        PreparedStatement userUpdate = con
			.prepareStatement("update roller_user set passphrase=? where username=?");

        Properties props = new Properties();
        // cr-java-0061/0063: Replace local file read with S3 GetObject
        S3Client s3Client = S3Client.create();
        String bucketName = System.getenv("APP_S3_BUCKET");
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key("passwords/" + fileName)
                .build();
            ResponseInputStream<?> s3Object = s3Client.getObject(getRequest);
            props.load(s3Object);
        } catch (SdkException e) {
            // Keep old failure path: if file/remote object is missing, proceed with empty props
            System.err.println("WARNING: Could not load passwords from S3: " + fileName);
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
     */
    private static void resetPassword(
                    Connection con, String username, String password, String algorithm) 
    	    throws Exception
    {
		PreparedStatement userUpdate =
            con.prepareStatement("update roller_user set passphrase=? where username=?");
		
		String newPassword = Utilities.encodePassword(password, algorithm);
		userUpdate.setString(1, newPassword);
		userUpdate.setString(2, username);
		userUpdate.executeUpdate();
    }   
    
    /**
     * Grant admin role to user by adding admin role for user to userrole table
     */
    private static void grantAdmin(Connection con, String userName) throws Exception
    {
        // Find userid of specified user
        String userid;
        PreparedStatement userQuery = con.prepareStatement(
           "select id from roller_user where username=?");
        userQuery.setString(1, userName);
        ResultSet userRS = userQuery.executeQuery();
        if (!userRS.next()) {
            System.err.println("ERROR: username not found in database");
            return;
        } else {
            userid = userRS.getString(1);
        }
        
        // Is user already an admin?
        PreparedStatement roleQuery = con.prepareStatement(
           "select username from userrole where username=? and rolename='admin'");
        roleQuery.setString(1, userName);
        ResultSet roleRS = roleQuery.executeQuery();
        if (!roleRS.next()) {
            // User not admin; add admin role
            PreparedStatement adminInsert = con.prepareStatement(
               "insert into userrole (id,rolename,username,userid) values (?,?,?,?)");
            adminInsert.setString(1, userName);
            adminInsert.setString(2, "admin");
            adminInsert.setString(3, userName);
            adminInsert.setString(4, userid);
            adminInsert.executeUpdate();
            System.out.println("User granted admin role");
        } else {
            System.out.println("User was already an admin");
        }
    }

    /**
     * Revoke admin role from user by removing admin role from userrole table
     */
    private static void revokeAdmin(Connection con, String userName) throws Exception
    {
        // Find userid of specified user
        String userid;
        PreparedStatement userQuery = con.prepareStatement(
           "select id from roller_user where username=?");
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
        
        // Delete user's admin entries from userrole table
        PreparedStatement roleDelete = con.prepareStatement(
           "delete from userrole where userid=? and rolename='admin'");
        roleDelete.setString(1, userid);
        roleDelete.executeUpdate();
    }
}
