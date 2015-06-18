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

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.calendar.service.Calendar;
import org.exoplatform.calendar.service.CalendarEvent;
import org.exoplatform.calendar.service.CalendarSetting;
import org.exoplatform.calendar.service.Reminder;
import org.exoplatform.calendar.service.Utils;
import org.exoplatform.calendar.service.impl.CalendarServiceImpl;
import org.exoplatform.calendar.service.impl.NewUserListener;
import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.UserNotFoundException;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.client.MoxtraForbiddenException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraMeetRecording;
import org.exoplatform.moxtra.client.MoxtraMeetRecordings;
import org.exoplatform.moxtra.client.MoxtraOwnerUndefinedException;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.commons.BaseMoxtraService;
import org.exoplatform.moxtra.jcr.JCR;
import org.exoplatform.moxtra.webui.MoxtraNotActivatedException;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.scheduler.JobInfo;
import org.exoplatform.services.scheduler.impl.JobSchedulerServiceImpl;
import org.exoplatform.services.security.ConversationState;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Business logic of Moxtra in eXo Calendar integration.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraCalendarService.java 00000 Mar 2, 2015 pnedonosko $
 * 
 */
public class MoxtraCalendarService extends BaseMoxtraService {

  protected static final Log LOG                           = ExoLogger.getLogger(MoxtraCalendarService.class);

  public final static String EVENT_STATE_BUSY              = "busy".intern();

  public final static String COMMA                         = ",".intern();

  public final static String CALENDAR_TYPE_PUBLIC          = String.valueOf(Calendar.TYPE_PUBLIC);

  public final static String CALENDAR_TYPE_PRIVATE         = String.valueOf(Calendar.TYPE_PRIVATE);

  public final static String CALENDAR_TYPE_SHARED          = String.valueOf(Calendar.TYPE_SHARED);

  /**
   * A period in ms, if scheduling meet event will happen later of it comparing to current time, then a
   * reminder will be set up for the calendar event.
   */
  public final static long   MEET_EVENT_REMINDER_THRESHOLD = 60 * 60 * 1000;

  public final static long   MEET_SCHEDULED_REFRESH_PERIOD = 60 * 1000;

  public final static long   MEET_ENDED_REFRESH_PERIOD     = 3 * 60 * 1000;

  protected class UserSettings {
    final ConversationState conversation;

    final ExoContainer      container;

    ConversationState       prevConversation;

    ExoContainer            prevContainer;

    SessionProvider         prevSessions;

    UserSettings(ConversationState conversation, ExoContainer container) {
      this.conversation = conversation;
      this.container = container;
    }
  }

  /**
   * Setup environment for jobs execution in eXo Container.
   */
  protected class Environment {

    protected final Map<String, UserSettings> config = new ConcurrentHashMap<String, UserSettings>();

    protected void configure(String userName) throws MoxtraCalendarException {
      ConversationState conversation = ConversationState.getCurrent();
      if (conversation == null) {
        throw new MoxtraCalendarException("Error configuring user environment for " + userName
            + ". User identity not set.");
      }
      config.put(userName, new UserSettings(conversation, ExoContainerContext.getCurrentContainer()));
    }

    protected void prepare(String userName) throws MoxtraCalendarException {
      UserSettings settings = config.get(userName);
      if (settings != null) {
        settings.prevConversation = ConversationState.getCurrent();
        ConversationState.setCurrent(settings.conversation);

        // set correct container
        settings.prevContainer = ExoContainerContext.getCurrentContainerIfPresent();
        ExoContainerContext.setCurrentContainer(settings.container);

        // set correct SessionProvider
        settings.prevSessions = sessionProviderService.getSessionProvider(null);
        SessionProvider sp = new SessionProvider(settings.conversation);
        sessionProviderService.setSessionProvider(null, sp);
      } else {
        throw new MoxtraCalendarException("User setting not configured to prepare " + userName
            + " environment.");
      }
    }

    protected void cleanup(String userName) throws MoxtraCalendarException {
      UserSettings settings = config.get(userName);
      if (settings != null) {
        ConversationState.setCurrent(settings.prevConversation);
        ExoContainerContext.setCurrentContainer(settings.prevContainer);
        SessionProvider sp = sessionProviderService.getSessionProvider(null);
        sessionProviderService.setSessionProvider(null, settings.prevSessions);
        sp.close();
      } else {
        throw new MoxtraCalendarException("User setting not configured to clean " + userName
            + " environment.");
      }
    }
  }

  /**
   * Moxtra app enabled in current context.
   */
  protected final ThreadLocal<MoxtraCalendarApplication> contextApp       = new ThreadLocal<MoxtraCalendarApplication>();

  protected final MimeTypeResolver                       mimetypeResolver = new MimeTypeResolver();

  protected final Environment                            jobEnvironment   = new Environment();

  /**
   * CalendarService implementation required to access JCR data storage.
   */
  protected final CalendarServiceImpl                    calendar;

  /**
   * OrganizationService to find eXo users email.
   */
  protected final OrganizationService                    orgService;

  protected final JobSchedulerServiceImpl                schedulerService;

  protected final NodeHierarchyCreator                   hierarchyCreator;

  protected final SessionProviderService                 sessionProviderService;

  protected final ManageDriveService                     driveService;

  protected final Set<MoxtraCalendarStateListener>       stateListeners   = new LinkedHashSet<MoxtraCalendarStateListener>();

  protected final Queue<String>                          downloadingMeets = new ConcurrentLinkedQueue<String>();

  /**
   * @throws MoxtraConfigurationException
   * 
   */
  public MoxtraCalendarService(MoxtraService moxtra,
                               SessionProviderService sessionProviderService,
                               NodeHierarchyCreator hierarchyCreator,
                               CalendarServiceImpl calendar,
                               OrganizationService orgService,
                               JobSchedulerServiceImpl schedulerService,
                               ManageDriveService driveService) {
    super(moxtra);
    this.sessionProviderService = sessionProviderService;
    this.hierarchyCreator = hierarchyCreator;
    this.calendar = calendar;
    this.orgService = orgService;
    this.schedulerService = schedulerService;
    this.driveService = driveService;
  }

  public void addListener(MoxtraCalendarStateListener listener) {
    stateListeners.add(listener);
  }

  public void removeListener(MoxtraCalendarStateListener listener) {
    stateListeners.remove(listener);
  }

  public CalendarEvent getEvent(String eventId) throws Exception {
    return calendar.getEventById(eventId);
  }

  public CalendarEvent getGroupEvent(String eventId) throws Exception {
    return calendar.getGroupEvent(eventId);
  }

  public CalendarSetting getCalendarSetting() throws Exception {
    return calendar.getCalendarSetting(Moxtra.currentUserName());
  }

