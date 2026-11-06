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
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */
package org.apache.roller.weblogger.ui.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Replacement for the {@code MessageDigestPasswordEncoder} that was removed in
 * Spring Security 7.0. It preserves the same behaviour: the raw password bytes
 * are digested with the given algorithm (e.g. MD5 or SHA-1) and the result is
 * returned as a lower-case hexadecimal string.
 */
public class DigestPasswordEncoder implements PasswordEncoder {

    private final String algorithm;

    public DigestPasswordEncoder(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm " + algorithm + " not available", e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encode(rawPassword).equals(encodedPassword);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
