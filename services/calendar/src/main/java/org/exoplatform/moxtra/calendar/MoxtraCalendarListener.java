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
import org.exoplatform.calendar.service.impl.CalendarEventListener;

import java.util.Date;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraCalendarListener.java 00000 Mar 15, 2015 pnedonosko $
 * 
 */
public class MoxtraCalendarListener extends CalendarEventListener {

  protected final MoxtraCalendarService moxtra;

  /**
   * 
   */
  public MoxtraCalendarListener(MoxtraCalendarService moxtra) {
    this.moxtra = moxtra;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void savePublicEvent(CalendarEvent event, String calendarId) {
    // super.savePublicEvent(event, calendarId);
    moxtra.saveMeet(calendarId, event);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updatePublicEvent(CalendarEvent event, String calendarId) {
    // super.updatePublicEvent(event, calendarId);
    moxtra.saveMeet(calendarId, event);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deletePublicEvent(CalendarEvent event, String calendarId) {
    // super.deletePublicEvent(event, calendarId);
    //moxtra.deleteMeet(calendarId, event);
    moxtra.deleteMeet(); // delete meet from the context
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updatePublicEvent(CalendarEvent oldEvent, CalendarEvent event, String calendarId) {
    //super.updatePublicEvent(oldEvent, event, calendarId);
    moxtra.saveMeet(calendarId, event); // TODO need oldEvent?
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeOneOccurrence(CalendarEvent originEvent, CalendarEvent removedEvent) {
    // TODO Auto-generated method stub
    super.removeOneOccurrence(originEvent, removedEvent);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateFollowingOccurrences(CalendarEvent originEvent, Date stopDate) {
    // TODO Auto-generated method stub
    super.updateFollowingOccurrences(originEvent, stopDate);
  }

}
