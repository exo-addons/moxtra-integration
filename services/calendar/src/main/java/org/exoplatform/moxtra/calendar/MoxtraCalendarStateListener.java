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
package org.exoplatform.moxtra.calendar;

import org.exoplatform.calendar.service.CalendarEvent;
import org.exoplatform.moxtra.client.MoxtraMeet;

/**
 * A way to customize Moxtra features in eXo Calendar by external services (e.g. by eXo Social when it is
 * a space calendar).<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraCalendarStateListener.java 00000 Jun 16, 2015 pnedonosko $
 * 
 */
public interface MoxtraCalendarStateListener {
  
  /**
   * Meet read in given calendar and event.
   * 
   * @param meet {@link MoxtraMeet}
   * @param calendarId {@link String}
   * @param event {@link CalendarEvent} 
   */
  void onMeetRead(MoxtraMeet meet, String calendarId, CalendarEvent event);
  
  /**
   * Meet updated in given calendar and event.
   * 
   * @param meet {@link MoxtraMeet}
   * @param calendarId {@link String}
   * @param event {@link CalendarEvent} 
   */
  void onMeetWrite(MoxtraMeet meet, String calendarId, CalendarEvent event);
  
  /**
   * Meet removed from calendar event or event itself removed.
   * 
   * @param meet {@link MoxtraMeet}
   * @param calendarId {@link String}
   * @param event {@link CalendarEvent} or <code>null</code> if event was deleted also
   */
  void onMeetDelete(MoxtraMeet meet, String calendarId, CalendarEvent event);

}