  public MoxtraMeet getMeet(CalendarEvent event) throws MoxtraCalendarException {
    try {
      String userName = Moxtra.currentUserName();
      // XXX event has wrong calendar type (private)
      // try using public type, then private and then shared
      Node eventNode = readEventNode(userName,
                                     String.valueOf(Utils.PUBLIC_TYPE),
                                     event.getCalendarId(),
                                     event.getId());
      if (eventNode == null) {
        eventNode = readEventNode(userName,
                                  String.valueOf(Utils.PRIVATE_TYPE),
                                  event.getCalendarId(),
                                  event.getId());
      }

      if (eventNode == null) {
        eventNode = readEventNode(userName,
                                  String.valueOf(Utils.SHARED_TYPE),
                                  event.getCalendarId(),
                                  event.getId());
      }

      MoxtraMeet existing;
      if (eventNode != null && JCR.isServices(eventNode)) {
        try {
          // read meet from eventNode, if meet exists:
          // if user authorized, then read it from Moxtra for latest updates
          // if user not authorized, return local meet, but issue in UI that need auth in Moxtra for latest
          // updates
          Node meetNode = JCR.getMeet(eventNode);
          // read locally stored meet
          existing = readMeet(meetNode, userName);

          fireMeetRead(existing, event.getCalendarId(), event);
        } catch (PathNotFoundException e) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Meet not found or not complete for event " + event.getSummary() + " in "
                + eventNode.getName() + ". " + e.getMessage());
          }
          existing = null;
        }
      } else {
        existing = null;
      }

      return existing;
    } catch (Exception e) {
      throw new MoxtraCalendarException("Error reading event node", e);
    }
  }

  /**
   * Return {@link EventMeet} instance that can be used for meet creation. This method doesn't create or
   * schedule a meet in Moxtra. Returned instance is an editor of the meet.
   * 
   * @return {@link EventMeet}
   */
  public MoxtraMeet newMeet() {
    return new MoxtraMeet().editor();
  }

  public String getGroupIdFromCalendarId(String calendarId) {
    if (calendarId != null && calendarId.indexOf(Utils.SPACE_CALENDAR_ID_SUFFIX) > 0) {
      return Utils.getSpaceGroupIdFromCalendarId(calendarId);
    }
    return null;
  }

  public String getCalendarIdFromGroupId(String groupId) {
    return Utils.getCalendarIdFromSpace(groupId);
  }

  /**
   * Create scheduled Meet in a new Event. This method can be used from external services (e.g. social) to
   * manage group meets.
   * 
   * @param groupId {@link String}
   * @param meet {@link MoxtraMeet}
   * @return {@link CalendarEvent}
   * @throws Exception
   */
  public CalendarEvent createMeet(String calendarId, MoxtraMeet meet) throws Exception {
    String userName = Moxtra.currentUserName();
    // create event first
    CalendarSetting userSettings = calendar.getCalendarSetting(userName);

    // long spaghetti to init the event :)
    CalendarEvent event = new CalendarEvent();
    event.setSummary(meet.getName());
    event.setDescription(meet.getAgenda());
    event.setCalendarId(calendarId);
    event.setCalType(CALENDAR_TYPE_PUBLIC);
    event.setEventType(CalendarEvent.TYPE_EVENT);
    event.setEventState(EVENT_STATE_BUSY);
    event.setEventCategoryId(NewUserListener.DEFAULT_EVENTCATEGORY_ID_MEETING);
    event.setEventCategoryName("Meetings"); // TODO constant?
    event.setSendOption(userSettings.getSendOption());

    event.setRepeatType(CalendarEvent.RP_NOREPEAT);
    event.setRepeatInterval(0);
    event.setRepeatCount(0);
    event.setRepeatUntilDate(null);
    event.setRepeatByDay(null);
    event.setRepeatByMonthDay(null);

    event.setPrivate(false);
    // TODO do we have a constant for the below string?
    event.setPriority("none");
    event.setEventState(EVENT_STATE_BUSY);

    // participants
    List<MoxtraUser> users = meet.getUsers();
    List<String> parts = new ArrayList<String>();
    List<String> partStatuses = new ArrayList<String>();
    List<String> invitations = new ArrayList<String>();
    List<String> reminded = new ArrayList<String>();
    for (int i = 0; i < users.size(); i++) {
      MoxtraUser user = users.get(i);
      String uid = user.getUniqueId();
      if (uid != null) {
        // it's eXo user
        parts.add(uid);
        partStatuses.add(uid + ":");
      } else {
        // invited by email or Moxtra user
        String email = user.getEmail();
        partStatuses.add(email + ":");
        invitations.add(email);
        reminded.add(email);
      }
    }
    // [pnedonosko, james]
    event.setParticipant(parts.toArray(new String[parts.size()]));
    // [pnedonosko:, james:, pnedonosko@yahoo.com:]
    event.setParticipantStatus(partStatuses.toArray(new String[partStatuses.size()]));
    // [pnedonosko@yahoo.com]
    event.setInvitation(invitations.toArray(new String[invitations.size()]));

    // use email reminder for the event if it is scheduled for more than a hour from now
    Date now = java.util.Calendar.getInstance().getTime();
    Date startTime = meet.getStartTime();
    if (startTime.getTime() - now.getTime() >= MEET_EVENT_REMINDER_THRESHOLD) {
      Reminder email = new Reminder();
      email.setReminderType(Reminder.TYPE_EMAIL);
      email.setReminderOwner(userName);

      // email.setAlarmBefore(Long.parseLong(getEmailRemindBefore())) ;
      StringBuffer sbAddress = new StringBuffer();
      for (String s : reminded) {
        if (sbAddress.length() > 0) {
          sbAddress.append(COMMA);
        }
        sbAddress.append(s);
      }
      email.setEmailAddress(sbAddress.toString());
      email.setRepeate(false);
      // email.setRepeatInterval(Long.parseLong(getEmailRepeatInterVal()));
      email.setFromDateTime(startTime);
      event.setReminders(Collections.singletonList(email));
    }
    event.setFromDateTime(startTime);
    event.setToDateTime(meet.getEndTime());

    // create an event
    calendar.savePublicEvent(calendarId, event, true);
    try {
      // create meet in Moxtra and then save in the event
      Node eventNode = saveEvenMeet(userName, meet, event, null, null);

      // TODO need any extras regarding the space context?
      eventNode.save();

      return event;
    } catch (Exception e) {
      removeMeetEvent(meet, userName, calendarId, event);
      throw e;
    } catch (Throwable t) {
      removeMeetEvent(meet, userName, calendarId, event);
      throw t;
    }
  }

  /**
   * Refresh already scheduled Event meet with remote meet in Moxtra.
   * 
   * @throws Exception
   */
  public CalendarEvent refreshMeet(String calendarId, String eventId) throws Exception {
    String userName = Moxtra.currentUserName();
    // find event first
    CalendarEvent event = calendar.getGroupEvent(calendarId, eventId);
    if (event != null) {
      // meet will be already refreshed to remote state
      // MoxtraMeet meet = getMeet(event);
      Node eventNode = readEventNode(userName, CALENDAR_TYPE_PUBLIC, calendarId, event.getId());
      if (eventNode != null && JCR.isServices(eventNode)) {
        try {
          // read meet from eventNode, if meet exists:
          Node meetNode = JCR.getMeet(eventNode);

          // find initial state of autorecording and end time
          Date localEndTime;
          try {
            localEndTime = JCR.getEndTime(meetNode).getDate().getTime();
          } catch (PathNotFoundException e) {
            localEndTime = null;
          }
          boolean localAutorec;
          try {
            localAutorec = JCR.getAutoRecording(meetNode).getBoolean();
          } catch (PathNotFoundException e) {
            // can be null if such value was read from Moxtra
            localAutorec = false;
          }

          // read-refresh locally stored meet
          MoxtraMeet meet = readMeet(meetNode, userName);
          eventNode.save();

          // update event in calendar
          event.setFromDateTime(meet.getStartTime());
          event.setToDateTime(meet.getEndTime());
          event.setSummary(meet.getName());
          event.setDescription(meet.getAgenda());
          // TODO need update perticipants?

          // save the event (this will fire saveEventMeet() when running in calendar)
          calendar.savePublicEvent(calendarId, event, false);

          // manage auto-rec downloads
          // if auto-record enabled and end time changed
          boolean updateVideoDownload = meet.isAutoRecording() && !meet.getEndTime().equals(localEndTime);
          // if auto-record was disabled
          boolean cancelVideoDownload = !meet.isAutoRecording() && localAutorec;
          if (cancelVideoDownload) {
            // remove scheduled meet video download
            removeDownloadJob(event, meet);
          } else if (updateVideoDownload) {
            // update scheduled meet video download (for time)
            updateDownloadJob(event, meet);
          }

          return event;
        } catch (PathNotFoundException e) {
          throw new MoxtraCalendarException("Error reading meet event node " + event.getSummary(), e);
        }
      } else {
        throw new MoxtraCalendarException("Cannot find meet event node " + event.getSummary());
      }
    } else {
      throw new MoxtraCalendarException("Cannot find meet event in " + calendarId);
    }
  }

  /**
   * Confirm Meet for given Event or Task.
   * 
   * @throws MoxtraCalendarException
   */
  public void saveMeet(String calendarId, CalendarEvent event) {
    MoxtraCalendarApplication app = contextApp.get();
    if (app != null) {
      contextApp.remove();
      try {
        if (CalendarEvent.TYPE_EVENT.equals(event.getEventType())) {
          String userName = Moxtra.currentUserName();
          try {
            // FYI How it works:
            // Take meet from the context app, for new and updated events, submit it to Moxtra and the save it
            // in the event node on the end.
            MoxtraMeet meet = app.getMeet();
            String calType = app.getCalendarType(); // use cal type set in event form
            // XXX we assume that calendar id match here for fired event and event of the
            // associated form in the app

            boolean fireDeleted = meet.hasDeleted() && !meet.isNew();

            Node eventNode = saveEvenMeet(userName, meet, event, calType, calendarId);
            eventNode.save();

            // we have two type of meet ops here
            if (fireDeleted) {
              fireMeetDelete(meet, calendarId, event);
            } else {
              fireMeetWrite(meet, calendarId, event);
            }
          } catch (MoxtraNotActivatedException e) {
            // XXX webui app not activated for this event, this shouldn't happen but does when saving an event
            // from space's Moxtra app
            if (LOG.isDebugEnabled()) {
              LOG.debug("<<< " + e.getMessage());
            }
          } catch (MoxtraMeetNotFoundException e) {
            // we have no meet associated with this event
            if (LOG.isDebugEnabled()) {
              LOG.debug("<<< " + e.getMessage());
            }
          }
        } // else, not supported event type
      } catch (MoxtraCalendarException e) {
        LOG.error("Error getting calendar data", e);
      } catch (MoxtraClientException e) {
        LOG.error("Error saving even meet", e);
      } catch (MoxtraException e) {
        LOG.error("Error saving even meet", e);
      } catch (OAuthSystemException e) {
        LOG.error("Cannot save even meet due to authorization error", e);
      } catch (OAuthProblemException e) {
        LOG.error("Cannot save even meet due to authorization failure", e);
      } catch (RepositoryException e) {
        LOG.error("Cannot save even meet due to storage error", e);
      } catch (Exception e) {
        LOG.error("Error saving even meet", e);
      } finally {
        app.reset(); // reset meet in the app
      }
    } else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("No moxtra app found in context for even " + event.getSummary());
      }
    }
  }

  /**
   * Delete Meet from context app. An app should be initialized for some WebUI component, otherwise this
   * method will delete nothing. An app will be reset on the end.<br>
   * This method designed for use after event deletion when its node cannot be found to read a meet.
   */
  public void deleteMeet() {
    MoxtraCalendarApplication app = contextApp.get();
    if (app != null) {
      if (app.isActivated()) {
        contextApp.remove();
        try {
          // here we handle event deletion with existing meet
          if (app.hasMeet()) {
            MoxtraMeet meet = app.getMeet();
            moxtra.getClient().deleteMeet(meet);
            fireMeetDelete(meet, app.getEventCalendarId(), null);
          }
        } catch (MoxtraCalendarException e) {
          LOG.error("Error deleting context meet", e);
        } catch (MoxtraClientException e) {
          LOG.error("Error deleting context meet", e);
        } catch (MoxtraException e) {
          LOG.error("Error deleting context meet", e);
        } catch (OAuthSystemException e) {
          LOG.error("Cannot delete context meet due to authorization error", e);
        } catch (OAuthProblemException e) {
          LOG.error("Cannot delete context meet due to authorization failure", e);
        } finally {
          app.reset(); // reset the app
        }
      }
    } else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Moxtra app not activated in the context. It may be already reset.");
      }
    }
  }

  /**
   * Download meet video associated with given event and save the video to a location configured in the meet
   * node. This method should be called by a meet owner (host user) only.
   * 
   * @param userName {@link String} event/meet owner in eXo
   * @param event {@link CalendarEvent} event with enabled meet
   * @throws MoxtraCalendarException
   */
  public String downloadMeetVideo(String userName, CalendarEvent event) throws MoxtraCalendarException {
    // FYI event node will be in system session
    Node eventNode = readEventNode(userName, event.getCalType(), event.getCalendarId(), event.getId());
    try {
      if (JCR.isServices(eventNode)) {
        try {
          // read meet from eventNode
          Node meetNode = JCR.getMeet(eventNode);
          // read-refresh locally stored meet
          MoxtraMeet meet = readMeet(meetNode, userName);
          if (meet.hasRecordings()) {
            return MoxtraMeetDownloadJob.MEET_STATUS_DOWNLOADED;
          } else if (downloadingMeets.contains(meet.getSessionKey())) {
            return MoxtraMeetDownloadJob.MEET_STATUS_DOWNLOADING;
          } else {
            if (meet.isAutoRecording()) {
              // check meet status
              if (MoxtraMeet.SESSION_ENDED.equals(meet.getStatus())) {
                // meet session already finished - we can try to download the video
                // read remote meet for recent data (like download link)

                // mark the meet as downloading
                downloadingMeets.add(meet.getSessionKey());

                try {
                  MoxtraClient client = moxtra.getClient(userName);
                  MoxtraMeetRecordings recs = client.getMeetRecordings(meet);
                  List<MoxtraMeetRecording> recList = recs.getRecordings();
                  if (recs.getCount() > 0) {
                    // find destination node
                    Node meetings;
                    // apply defaults:
                    // * space's documents with folder 'Meetings/${EVENT_NAME}'
                    // * user's Personal Documents subfolder 'My Meetings/${EVENT_NAME}' for personal
                    // calendar and others
                    if (CALENDAR_TYPE_PUBLIC.equals(event.getCalType())) {
                      try {
                        Calendar spaceCal = calendar.getGroupCalendar(event.getCalendarId());
                        if (spaceCal == null) {
                          throw new MoxtraCalendarException("Space calendar not found "
                              + event.getCalendarId());
                        }
                        try {
                          // spaceCal.getCalendarPath(); // TODO can use it?
                          Node documents = getSpaceDocumentsNode(userName, spaceCal.getCalendarOwner());
                          if (documents != null) {
                            meetings = getMeetingsFolder(documents, "Meetings");
                          } else {
                            throw new MoxtraCalendarException("Unable to save meet video recordings for event "
                                + event.getSummary()
                                + ": cannot find Documents for space "
                                + spaceCal.getCalendarOwner());
                          }
                        } catch (Exception ce) {
                          throw new MoxtraCalendarException("Error opening meetings folder in space home "
                              + spaceCal.getCalendarOwner() + ". " + ce.getMessage(), ce);
                        }
                      } catch (Exception ce) {
                        throw new MoxtraCalendarException("Error reading space calendar "
                            + event.getCalendarId() + ". " + ce.getMessage(), ce);
                      }
                    } else if (CALENDAR_TYPE_PRIVATE.equals(event.getCalType())) {
                      try {
                        Node documents = getUserDocumentsNode(userName);
                        if (documents != null) {
                          meetings = getMeetingsFolder(documents, "My Meetings");
                        } else {
                          throw new MoxtraCalendarException("Unable to save meet video recordings for event "
                              + event.getSummary() + ": cannot find Personal Documents for user " + userName);
                        }
                      } catch (Exception he) {
                        throw new MoxtraCalendarException("Error opening meetings folder in user home "
                            + userName + ". " + he.getMessage(), he);
                      }
                    } else {
                      throw new MoxtraCalendarException("Event calendar type " + event.getCalType()
                          + " not supported. Meet download job canceled '" + event.getSummary() + "' in "
                          + event.getCalendarId() + " (" + meet.getStartMeetUrl() + ")");
                    }

                    // add this event meet folder in meetings
                    String meetNodeName = Text.escapeIllegalJcrChars(meet.getName());
                    Node meetFolder;
                    try {
                      meetFolder = meetings.getNode(meetNodeName);
                    } catch (PathNotFoundException e) {
                      meetFolder = meetings.addNode(meetNodeName, "nt:folder");
                      meetings.save(); // save to let actions add mixins
                    }
                    meetFolder.setProperty("exo:title", meet.getName());
                    meetFolder.setProperty("exo:name", meet.getName());
                    meetings.save();

                    List<Node> videos = new ArrayList<Node>();

                    for (int i = 0; i < recList.size(); i++) {
                      MoxtraMeetRecording rec = recList.get(i);
                      if (rec.getContentLength() > 0) {
                        String mimeType = rec.getContentType();
                        String fileExt = mimetypeResolver.getExtension(mimeType);
                        fileExt = (fileExt.length() > 0 ? "." + fileExt : fileExt);
                        
                        // XXX hardcoded support for video/mp4 what Moxtra uses for meet videos
                        if (fileExt.length() == 0 && mimeType.indexOf("mp4") > 0) {
                          fileExt = ".mp4";
                        }
                        
                        InputStream is = client.requestGet(rec.getDownloadLink(), rec.getContentType());

                        String numSuffix;
                        if (recList.size() > 1) {
                          numSuffix = "-" + String.valueOf(i + 1);
                        } else {
                          numSuffix = "";
                        }

                        String nodeName = meetNodeName + numSuffix + fileExt;
                        Node video;
                        Node content;
                        try {
                          video = meetFolder.getNode(nodeName);
                          content = video.getNode("jcr:content");
                        } catch (PathNotFoundException e) {
                          video = meetFolder.addNode(nodeName, "nt:file");
                          content = video.addNode("jcr:content", "nt:resource");
                        }

                        content.setProperty("jcr:mimeType", mimeType);
                        // use default calendar (w/ default tz) to show dates in server time
                        java.util.Calendar created = java.util.Calendar.getInstance();
                        created.setTime(rec.getCreatedTime());
                        content.setProperty("jcr:lastModified", created);
                        content.setProperty("jcr:data", is);
                        meetFolder.save(); // save to let actions add mixins to new folder node

                        if (recList.size() > 1) {
                          numSuffix = " " + String.valueOf(i + 1);
                        } else {
                          numSuffix = "";
                        }
                        String videoTitle = meet.getName() + numSuffix + fileExt;
                        video.setProperty("exo:title", videoTitle);
                        video.setProperty("exo:name", videoTitle);

                        // add content mixin
                        JCR.addMeetContent(video);
                        video.save();

                        // reference recordings to its meet
                        JCR.setName(video, videoTitle);
                        JCR.setMeetRef(video, meetNode.getUUID());
                        video.save();

                        videos.add(video);
                      } else {
                        LOG.warn("Ignoring empty meet recording #" + i + " for event '" + event.getSummary()
                            + "' in " + event.getCalendarId() + " (" + meet.getStartMeetUrl() + ")");
                      }
                    }

                    // reference recordings in the meetNode
                    JCR.setRecordingsRef(meetNode, videos);
                    meetNode.save();

                    if (videos.size() > 0) {
                      if (LOG.isDebugEnabled()) {
                        LOG.debug("Meet '" + event.getSummary() + "' in " + event.getCalendarId() + " ("
                            + meet.getStartMeetUrl() + ") recordings saved in node "
                            + videos.get(0).getParent().getPath());
                      }
                    } else {
                      LOG.warn("Meet recordings was empty and not saved for '" + event.getSummary() + "' in "
                          + event.getCalendarId() + " (" + meet.getStartMeetUrl() + ")");
                    }
                  } else {
                    return MoxtraMeetDownloadJob.MEET_STATUS_NOT_PROCESSED;
                  }
                } finally {
                  downloadingMeets.remove(meet.getSessionKey());
                }
              } // else, session not ended or already removed - let caller to decide
            } else {
              // else, auto-recording isn't enabled for the moment
              LOG.warn("Meet recording not enabled for '" + event.getSummary() + "' in "
                  + event.getCalendarId() + " (" + meet.getStartMeetUrl() + ")");
            }
            return meet.getStatus();
          }
        } catch (OAuthSystemException e) {
          throw new MoxtraCalendarException("Error accessing meet for event '" + event.getSummary() + "' in "
              + event.getCalendarId() + ". " + e.getMessage(), e);
        } catch (OAuthProblemException e) {
          throw new MoxtraCalendarException("Error accessing meet for event '" + event.getSummary() + "' in "
              + event.getCalendarId() + ". " + e.getMessage(), e);
        } catch (MoxtraException e) {
          throw new MoxtraCalendarException("Error downloading meet video for event '" + event.getSummary()
              + "' in " + event.getCalendarId() + ". " + e.getMessage(), e);
        } catch (UserNotFoundException e) {
          throw new MoxtraCalendarException("User not found " + userName
              + " to download meet video for event '" + event.getSummary() + "' in " + event.getCalendarId()
              + ". " + e.getMessage(), e);
        }
      } else {
        LOG.warn("Moxtra meet not enabled for given event '" + event.getSummary() + " in "
            + event.getCalendarId() + ".");
      }
    } catch (RepositoryException e) {
      if (LOG.isDebugEnabled()) {
        try {
          LOG.debug("Error saving meet recording for event '" + event.getSummary() + "' from "
              + eventNode.getName() + ". " + e.getMessage());
        } catch (RepositoryException re) {
          // ignore it
        }
      }
      throw new MoxtraCalendarException("Error reading saved meet for event '" + event.getSummary() + "' in "
          + event.getCalendarId() + ". " + e.getMessage(), e);
    }
    return null;
  }

  public void prepareJobEnvironment(JobDetail job) throws MoxtraCalendarException {
    String exoUserId = job.getJobDataMap().getString(MoxtraMeetDownloadJob.DATA_USER_ID);
    jobEnvironment.prepare(exoUserId);
  }

  public void cleanupJobEnvironment(JobDetail job) throws MoxtraCalendarException {
    String exoUserId = job.getJobDataMap().getString(MoxtraMeetDownloadJob.DATA_USER_ID);
    jobEnvironment.cleanup(exoUserId);
  }

  // ******* internals *******

  /**
   * Add given app to the context.
   */
  void initContext(MoxtraCalendarApplication app) {
    contextApp.set(app);
  }

  /**
   * Remove given app from the context.
   */
  void cleanContext(MoxtraCalendarApplication app) {
    contextApp.remove();
  }

  protected Node readEventNode(String userName, String calType, String calId, String eventId) throws MoxtraCalendarException {
    try {
      return calendar.getDataStorage().getCalendarEventNode(userName, calType, calId, eventId);
    } catch (Exception e) {
      throw new MoxtraCalendarException("Error reading event node", e);
    }
  }

  /**
   * Set required fields from the event to meet.
   * 
   * @param meet
   * @param event
   * @throws Exception
   */
  protected void initEventMeet(MoxtraMeet meet, CalendarEvent event) throws Exception {
    meet.editName(event.getSummary());
    meet.editAgenda(event.getDescription());
    meet.editStartTime(event.getFromDateTime()); // use original dates from calendar (in its tz)
    meet.editEndTime(event.getToDateTime());

    // invited eXo users: we will invite all by email, only if someone not already directly invited
    // use reminders in the task
    List<MoxtraUser> currentUsers = meet.getUsers();
    Set<String> alreadyUsers = new HashSet<String>();
    List<MoxtraUser> newUsers = new ArrayList<MoxtraUser>();
    // added eXo and Moxtra users (TODO move Moxtra users to event invitations)
    next: for (String nameOrEmail : event.getParticipant()) {
      String userId, name, email;
      User user = orgService.getUserHandler().findUserByName(nameOrEmail);
      if (user != null) {
        email = user.getEmail();
        userId = user.getUserName();
        for (MoxtraUser participant : currentUsers) {
          if (participant.getEmail().equals(email)) {
            alreadyUsers.add(email);
            continue next; // already participant
          }
        }
        name = Moxtra.fullName(user);
      } else if (nameOrEmail.indexOf('@') > 0) {
        // assume it is email
        email = name = nameOrEmail;
        userId = null;
      } else {
        // skip undetected user
        LOG.warn("Cannot recognize user " + nameOrEmail + " for Moxtra Meet. User skipped.");
        continue next;
      }
      if (userId != null) {
        newUsers.add(new MoxtraUser(userId, moxtra.getClient().getOrgId(), name, email));
      } else {
        newUsers.add(new MoxtraUser(email));
      }
    }
    // event invitations by email
    next: for (String email : event.getInvitation()) {
      for (MoxtraUser participant : currentUsers) {
        if (participant.getEmail().equals(email)) {
          alreadyUsers.add(email);
          continue next; // already participant
        }
      }
      for (MoxtraUser newUser : newUsers) {
        if (newUser.getEmail().equals(email)) {
          continue next; // already invited
        }
      }
      newUsers.add(new MoxtraUser(email));
    }
    MoxtraUser hostUser = getHostUser(meet);
    for (MoxtraUser participant : currentUsers) {
      // remove all not existing in event participants except of meet host user
      if (!alreadyUsers.contains(participant.getEmail())) {
        if (hostUser != null && hostUser.equals(participant)) {
          continue; // skip host user
        }
        meet.removeUser(participant);
      }
    }
    for (MoxtraUser newUser : newUsers) {
      meet.addUser(newUser);
    }
  }

  /**
   * Write meet's data to the node. Node will not be saved.
   * 
   * @param meetNode {@link Node}
   * @param meet {@link MoxtraMeet} meet object to save, should be an editor instance
   * @param users actual users for a new meet (as in remote meet on Moxtra)
   * @throws RepositoryException
   */
  protected void writeMeet(Node meetNode, MoxtraMeet meet, boolean isNew) throws RepositoryException {
    // TODO move this method to common place

    // object fields
    // XXX some fields may be null in some cases: e.g. end time for started meet, or agenda cannot be read
    JCR.setId(meetNode, meet.getBinderId());
    JCR.setName(meetNode, meet.getName());
    // binder fields
    JCR.setRevision(meetNode, meet.getRevision());
    JCR.setCreatedTime(meetNode, meet.getCreatedTime());
    JCR.setUpdatedTime(meetNode, meet.getUpdatedTime());
    // JCR.setThumbnailUrl(meetNode, meet.getThumbnailUrl());
    // meet fields
    JCR.setAgenda(meetNode, meet.getAgenda());
    JCR.setStartTime(meetNode, meet.getStartTime());
    Date endTime = meet.getEndTime();
    if (endTime != null) {
      JCR.setEndTime(meetNode, endTime);
    }
    JCR.setStartMeetUrl(meetNode, meet.getStartMeetUrl());
    JCR.setSessionKey(meetNode, meet.getSessionKey());
    // JCR.setSessionId(meetNode, meet.getSessionId());
    JCR.setStatus(meetNode, meet.getStatus());
    if (isNew) {
      JCR.setAutoRecording(meetNode, meet.isAutoRecording()); // using "is" for new meet
      // create local users
      Node usersNode = JCR.addUsers(meetNode);
      // add users from given list (of actual remote users)
      // XXX if meet already started (what is almost not possible - we just scheduled it), its
      // users will be empty, thus if such crap happened, we'll use local users (host user email
      // may differ in this case)
      for (MoxtraUser participant : meet.getUsers()) {
        Node pnode = usersNode.addNode(participant.getEmail());
        JCR.setId(pnode, participant.getId());
        JCR.setUniqueId(pnode, participant.getUniqueId());
        JCR.setOrgId(pnode, participant.getOrgId());
        JCR.setName(pnode, participant.getName());
        JCR.setEmail(pnode, participant.getEmail());
        JCR.setPictureUri(pnode, participant.getPictureUri());
        JCR.setType(pnode, participant.getType());
      }
    } else {
      // XXX special notion for auto-recording in updates: it may be null read from Moxtra
      // using getter not "is" for existing meet
      Boolean autorec = meet.getAutoRecording();
      if (autorec != null) {
        JCR.setAutoRecording(meetNode, autorec);
      }
      if (meet.isEditor()) {
        // update local users
        Node usersNode = JCR.getUsers(meetNode);
        if (meet.hasUsersRemoved()) {
          for (MoxtraUser removed : meet.getRemovedUsers()) {
            try {
              usersNode.getNode(removed.getEmail()).remove();
            } catch (PathNotFoundException e) {
              // already not found
            }
          }
        }
        if (meet.hasUsersAdded()) {
          for (MoxtraUser participant : meet.getAddedUsers()) {
            Node pnode = usersNode.addNode(participant.getEmail());
            JCR.setId(pnode, participant.getId());
            JCR.setUniqueId(pnode, participant.getUniqueId());
            JCR.setOrgId(pnode, participant.getOrgId());
            JCR.setName(pnode, participant.getName());
            JCR.setEmail(pnode, participant.getEmail());
            JCR.setPictureUri(pnode, participant.getPictureUri());
            JCR.setType(pnode, participant.getType());
          }
        }
      } else {
        // merge local users with users from the meet
        List<MoxtraUser> meetUsers = meet.getUsers();
        if (meetUsers.size() > 0) {
          Node usersNode = JCR.getUsers(meetNode);
          Set<String> existingEmails = new HashSet<String>();
          // add/update current meet users
          for (MoxtraUser participant : meetUsers) {
            String email = participant.getEmail();
            existingEmails.add(email);
            Node pnode;
            try {
              pnode = usersNode.getNode(email);
            } catch (PathNotFoundException e) {
              pnode = usersNode.addNode(email);
            }
            JCR.setId(pnode, participant.getId());
            JCR.setUniqueId(pnode, participant.getUniqueId());
            JCR.setOrgId(pnode, participant.getOrgId());
            JCR.setName(pnode, participant.getName());
            JCR.setEmail(pnode, email);
            JCR.setPictureUri(pnode, participant.getPictureUri());
            JCR.setType(pnode, participant.getType());
          }
          // remove not in the meet users list
          for (NodeIterator piter = usersNode.getNodes(); piter.hasNext();) {
            Node pnode = piter.nextNode();
            if (!existingEmails.contains(pnode.getName())) {
              pnode.remove();
            }
          }
        } // else, meet may have empty users only if it is started currently - ignore it
      }
    }
    // internal save time
    Date savedTime = new Date();
    JCR.setSavedTime(meetNode, savedTime);
    meet.setSavedTime(savedTime);
  }

  /**
   * Read meet from the node, refresh it with remote state, save refreshed meet in the node.
   * 
   * @param meetNode {@link Node} node where meet saved
   * @param userName {@link String} current eXo user
   * @throws RepositoryException
   * @throws MoxtraException
   * @throws OAuthProblemException
   * @throws OAuthSystemException
   * @throws MoxtraClientException
   */
  protected MoxtraMeet readMeet(Node meetNode, String userName) throws RepositoryException,
                                                               MoxtraClientException,
                                                               OAuthSystemException,
                                                               OAuthProblemException,
                                                               MoxtraException {
    // object fields
    String binderId = JCR.getId(meetNode).getString();
    String name = JCR.getName(meetNode).getString();
    // binder fields
    long revision = JCR.getRevision(meetNode).getLong();
    Date createdTime = JCR.getCreatedTime(meetNode).getDate().getTime();
    Date updatedTime = JCR.getUpdatedTime(meetNode).getDate().getTime();
    // JCR.setThumbnailUrl(meetNode, meet.getThumbnailUrl());
    // meet fields
    String agenda;
    try {
      agenda = JCR.getAgenda(meetNode).getString();
    } catch (PathNotFoundException e) {
      agenda = null;
    }
    Date startTime = JCR.getStartTime(meetNode).getDate().getTime();
    Date endTime;
    try {
      endTime = JCR.getEndTime(meetNode).getDate().getTime();
    } catch (PathNotFoundException e) {
      endTime = null;
    }
    String startMeetUrl = JCR.getStartMeetUrl(meetNode).getString();
    String sessionKey = JCR.getSessionKey(meetNode).getString();
    Boolean autorec;
    try {
      autorec = JCR.getAutoRecording(meetNode).getBoolean();
    } catch (PathNotFoundException e) {
      // can be null if such value was read from Moxtra
      autorec = null;
    }
    String status;
    try {
      status = JCR.getStatus(meetNode).getString();
    } catch (PathNotFoundException e) {
      status = null; // undefined?
    }
    // meet users
    List<MoxtraUser> users = new ArrayList<MoxtraUser>();
    Node usersNode = JCR.getUsers(meetNode);
    for (NodeIterator piter = usersNode.getNodes(); piter.hasNext();) {
      Node pnode = piter.nextNode();
      users.add(new MoxtraUser(JCR.getIdString(pnode),
                               JCR.getUniqueIdString(pnode),
                               JCR.getOrgIdString(pnode),
                               JCR.getName(pnode).getString(),
                               JCR.getEmail(pnode).getString(),
                               JCR.getPictureUriString(pnode),
                               JCR.getType(pnode).getString()));
    }

    MoxtraMeet localMeet = MoxtraMeet.create(sessionKey, //
                                             null, // sessionId,
                                             binderId,
                                             name,
                                             agenda,
                                             revision,
                                             startMeetUrl,
                                             createdTime,
                                             updatedTime,
                                             startTime,
                                             endTime,
                                             autorec,
                                             status,
                                             users);

    boolean needRefresh;
    try {
      Date savedTime = JCR.getSavedTime(meetNode).getDate().getTime();
      localMeet.setSavedTime(savedTime);
      if (localMeet.isEnded()) {
        needRefresh = (System.currentTimeMillis() - savedTime.getTime()) > MEET_ENDED_REFRESH_PERIOD;
      } else {
        needRefresh = (System.currentTimeMillis() - savedTime.getTime()) > MEET_SCHEDULED_REFRESH_PERIOD;
      }
    } catch (PathNotFoundException e) {
      needRefresh = true;
    }

    if (needRefresh) {
      MoxtraClient client = moxtra.getClient();
      if (client.isAuthorized()) {
        // if client authorized try refresh the meet from Moxtra
        try {
          if (client.isSSOAuth()) {
            // use meet owner user for refresh
            try {
              MoxtraUser meetOwner = localMeet.getOwnerUser();
              MoxtraClient ownerClient;
              if (!meetOwner.isSameIdentity(userName, client.getOrgId())) {
                ownerClient = moxtra.getClient(meetOwner.getUniqueId());
              } else {
                ownerClient = client;
              }
              ownerClient.refreshMeet(localMeet);
            } catch (MoxtraOwnerUndefinedException e) {
              LOG.warn("Cannot find meet owner: " + e.getMessage(), e);
            } catch (UserNotFoundException e) {
              LOG.warn("Error reading meet owner: " + e.getMessage(), e);
            }
          } else {
            // use current user for refreshing the meet, this may fail with MoxtraForbiddenException
            client.refreshMeet(localMeet);
          }

          // Save back to the local node under current user
          // FIXME with this logic that saved data never used for authorized users as always read from
          // the Moxtra API. Saved local meet will be shown to not authorized users.
          // Saved locally meet could be good as for caching purpose, otherwise need save only reference data
          // such as binder id and session key.
          writeMeet(meetNode, localMeet, false);
          meetNode.save();
        } catch (MoxtraForbiddenException e) {
          // user has no access to this meet - we cannot refresh
          if (LOG.isDebugEnabled()) {
            LOG.debug("Meet refresh not possible: " + e.getMessage(), e);
          } else {
            LOG.warn("Meet refresh not possible. " + e.getMessage());
          }
        }
      }
    }

    // meet recordings if available locally
    try {
      localMeet.setRecordings(JCR.getRecordings(meetNode));
    } catch (PathNotFoundException e) {
      // no recordings
    }

    return localMeet;
  }

  /**
   * Schedule a local job to download a meet video.
   * 
   * @throws Exception
   */
  protected String createDownloadJob(CalendarEvent event, MoxtraMeet meet, Node meetNode) throws Exception {
    String jobName = meetJobName(event, meet);

    // job info
    JobDetailImpl job = new JobDetailImpl();

    job.setName(jobName);
    job.setGroup(MOXTRA_DOWNLOAD_JOB_GROUP_NAME);
    job.setJobClass(MoxtraMeetDownloadJob.class);
    job.setDescription("Download meet video in job");

    String userName = Moxtra.currentUserName();

    JobDataMap jobData = job.getJobDataMap();

    jobData.put(MoxtraMeetDownloadJob.DATA_USER_ID, userName);
    jobData.put(MoxtraMeetDownloadJob.DATA_CALENDAR_TYPE, event.getCalType());
    jobData.put(MoxtraMeetDownloadJob.DATA_CALENDAR_ID, event.getCalendarId());
    jobData.put(MoxtraMeetDownloadJob.DATA_EVENT_ID, event.getId());
    jobData.put(MoxtraMeetDownloadJob.DATA_DOWNLOAD_ATTEMPTS, Long.valueOf(0l));

    // TODO cleanup not required fields
    // job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_MOXTRA_USER_ID, meet.getHostUser().getId());
    // job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_MOXTRA_USER_EMAIL, meet.getHostUser().getEmail());
    // job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_MOXTRA_BINDER_ID, meet.getBinderId());
    // job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_MOXTRA_MEET_SESSION_KEY, meet.getSessionKey());
    //
    // job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_MEET_NODE_WORKSPACE,
    // meetNode.getSession().getWorkspace().getName());
    // job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_MEET_NODE_PATH, meetNode.getPath());

    // schedule the job
    // video may be not available yet, thus job might need to be rescheduled, or need run it periodically
    SimpleTriggerImpl trigger = new SimpleTriggerImpl();
    trigger.setName(jobName);
    trigger.setGroup(MOXTRA_DOWNLOAD_JOB_GROUP_NAME);

    // will try with a delay after meet end
    // use Moxtra calendar as we use theirs end time
    java.util.Calendar downloadTime = Moxtra.getCalendar();
    downloadTime.setTime(meet.getEndTime());
    java.util.Calendar nowTime = Moxtra.getCalendar();
    if (downloadTime.before(nowTime)) {
      downloadTime = nowTime;
    }
    downloadTime.add(java.util.Calendar.MINUTE, MoxtraMeetDownloadJob.MEET_DOWNLOAD_DELAY_MINUTES);
    trigger.setStartTime(downloadTime.getTime());

    jobEnvironment.configure(userName);

    schedulerService.addJob(job, trigger);

    return jobName;
  }

  protected void updateDownloadJob(CalendarEvent event, MoxtraMeet meet) throws Exception {
    String jobName = meetJobName(event, meet);

    // new trigger with actual meet end time
    SimpleTriggerImpl trigger = new SimpleTriggerImpl();
    trigger.setName(jobName);
    trigger.setGroup(MOXTRA_DOWNLOAD_JOB_GROUP_NAME);

    java.util.Calendar downloadTime = java.util.Calendar.getInstance();
    downloadTime.setTime(meet.getEndTime());
    java.util.Calendar nowTime = Moxtra.getCalendar();
    if (downloadTime.before(nowTime)) {
      downloadTime = nowTime;
    }
    downloadTime.add(java.util.Calendar.MINUTE, MoxtraMeetDownloadJob.MEET_DOWNLOAD_DELAY_MINUTES);
    trigger.setStartTime(downloadTime.getTime());

    schedulerService.rescheduleJob(jobName, MOXTRA_DOWNLOAD_JOB_GROUP_NAME, trigger);
  }

  protected void removeDownloadJob(CalendarEvent event, MoxtraMeet meet) throws Exception {
    String jobName = meetJobName(event, meet);
    JobInfo jobInfo = new JobInfo(jobName, MOXTRA_DOWNLOAD_JOB_GROUP_NAME, MoxtraMeetDownloadJob.class);
    schedulerService.removeJob(jobInfo);
  }

  protected String meetJobName(CalendarEvent event, MoxtraMeet meet) {
    return "Meet_" + meet.getSessionKey() + "@" + event.getId() + "." + event.getCalendarId();
  }

  /**
   * Find given user Personal Documents folder using system session.
   * 
   * @param userName {@link String}
   * @return {@link Node} Personal Documents folder node or <code>null</code>
   * @throws Exception
   */
  protected Node getUserDocumentsNode(String userName) throws Exception {
    // code idea based on ECMS's UIJCRExplorerPortlet.getUserDrive()
    for (DriveData userDrive : driveService.getPersonalDrives(userName)) {
      String homePath = userDrive.getHomePath();
      if (homePath.endsWith("/Private")) {
        // using system session!
        SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
        Node userNode = hierarchyCreator.getUserNode(sessionProvider, userName);
        String driveRootPath = org.exoplatform.services.cms.impl.Utils.getPersonalDrivePath(homePath,
                                                                                            userName);
        int uhlen = userNode.getPath().length();
        if (homePath.length() > uhlen) {
          // it should be w/o leading slash, e.g. "Private"
          String driveSubPath = driveRootPath.substring(uhlen + 1);
          return userNode.getNode(driveSubPath);
        }
      }
    }
    return null;
  }

  /**
   * Find given group Documents folder using system session.
   * 
   * @param groupName {@link String}
   * @return {@link Node} space's Documents folder node or <code>null</code>
   * @throws Exception
   */
  protected Node getSpaceDocumentsNode(String userName, String groupName) throws Exception {
    // DriveData groupDrive = driveService.getDriveByName("Groups");
    String groupDriveName = groupName.replace("/", ".");
    DriveData groupDrive = driveService.getDriveByName(groupDriveName);
    if (groupDrive != null) {
      // using system session!
      SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      // we actually don't need user home node, just a JCR session
      Session session = hierarchyCreator.getUserNode(sessionProvider, userName).getSession();
      return (Node) session.getItem(groupDrive.getHomePath());
    } else {
      return null;
    }
  }

  /**
   * Get, create if not found, a meetings folder in given parent node.
   * 
   * @param parent {@link Node}
   * @param name {@link String}
   * @return {@link Node}
   * @throws RepositoryException
   */
  protected Node getMeetingsFolder(Node parent, String name) throws RepositoryException {
    Node meetings;
    try {
      meetings = parent.getNode(name);
    } catch (PathNotFoundException e) {
      meetings = parent.addNode(name, "nt:folder");
      parent.save();
    }
    return meetings;
  }

  /**
   * Find node by its UUID using system session.
   * 
   * @param userName {@link String}
   * @return {@link Node} node or <code>null</code> if node not found
   * @throws Exception
   */
  protected Node getNodeByUUID(String nodeUUID) throws Exception {
    SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
    Node userNode = hierarchyCreator.getUserNode(sessionProvider, Moxtra.currentUserName());
    try {
      return userNode.getSession().getNodeByUUID(nodeUUID);
    } catch (ItemNotFoundException e) {
      return null;
    }
  }

  protected MoxtraUser getHostUser(MoxtraMeet meet) {
    if (!meet.isNew()) {
      try {
        return meet.getHostUser();
      } catch (MoxtraException e) {
        if (LOG.isDebugEnabled()) {
          LOG.warn("Cannot get host user from meet " + meet, e);
        }
      }
    }
    return null;
  }

  protected Node saveEvenMeet(String userName,
                              MoxtraMeet meet,
                              CalendarEvent event,
                              String calType,
                              String calendarId) throws Exception {
    // TODO is it correct?
    if (calType != null) {
      event.setCalType(calType);
    }
    Node eventNode = readEventNode(userName,
                                   calType != null ? calType : event.getCalType(),
                                   calendarId != null ? calendarId : event.getCalendarId(),
                                   event.getId());
    if (eventNode != null) {
      MoxtraClient client = moxtra.getClient();
      if (meet.hasDeleted()) {
        // created new meet also can be marked as deleted (don't delete not created yet meet)
        if (!meet.isNew()) {
          boolean cancelVideoDownload = meet.isAutoRecording() || meet.hasAutoRecordingChanged();
          client.deleteMeet(meet);
          JCR.removeServices(eventNode);
          if (cancelVideoDownload) {
            // remove scheduled meet video download
            removeDownloadJob(event, meet);
          }
        } // else, meet was enabled and then disabled (marked deleted)
      } else {
        // init meet fields from the event
        initEventMeet(meet, event);
        Node meetNode;
        if (meet.isNew()) {
          // schedule the meet in Moxtra (invite participants if required)
          if (!JCR.isServices(eventNode)) {
            JCR.addServices(eventNode, userName);
          } else if (JCR.hasMeet(eventNode)) {
            // meet already created for this event
            throw new MoxtraCalendarException("Meet already created for this event " + event.getSummary());
          }
          client.createMeet(meet);
          // save local node after creating remote meet but before adding meet (mixin should be saved)
          eventNode.save();
          meetNode = JCR.addMeet(eventNode);
          // save meet using local meet editor but get actual users from read remote Moxtra
          // refresh meet object to latest data (new status will be reset!)
          client.refreshMeet(meet);
          writeMeet(meetNode, meet, true);
          if (meet.isAutoRecording()) {
            // schedule meet video download
            createDownloadJob(event, meet, meetNode);
          }
        } else {
          // Update the meet in Moxtra
          if (!JCR.isServices(eventNode)) {
            // meet not created for this event
            throw new MoxtraCalendarException("Meet not enabled for this event " + event.getSummary());
          }
          // if auto-record enabled and end time changed
          boolean updateVideoDownload = meet.isAutoRecording() && meet.hasEndTimeChanged();
          // if auto-record was disabled
          boolean cancelVideoDownload = !meet.isAutoRecording() && meet.hasAutoRecordingChanged();
          client.updateMeet(meet);
          meetNode = JCR.getMeet(eventNode);
          // update meet using local meet editor
          writeMeet(meetNode, meet, false);
          if (cancelVideoDownload) {
            // remove scheduled meet video download
            removeDownloadJob(event, meet);
          } else if (updateVideoDownload) {
            // update scheduled meet video download (for time)
            updateDownloadJob(event, meet);
          }
        }
      }

      // we don't save meet node here, the caller code have to do this!s
      return eventNode;
    } else {
      throw new MoxtraCalendarException("Event node cannot be found for user " + userName + " event "
          + event.getSummary());
    }
  }

  protected void removeMeetEvent(MoxtraMeet meet, String userId, String calendarId, CalendarEvent event) {
    // TODO meet could be created but error during users invitation
    if (meet.getSessionKey() == null) {
      // if no meet created remove the event
      try {
        calendar.removePublicEvent(calendarId, event.getId());
      } catch (Throwable re) {
        LOG.error("Error removing an event of failed to create meet. " + re.getMessage(), re);
      }
    }
  }

  protected void fireMeetRead(MoxtraMeet meet, String calendarId, CalendarEvent event) {
    for (MoxtraCalendarStateListener listener : stateListeners) {
      listener.onMeetRead(meet, calendarId, event);
    }
  }

  protected void fireMeetWrite(MoxtraMeet meet, String calendarId, CalendarEvent event) {
    for (MoxtraCalendarStateListener listener : stateListeners) {
      listener.onMeetWrite(meet, calendarId, event);
    }
  }

  protected void fireMeetDelete(MoxtraMeet meet, String calendarId, CalendarEvent event) {
    for (MoxtraCalendarStateListener listener : stateListeners) {
      listener.onMeetDelete(meet, calendarId, event);
    }
  }
}
