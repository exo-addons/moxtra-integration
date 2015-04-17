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
package org.exoplatform.moxtra.calendar.webui;

import org.apache.commons.lang.reflect.FieldUtils;
import org.exoplatform.calendar.service.CalendarEvent;
import org.exoplatform.calendar.service.Utils;
import org.exoplatform.moxtra.calendar.MoxtraCalendarService;
import org.exoplatform.moxtra.calendar.webui.UICalendarView.ConfirmCloseActionListener;
import org.exoplatform.moxtra.calendar.webui.UICalendarView.MoveEventActionListener;
import org.exoplatform.moxtra.calendar.webui.UICalendarView.UpdateEventActionListener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UICalendarView.java 00000 Apr 15, 2015 pnedonosko $
 * 
 */
@ComponentConfig(events = { @EventConfig(listeners = ConfirmCloseActionListener.class),
    @EventConfig(listeners = UpdateEventActionListener.class),
    @EventConfig(listeners = MoveEventActionListener.class) })
public class UICalendarView extends UIForm {

  public static final String CALENDARID  = "calendarId".intern();

  public static final String CALTYPE     = "calType".intern();

  public static final String EVENTID     = "eventId".intern();

  public static final String START_TIME  = "startTime".intern();

  public static final String FINISH_TIME = "finishTime".intern();

  private static final Log   LOG         = ExoLogger.getExoLogger(UICalendarView.class);

  public static class ConfirmCloseActionListener extends EventListener<UIForm> {
    @Override
    public void execute(Event<UIForm> event) throws Exception {
      UIForm uiCalendarView = event.getSource();

      // Event id, calendar id and type fields exist in Celendar's UICalendarView and it doesn't clean them on
      // the confirmation
      // XXX read them by reflection
      // String eventId = (String) FieldUtils.readField(uiCalendarView, "singleDeletedEventId", true);
      // String calendarId = (String) FieldUtils.readField(uiCalendarView, "singleDeletedCalendarId", true);
      String calendarType = (String) FieldUtils.readField(uiCalendarView, "singleDeletedEventType", true);

      // if (calendarId != null && calendarType != null && eventId != null) {
      if (calendarType != null && String.valueOf(Utils.PRIVATE_TYPE).equals(calendarType)) {
        // it was called: calendarService.removeUserEvent(username, calendarId, eventId);
        MoxtraCalendarService moxtra = (MoxtraCalendarService) uiCalendarView.getApplicationComponent(MoxtraCalendarService.class);
        moxtra.deleteMeet();
      }
    }
  }

  public static class UpdateEventActionListener extends EventListener<UIForm> {
    @Override
    public void execute(Event<UIForm> event) throws Exception {
      UIForm uiCalendarView = event.getSource();

      // Updated event's id, calendar id and type exist in the request
      // and it may be obtained from the view parent class method getLastUpdatedEventId()
      WebuiRequestContext context = event.getRequestContext();
      // This action will be added to different views and they do different request parameters:
      String eventId = context.getRequestParameter(OBJECTID);
      String calendarId = context.getRequestParameter(eventId + CALENDARID);
      String calendarType = context.getRequestParameter(eventId + CALTYPE);
      // String startTime = context.getRequestParameter(eventId + START_TIME);
      // String endTime = context.getRequestParameter(eventId + FINISH_TIME);
      if (eventId == null || !eventId.startsWith("Event")) {
        // try approach of UIMonthView
        eventId = context.getRequestParameter(EVENTID);
        calendarId = context.getRequestParameter(CALENDARID);
        calendarType = context.getRequestParameter(CALTYPE);
      }

      if (calendarId != null && calendarType != null && eventId != null) {
        if (String.valueOf(Utils.PRIVATE_TYPE).equals(calendarType)) {
          // it was called: calendarService.saveUserEvent(username, calendarId, eventId);
          MoxtraCalendarService moxtra = (MoxtraCalendarService) uiCalendarView.getApplicationComponent(MoxtraCalendarService.class);
          CalendarEvent calendarEvent = moxtra.getEvent(eventId);
          if (calendarEvent != null) {
            moxtra.saveMeet(calendarId, calendarEvent);
          } else {
            LOG.warn("Cannot find updated event " + eventId + " in calendar " + calendarId);
          }

        }
      }
    }
  }

  public static class MoveEventActionListener extends EventListener<UIForm> {
    @Override
    public void execute(Event<UIForm> event) throws Exception {
      UIForm uiCalendarView = event.getSource();

      WebuiRequestContext context = event.getRequestContext();
      String eventIds = context.getRequestParameter(OBJECTID);
      String calendarId = context.getRequestParameter(CALENDARID);
      String calendarType = event.getRequestContext().getRequestParameter(CALTYPE);

      if (calendarId != null && calendarType != null && eventIds != null) {
        if (String.valueOf(Utils.PRIVATE_TYPE).equals(calendarType)) {
          MoxtraCalendarService moxtra = (MoxtraCalendarService) uiCalendarView.getApplicationComponent(MoxtraCalendarService.class);
          for (String eventId : eventIds.split(",")) {
            eventId = eventId.trim();
            CalendarEvent calendarEvent = moxtra.getEvent(eventId);
            if (calendarEvent != null) {
              moxtra.saveMeet(calendarId, calendarEvent);
            } else {
              LOG.warn("Cannot find moved event " + eventId + " in calendar " + calendarId);
            }
          }
        }
      }
    }
  }
}
