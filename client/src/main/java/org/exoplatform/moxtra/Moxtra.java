/*
 * Copyright (C) 2003-2015 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.moxtra;

import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.ConversationState;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * General constants, utility methods and formats for work with Moxtra services.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: Moxtra.java 00000 Apr 8, 2015 pnedonosko $
 * 
 */
public class Moxtra {

  public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

  static {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DATE_FORMAT.setTimeZone(tz);
  }

  /**
   * 
   */
  private Moxtra() {
  }

  /**
   * Java {@link Calendar} instance for now time, initialized with UTC timezone as Moxtra requires.
   * 
   * @return {@link Calendar}
   */
  public static final Calendar getCalendar() {
    return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
  }

  /**
   * Java {@link Calendar} instance for given date, initialized with UTC timezone as Moxtra requires.
   * 
   * @return {@link Calendar}
   */
  public static final Calendar getCalendar(Date time) {
    Calendar cal = getCalendar();
    cal.setTime(time);
    return cal;
  }

  /**
   * Java {@link Calendar} instance for given date in milliseconds, initialized with UTC timezone as Moxtra
   * requires.
   * 
   * @return {@link Calendar}
   */
  public static final Calendar getCalendar(Long timeInMillis) {
    Calendar cal = getCalendar();
    cal.setTimeInMillis(timeInMillis);
    return cal;
  }

  /**
   * Java {@link Date} for given date in milliseconds, date with UTC timezone as Moxtra
   * requires.
   * 
   * @return {@link Long}
   */
  public static final Date getDate(Long timeInMillis) {
    return getCalendar(timeInMillis).getTime();
  }

  /**
   * Java {@link Date} for given date but in UTC timezone as Moxtra requires.
   * 
   * @return {@link Date}
   */
  public static final Date getDate(Date localDate) {
    return getCalendar(localDate).getTime();
  }

  /**
   * Format date in ISO-8601 format "YYYY-MM-DDThh:mm:ssZ" with UTC timezone.
   * 
   * @param date {@link Date}
   * @return {@link String}
   */
  public static final String formatDate(Date date) {
    return DATE_FORMAT.format(date);
  }

  /**
   * Parse date from a string in ISO-8601 format "YYYY-MM-DDThh:mm:ssZ" with UTC timezone.
   * 
   * @param dateStr {@link String}
   * @return {@link Date}
   * @throws ParseException if string cannot be parsed
   */
  public static final Date parseDate(String dateStr) throws ParseException {
    return DATE_FORMAT.parse(dateStr);
  }

  /**
   * Find most viable user's full name.
   * 
   * @param user {@link User} organization user
   * @return
   */
  public static String fullName(User user) {
    String name = user.getDisplayName();
    if (name == null || name.trim().length() == 0) {
      name = user.getFirstName() + " " + user.getLastName();
      if (name.trim().length() == 0) {
        name = user.getEmail();
        if (name == null || name.trim().length() == 0) {
          name = user.getUserName();
        }
      }
    }
    return name;
  }

  /**
   * Return meaningful value of some text in the string. If string is empty then <code>null</code> will be
   * returned.
   * 
   * @param value input {@link String}
   * @return textual value or <code>null</code>
   */
  public static String cleanValue(String value) {
    if (value != null && value.trim().length() == 0) {
      return null;
    }
    return value;
  }

  /**
   * Current eXo user name as set in identity of {@link ConversationState#getCurrent()}.
   * 
   * @return {@link String} with current eXo user name
   * @throws ConversationStateNotFoundException when no current {@link ConversationState} set
   */
  public static String currentUserName() {
    ConversationState currentConvo = ConversationState.getCurrent();
    if (currentConvo != null) {
      return currentConvo.getIdentity().getUserId();
    } else {
      throw new ConversationStateNotFoundException("Current conversation state not set in "
          + Thread.currentThread());
    }
  }

}
