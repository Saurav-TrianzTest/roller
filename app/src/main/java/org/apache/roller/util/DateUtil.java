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

package org.apache.roller.util;

import java.text.ParseException;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;


/**
 * General purpose date utilities.
 *
 * TODO: all date handling functions need to be aware of locale and timezone.
 */
public abstract class DateUtil {
    
    public static final long MILLIS_IN_DAY = 86400000;
    
    // a bunch of date formats
    private static final String FORMAT_DEFAULT_DATE = "dd.MM.yyyy";
    private static final String FORMAT_DEFAULT_DATE_MINIMAL = "d.M.yy";
    private static final String FORMAT_DEFAULT_TIMESTAMP = "yyyy-MM-dd HH:mm:ss.SSS";
    
    private static final String FORMAT_FRIENDLY_TIMESTAMP = "dd.MM.yyyy HH:mm:ss";
    
    private static final String FORMAT_6CHARS = "yyyyMM";
    private static final String FORMAT_8CHARS = "yyyyMMdd";
    
    private static final String FORMAT_ISO_8601 = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String FORMAT_ISO_8601_DAY = "yyyy-MM-dd";
    
    private static final String FORMAT_RFC_822 = "EEE, d MMM yyyy HH:mm:ss Z";
    
    
    /**
     * Returns a Date set to the first possible millisecond of the day, just
     * after midnight. If a null day is passed in, a new Date is created.
     * midnight (00m 00h 00s)
     */
    public static Date getStartOfDay(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withHour(0).withMinute(0).withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the first possible millisecond of the day, just
     * after midnight. If a null day is passed in, a new Date is created.
     * midnight (00m 00h 00s)
     */
    public static Date getStartOfDay(Date day, Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withHour(zoned.getHour()).withMinute(0).withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the last possible millisecond of the day, just
     * before midnight. If a null day is passed in, a new Date is created.
     * midnight (00m 00h 00s)
     */
    public static Date getEndOfDay(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    public static Date getEndOfDay(Date day,Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the first possible millisecond of the hour.
     * If a null day is passed in, a new Date is created.
     */
    public static Date getStartOfHour(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withMinute(0).withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the first possible millisecond of the hour.
     * If a null day is passed in, a new Date is created.
     */
    public static Date getStartOfHour(Date day, Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withMinute(0).withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the last possible millisecond of the day, just
     * before midnight. If a null day is passed in, a new Date is created.
     * midnight (00m 00h 00s)
     */
    public static Date getEndOfHour(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withMinute(59).withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    public static Date getEndOfHour(Date day, Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        if (day == null || cal == null) {
            return day;
        }
        ZonedDateTime zoned = ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withMinute(59).withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the first possible millisecond of the minute.
     * If a null day is passed in, a new Date is created.
     */
    public static Date getStartOfMinute(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the first possible millisecond of the minute.
     * If a null day is passed in, a new Date is created.
     */
    public static Date getStartOfMinute(Date day, Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the last possible millisecond of the minute.
     * If a null day is passed in, a new Date is created.
     */
    public static Date getEndOfMinute(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    public static Date getEndOfMinute(Date day, Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        if (day == null || cal == null) {
            return day;
        }
        ZonedDateTime zoned = ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the first possible millisecond of the month, just
     * after midnight. If a null day is passed in, a new Date is created.
     * midnight (00m 00h 00s)
     */
    public static Date getStartOfMonth(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    public static Date getStartOfMonth(Date day, Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set to the last possible millisecond of the month, just
     * before midnight. If a null day is passed in, a new Date is created.
     * midnight (00m 00h 00s)
     */
    public static Date getEndOfMonth(Date day) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        int lastDay = zoned.toLocalDate().lengthOfMonth();
        zoned = zoned.withDayOfMonth(lastDay).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    public static Date getEndOfMonth(Date day,Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        int lastDay = zoned.toLocalDate().lengthOfMonth();
        zoned = zoned.withDayOfMonth(lastDay).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a Date set just to Noon, to the closest possible millisecond
     * of the day. If a null day is passed in, a new Date is created.
     * noon (00m 12h 00s)
     */
    public static Date getNoonOfDay(Date day, Calendar cal) {
        // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
        ZonedDateTime zoned = day == null 
            ? ZonedDateTime.now(ZoneOffset.UTC) 
            : ZonedDateTime.ofInstant(day.toInstant(), ZoneOffset.UTC);
        zoned = zoned.withHour(12).withMinute(0).withSecond(0).withNano(0);
        return Date.from(zoned.toInstant());
    }
    
    
    /**
     * Returns a java.sql.Timestamp equal to the current time
     **/
    public static java.sql.Timestamp now() {
        // cr-java-0111: Use Instant instead of Date.from(Instant.now())
        return new java.sql.Timestamp(Instant.now().toEpochMilli());
    }
    
    
    /**
     * Returns a string the represents the passed-in date parsed
     * according to the passed-in format.  Returns an empty string
     * if the date or the format is null.
     **/
    public static String format(Date aDate, DateTimeFormatter aFormat) {
        if (aDate == null || aFormat == null ) { return ""; }
        return aFormat.format(aDate.toInstant());
    }
    
    
    /**
     * Returns a Date using the passed-in string and format.  Returns null if the string
     * is null or empty or if the format is null.  The string must match the format.
     **/
    public static Date parse(String aValue, DateTimeFormatter aFormat) throws ParseException {
        if (StringUtils.isEmpty(aValue) || aFormat == null) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(aValue, aFormat);
            return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (Exception e) {
            throw new ParseException("Failed to parse date: " + aValue, 0);
        }
    }
    
    
    /**
     * Returns true if endDate is after startDate or if startDate equals endDate
     * or if they are the same date.  Returns false if either value is null.
     **/
    public static boolean isValidDateRange(Date startDate, Date endDate) {
        return isValidDateRange(startDate, endDate, true);
    }
    
    
    /**
     * Returns true if endDate is after startDate or if startDate equals endDate.
     * Returns false if either value is null.  If equalOK, returns true if the
     * dates are equal.
     **/
    public static boolean isValidDateRange(Date startDate, Date endDate, boolean equalOK) {
        // false if either value is null
        if (startDate == null || endDate == null) { return false; }
        
        if (equalOK && startDate.equals(endDate)) {
            // true if they are equal
            return true;
        }
        
        // true if endDate after startDate
        if (endDate.after(startDate)) {
            return true;
        }
        
        return false;
    }
    
    
    // convenience method returns minimal date format
    public static DateTimeFormatter defaultDateFormat() {
        return DateUtil.friendlyDateFormat(true);
    }
    
    
    // convenience method returns minimal date format
    public static DateTimeFormatter minimalDateFormat() {
        return friendlyDateFormat(true);
    }
    
    
    // convenience method that returns friendly data format
    // using full month, day, year digits.
    public static DateTimeFormatter fullDateFormat() {
        return friendlyDateFormat(false);
    }
    
    
    /** 
     * Returns a "friendly" date format.
     * @param minimalFormat Should the date format allow single digits.
     **/
    public static DateTimeFormatter friendlyDateFormat(boolean minimalFormat) {
        if (minimalFormat) {
            return DateTimeFormatter.ofPattern(FORMAT_DEFAULT_DATE_MINIMAL).withZone(ZoneOffset.UTC);
        }
        
        return DateTimeFormatter.ofPattern(FORMAT_DEFAULT_DATE).withZone(ZoneOffset.UTC);
    }
    
    
    // returns full timestamp format
    public static DateTimeFormatter defaultTimestampFormat() {
        return DateTimeFormatter.ofPattern(FORMAT_DEFAULT_TIMESTAMP).withZone(ZoneOffset.UTC);
    }
    
    
    // convenience method returns long friendly timestamp format
    public static DateTimeFormatter friendlyTimestampFormat() {
        return DateTimeFormatter.ofPattern(FORMAT_FRIENDLY_TIMESTAMP).withZone(ZoneOffset.UTC);
    }
    
    
    // convenience method returns minimal date format
    public static DateTimeFormatter get8charDateFormat() {
        return DateTimeFormatter.ofPattern(FORMAT_8CHARS).withZone(ZoneOffset.UTC);
    }
    
    
    // convenience method returns minimal date format
    public static DateTimeFormatter get6charDateFormat() {
        return DateTimeFormatter.ofPattern(FORMAT_6CHARS).withZone(ZoneOffset.UTC);
    }
    
    
    // convenience method returns minimal date format
    public static DateTimeFormatter getIso8601DateFormat() {
        return DateTimeFormatter.ofPattern(FORMAT_ISO_8601).withZone(ZoneOffset.UTC);
    }
    
    
    // convenience method returns minimal date format
    public static DateTimeFormatter getIso8601DayDateFormat() {
        return DateTimeFormatter.ofPattern(FORMAT_ISO_8601_DAY).withZone(ZoneOffset.UTC);
    }
    
    
    // convenience method returns minimal date format
    public static DateTimeFormatter getRfc822DateFormat() {
        // http://www.w3.org/Protocols/rfc822/Overview.html#z28
        // Using Locale.US to fix ROL-725 and ROL-628
        return DateTimeFormatter.ofPattern(FORMAT_RFC_822, Locale.US).withZone(ZoneOffset.UTC);
    }
    
    
    // convenience method
    public static String defaultDate(Date date) {
        return format(date, defaultDateFormat());
    }
    
    
    // convenience method using minimal date format
    public static String minimalDate(Date date) {
        return format(date, DateUtil.minimalDateFormat());
    }
    
    
    public static String fullDate(Date date) {
        return format(date, DateUtil.fullDateFormat());
    }
    
    
    /**
     * Format the date using the "friendly" date format.
     */
    public static String friendlyDate(Date date, boolean minimalFormat) {
        return format(date, friendlyDateFormat(minimalFormat));
    }
    
    
    // convenience method
    public static String friendlyDate(Date date) {
        return format(date, friendlyDateFormat(true));
    }
    
    
    // convenience method
    public static String defaultTimestamp(Date date) {
        return format(date, defaultTimestampFormat());
    }
    
    
    // convenience method returns long friendly formatted timestamp
    public static String friendlyTimestamp(Date date) {
        return format(date, friendlyTimestampFormat());
    }
    
    
    // convenience method returns 8 char day stamp YYYYMMDD
    public static String format8chars(Date date) {
        return format(date, get8charDateFormat());
    }

    // convenience method returns 8 char day stamp YYYYMMDD using time zone
    public static String format8chars(Date date, TimeZone tz) {
        DateTimeFormatter formatter = get8charDateFormat().withZone(tz.toZoneId());
        return format(date, formatter);
    }

    // convenience method returns 6 char month stamp YYYYMM
    public static String format6chars(Date date) {
        return format(date, get6charDateFormat());
    }

    // convenience method returns 6 char month stamp YYYYMM using time zone
    public static String format6chars(Date date, TimeZone tz) {
        DateTimeFormatter formatter = get6charDateFormat().withZone(tz.toZoneId());
        return format(date, formatter);
    }

    // convenience method returns long friendly formatted timestamp
    public static String formatIso8601Day(Date date) {
        return format(date, getIso8601DayDateFormat());
    }
    
    
    public static String formatRfc822(Date date) {
        return format(date, getRfc822DateFormat());
    }
    
    
    // This is a hack, but it seems to work
    public static String formatIso8601(Date date) {
        if (date == null) {
            return "";
        }
        
        // Add a colon 2 chars before the end of the string
        // to make it a valid ISO-8601 date.
        
        String str = format(date, getIso8601DateFormat());
        StringBuilder sb = new StringBuilder();
        sb.append( str.substring(0,str.length()-2) );
        sb.append( ":" );
        sb.append( str.substring(str.length()-2) );
        return sb.toString();
    }
    
    
    public static Date parseIso8601(String value) throws Exception {
        return ISO8601DateParser.parse(value);
    }
    
    
    /**
     * Parse data as either 6-char or 8-char format.
     */
    public static Date parseWeblogURLDateString(String dateString, TimeZone tz, Locale locale) {
        
        Date ret = Date.from(Instant.now());
        DateTimeFormatter char8DateFormat = DateUtil.get8charDateFormat();
        DateTimeFormatter char6DateFormat = DateUtil.get6charDateFormat();
        
        if (dateString != null
                && dateString.length()==8
                && StringUtils.isNumeric(dateString) ) {
            try {
                LocalDate localDate = LocalDate.parse(dateString, char8DateFormat);
                ret = Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (Exception e) {
                // parse failed, keep ret as now
            }
            
            // make sure the requested date is not in the future
            // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
            ZonedDateTime todayZoned = ZonedDateTime.now(ZoneOffset.UTC);
            Date today = Date.from(todayZoned.toInstant());
            if(ret.after(today)) {
                ret = today;
            }
            
        } else if(dateString != null
                && dateString.length()==6
                && StringUtils.isNumeric(dateString)) {
            try {
                LocalDate localDate = LocalDate.parse(dateString, char6DateFormat);
                ret = Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (Exception e) {
                // parse failed, keep ret as now
            }
            
            // make sure the requested date is not in the future
            // cr-java-0111: Replace Calendar with java.time API, standardized on UTC
            ZonedDateTime todayZoned = ZonedDateTime.now(ZoneOffset.UTC);
            Date today = Date.from(todayZoned.toInstant());
            if(ret.after(today)) {
                ret = today;
            }
        }
        
        return ret;
    }
    
}
