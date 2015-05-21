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
import org.exoplatform.calendar.service.Utils;
import org.exoplatform.calendar.service.impl.CalendarServiceImpl;
import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraMeetRecording;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.commons.BaseMoxtraService;
import org.exoplatform.moxtra.jcr.JCR;
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
import org.quartz.JobDetail;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

  public static final String MEET_VIDEO_DOWNLOAD_JOB_GROUP_NAME = "moxtra_meet_download";

  protected static final Log LOG                                = ExoLogger.getLogger(MoxtraCalendarService.class);

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

  public CalendarEvent getEvent(String eventId) throws Exception {
    return calendar.getEventById(eventId);
  }

  public CalendarEvent getGroupEvent(String eventId) throws Exception {
    return calendar.getGroupEvent(eventId);
  }

  public CalendarSetting getCalendarSetting() throws Exception {
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    return calendar.getCalendarSetting(userName);
  }

  public MoxtraMeet getMeet(CalendarEvent event) throws MoxtraCalendarException {
    try {
      String userName = ConversationState.getCurrent().getIdentity().getUserId();
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
          existing = readMeet(meetNode);
          // TODO merge local and remote meet data (as local has fields not available when reading from
          // Moxtra)
          // MoxtraClient moxtra = moxtra.getClient();
          // if (moxtra.isAuthorized()) {
          // existing = moxtra.getClient().getMeet(existing.getBinderId());
          // }
        } catch (PathNotFoundException e) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Meet not found or not complete for event " + event.getSummary() + " in "
                + eventNode.getName() + ". " + e.getMessage());
            // e.printStackTrace();
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
          String userName = ConversationState.getCurrent().getIdentity().getUserId();
          try {
            // FYI How it works:
            // Take meet from the context app, for new and updated events, submit it to Moxtra and the save it
            // in the event node on the end.
            MoxtraMeet meet = app.getMeet();
            String calType = app.getCalendarType(); // use cal type set in event form
            // XXX we assume that calendar id match here for fired event and event of the
            // associated form in the app
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
                    JCR.addServices(eventNode);
                  } else if (JCR.hasMeet(eventNode)) {
                    // meet already created for this event
                    throw new MoxtraCalendarException("Meet already created for this event "
                        + event.getSummary());
                  }
                  client.createMeet(meet);
                  // save local node after creating remote meet but before adding meet (mixin should be saved)
                  eventNode.save();
                  meetNode = JCR.addMeet(eventNode);
                  // save meet using local meet editor but get actual users from read remote Moxtra
                  // XXX Here we getting only actual users, remote meet cannot be used for
                  // other data (see method's Javadoc) - it is OK.
                  MoxtraMeet remoteMeet = client.getMeet(meet.getSessionKey());
                  writeMeet(meetNode, meet, remoteMeet.getUsers());
                  if (meet.isAutoRecording()) {
                    // schedule meet video download
                    createDownloadJob(event, meet, meetNode, null);
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
                  writeMeet(meetNode, meet, null);
                  if (updateVideoDownload) {
                    // update scheduled meet video download (for time)
                    updateDownloadJob(event, meet);
                  }
                  if (cancelVideoDownload) {
                    // remove scheduled meet video download
                    removeDownloadJob(event, meet);
                  }
                }
              }

              // save meet to the event node in Calendar storage
              eventNode.save();
            } else {
              LOG.error("Event node cannot be found for user " + userName + " event " + event.getSummary());
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
   * Delete Meet for given Event or Task. This method doesn't use context app and its meet, instead it assumes
   * that given event exists and meet object can be read from it. In any case context app will be reset on the
   * end.<br>
   * 
   * @param calendarId
   * @param event
   */
  @Deprecated
  // NOT USED
  public void deleteMeet(String calendarId, CalendarEvent event) {
    MoxtraCalendarApplication app = contextApp.get();
    if (app != null) {
      contextApp.remove();
      try {
        // here we handle event deletion with existing meet
        MoxtraMeet meet = getMeet(event);
        if (meet != null) {
          moxtra.getClient().deleteMeet(meet);
        } else {
          LOG.error("Cannot delete event meet " + event.getSummary() + ", it is not found in calendar "
              + calendarId);
        }
      } catch (MoxtraCalendarException e) {
        LOG.error("Error deleting event meet", e);
      } catch (MoxtraClientException e) {
        LOG.error("Error deleting event meet", e);
      } catch (MoxtraException e) {
        LOG.error("Error deleting event meet", e);
      } catch (OAuthSystemException e) {
        LOG.error("Cannot delete event meet due to authorization error", e);
      } catch (OAuthProblemException e) {
        LOG.error("Cannot delete event meet due to authorization failure", e);
      } finally {
        app.reset(); // reset the app
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
            moxtra.getClient().deleteMeet(app.getMeet());
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
    Node eventNode = readEventNode(userName, event.getCalType(), event.getCalendarId(), event.getId());
    try {
      if (JCR.isServices(eventNode)) {
        try {
          // read meet from eventNode
          Node meetNode = JCR.getMeet(eventNode);
          // read locally stored meet
          MoxtraMeet meet = readMeet(meetNode);
          if (meet.isAutoRecording()) {
            // check meet status
            if (MoxtraMeet.SESSION_ENDED.equals(meet.getStatus())) {
              // meet session already finished - we can try to download the video
              // read remote meet for recent data (like download link)
              MoxtraClient client = moxtra.getClient();
              List<MoxtraMeetRecording> recs = client.getMeetRecordings(meet);
              if (recs.size() > 0) {
                MoxtraMeetRecording rec = recs.get(0); // FIXME only first now
                if (rec.getContentLength() > 0) {
                  // find destination node
                  Node meetings;
                  // TODO use workspace also
                  String destPath = meet.getVideoPath();
                  if (destPath == null) {
                    // apply defaults:
                    // * space's documents with folder 'Meetings/${EVENT_NAME}'
                    // * user's Personal Documents subfolder 'My Meetings/${EVENT_NAME}' for personal
                    // calendar and others
                    try {
                      Calendar spaceCal = calendar.getGroupCalendar(event.getCalendarId());
                      // spaceCal.getCalendarPath(); // TODO can use it?
                      Node documents = getSpaceDocumentsNode(userName, spaceCal.getCalendarOwner());
                      if (documents != null) {
                        meetings = getMeetingsFolder(documents, "Meetings");
                      } else {
                        throw new MoxtraCalendarException("Unable to save meet video recordings for event "
                            + event.getSummary() + ": cannot find Documents for space "
                            + spaceCal.getCalendarOwner());
                      }
                    } catch (Exception ce) {
                      // XXX if exception then not a space calendar
                      if (LOG.isDebugEnabled()) {
                        LOG.debug("Error getting " + event.getSummary() + " calendar. " + ce.getMessage());
                      }
                      try {
                        Node documents = getUserDocumentsNode(userName);
                        if (documents != null) {
                          meetings = getMeetingsFolder(documents, "My Meetings");
                        } else {
                          throw new MoxtraCalendarException("Unable to save meet video recordings for event "
                              + event.getSummary() + ": cannot find Personal Documents for user " + userName);
                        }
                      } catch (Exception he) {
                        throw new MoxtraCalendarException("Error creating meetings folder in user home "
                            + userName + ". " + he.getMessage(), he);
                      }
                    }
                  } else {
                    meetings = (Node) eventNode.getSession().getItem(destPath);
                  }

                  // add this event meet folder in meetings
                  String meetNodeName = Text.escapeIllegalJcrChars(meet.getName());
                  Node meetFolder = meetings.addNode(meetNodeName, "nt:folder");
                  meetings.save(); // save to let actions add mixins
                  meetFolder.setProperty("exo:title", meet.getName());
                  meetFolder.setProperty("exo:name", meet.getName());

                  String mimeType = rec.getContentType();
                  String fileExt = mimetypeResolver.getExtension(mimeType);
                  fileExt = (fileExt.length() > 0 ? "." + fileExt : fileExt);

                  InputStream is = client.requestGet(rec.getDownloadLink(), rec.getContentType());
                  Node video = meetFolder.addNode(meetNodeName + fileExt, "nt:file");
                  Node content = video.addNode("jcr:content", "nt:resource");
                  content.setProperty("jcr:mimeType", mimeType);
                  // use default calendar (w/ default tz) to show dates in server time
                  java.util.Calendar created = java.util.Calendar.getInstance();
                  created.setTime(rec.getCreatedTime());
                  content.setProperty("jcr:lastModified", created);
                  content.setProperty("jcr:data", is);

                  meetings.save(); // save and then set exo title/name
                  String videoTitle = meet.getName() + fileExt;
                  meetFolder.setProperty("exo:title", videoTitle);
                  meetFolder.setProperty("exo:name", videoTitle);
                } else {
                  LOG.warn("Moxtra meet recording empty for given event '" + event.getSummary() + "' ("
                      + event.getId() + ")");
                  return null;
                }
              }
            } // else, session not ended or already removed - let caller to decide
            return meet.getStatus();
          }
        } catch (OAuthSystemException e) {
          throw new MoxtraCalendarException("Error accessing meet for event " + event.getSummary() + ". "
              + e.getMessage(), e);
        } catch (OAuthProblemException e) {
          throw new MoxtraCalendarException("Error accessing meet for event " + event.getSummary() + ". "
              + e.getMessage(), e);
        } catch (MoxtraException e) {
          throw new MoxtraCalendarException("Error downloading meet video for event " + event.getSummary()
              + ". " + e.getMessage(), e);
        }
      } else {
        LOG.warn("Moxtra meet not enabled for given event '" + event.getSummary() + "' (" + event.getId()
            + ")");
      }
    } catch (RepositoryException e) {
      if (LOG.isDebugEnabled()) {
        try {
          LOG.debug("Error reading saved meet for event " + event.getSummary() + " in " + eventNode.getName()
              + ". " + e.getMessage());
        } catch (RepositoryException re) {
          // ignore it
        }
      }
      throw new MoxtraCalendarException("Error reading saved meet for event " + event.getSummary() + ". "
          + e.getMessage(), e);
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
      String name, email;
      User user = orgService.getUserHandler().findUserByName(nameOrEmail);
      if (user != null) {
        email = user.getEmail();
        for (MoxtraUser participant : currentUsers) {
          if (participant.getEmail().equals(email)) {
            alreadyUsers.add(email);
            continue next; // already participant
          }
        }
        name = user.getDisplayName();
        if (name == null) {
          name = user.getFirstName() + " " + user.getLastName();
          if (name.trim().length() == 0) {
            name = email;
          }
        }
      } else if (nameOrEmail.indexOf('@') > 0) {
        // assume it is email
        email = name = nameOrEmail;
      } else {
        // skip undetected user
        LOG.warn("Cannot recognize user " + nameOrEmail + " for Moxtra Meet. User skipped.");
        continue next;
      }
      newUsers.add(new MoxtraUser(name, email));
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
  protected void writeMeet(Node meetNode, MoxtraMeet meet, List<MoxtraUser> users) throws RepositoryException {
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
    if (meet.isNew()) {
      JCR.setAutoRecording(meetNode, meet.isAutoRecording()); // using "is" for new meet
      // create local users
      Node usersNode = JCR.addUsers(meetNode);
      // add users from given list (of actual remote users)
      // XXX if meet already started (what is almost not possible - we just scheduled it), its
      // users will be empty, thus if such crap happened, we'll use local users (host user email
      // may differ in this case)
      for (MoxtraUser participant : users.size() > 0 ? users : meet.getUsers()) {
        Node pnode = usersNode.addNode(participant.getEmail());
        JCR.setId(pnode, participant.getId());
        JCR.setName(pnode, participant.getName());
        JCR.setEmail(pnode, participant.getEmail());
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
            JCR.setName(pnode, participant.getName());
            JCR.setEmail(pnode, participant.getEmail());
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
            JCR.setName(pnode, participant.getName());
            JCR.setEmail(pnode, email);
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
  }

  /**
   * Read meet from the node, refresh it with remote state, save refreshed meet in the node.
   * 
   * @param meetNode
   * @throws RepositoryException
   * @throws MoxtraException
   * @throws OAuthProblemException
   * @throws OAuthSystemException
   * @throws MoxtraClientException
   */
  protected MoxtraMeet readMeet(Node meetNode) throws RepositoryException,
                                              MoxtraClientException,
                                              OAuthSystemException,
                                              OAuthProblemException,
                                              MoxtraException {
    // TODO move this method to common place

    // TODO it's actually not required to read all fields before refreshing with Moxtra, need only such that
    // will be used to get the meet from remote services (sessionKey, binderId)

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
    // JCR.setSessionId(meetNode, meet.getSessionId());
    // meet users
    List<MoxtraUser> users = new ArrayList<MoxtraUser>();
    Node usersNode = JCR.getUsers(meetNode);
    for (NodeIterator piter = usersNode.getNodes(); piter.hasNext();) {
      Node pnode = piter.nextNode();
      users.add(new MoxtraUser(JCR.getId(pnode).getString(),
                               JCR.getName(pnode).getString(),
                               JCR.getEmail(pnode).getString(),
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
                                             users);

    MoxtraClient client = moxtra.getClient();
    if (client.isAuthorized()) {
      client.refreshMeet(localMeet);

      // Save back to the local node
      // FIXME with this logic that saved data never used for authorized users as always read from
      // the Moxtra API. Saved local meet will be shown to not authorized users.
      // Saved locally meet could be good as for caching purpose, otherwise need save only reference data such
      // as binder id and session key.
      writeMeet(meetNode, localMeet, localMeet.getUsers());
    }
    return localMeet;
  }

  /**
   * Schedule a local job to download a meet video.
   * 
   * @throws Exception
   */
  protected String createDownloadJob(CalendarEvent event, MoxtraMeet meet, Node meetNode, Date jobTime) throws Exception {
    String jobName = meetJobName(event, meet);

    // job info
    JobDetailImpl job = new JobDetailImpl();

    job.setName(jobName);
    job.setGroup(MEET_VIDEO_DOWNLOAD_JOB_GROUP_NAME);
    job.setJobClass(MoxtraMeetDownloadJob.class);
    job.setDescription("Download meet video in job");

    String userName = ConversationState.getCurrent().getIdentity().getUserId();

    job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_USER_ID, userName);
    job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_CALENDAR_ID, event.getCalendarId());
    job.getJobDataMap().put(MoxtraMeetDownloadJob.DATA_EVENT_ID, event.getId());

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
    // TODO video may be not available yet, thus job might need to be rescheduled, or need run it periodically
    SimpleTriggerImpl trigger = new SimpleTriggerImpl();
    trigger.setName(jobName);
    trigger.setGroup(MEET_VIDEO_DOWNLOAD_JOB_GROUP_NAME);

    if (jobTime != null) {
      // use given time
      trigger.setStartTime(jobTime);
    } else {
      // will try with a delay after meet end
      // use Moxtra calendar as we use theirs end time
      java.util.Calendar downloadTime = Moxtra.getCalendar();
      downloadTime.setTime(meet.getEndTime());
      // TODO add 20 to be sure video is ready on Moxtra: more robust algo to do not wait too much
      downloadTime.add(java.util.Calendar.MINUTE, 7);
      trigger.setStartTime(downloadTime.getTime());
    }

    jobEnvironment.configure(userName);

    schedulerService.addJob(job, trigger);

    return jobName;
  }

  protected void updateDownloadJob(CalendarEvent event, MoxtraMeet meet) throws Exception {
    String jobName = meetJobName(event, meet);

    // new trigger with actual meet end time
    SimpleTriggerImpl trigger = new SimpleTriggerImpl();
    trigger.setName(jobName);
    trigger.setGroup(MEET_VIDEO_DOWNLOAD_JOB_GROUP_NAME);

    java.util.Calendar downloadTime = java.util.Calendar.getInstance();
    downloadTime.setTime(meet.getEndTime());
    // TODO add 20 to be sure video is ready on Moxtra: more robust algo to do not wait
    downloadTime.add(java.util.Calendar.MINUTE, 7);
    trigger.setStartTime(downloadTime.getTime());

    schedulerService.rescheduleJob(jobName, MEET_VIDEO_DOWNLOAD_JOB_GROUP_NAME, trigger);
  }

  protected void removeDownloadJob(CalendarEvent event, MoxtraMeet meet) throws Exception {
    String jobName = meetJobName(event, meet);
    JobInfo jobInfo = new JobInfo(jobName, MEET_VIDEO_DOWNLOAD_JOB_GROUP_NAME, MoxtraMeetDownloadJob.class);
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
      if (userDrive.getHomePath().endsWith("/Private")) {
        // using system session!
        SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
        Node userNode = hierarchyCreator.getUserNode(sessionProvider, userName);
        String driveRootPath = org.exoplatform.services.cms.impl.Utils.getPersonalDrivePath(homePath,
                                                                                            userName);
        String driveSubPath = driveRootPath.substring(userNode.getPath().length());
        return userNode.getNode(driveSubPath);
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
    DriveData groupDrive = driveService.getDriveByName(groupName);
    // using system session!
    SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
    Session session = hierarchyCreator.getUserNode(sessionProvider, userName).getSession();
    return (Node) session.getItem(groupDrive.getHomePath() + "/Documents");
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
   * TODO NOT USED. Java {@link java.util.Calendar} instance with Moxtra timezone and other user settings from
   * eXo Calendar.
   * 
   * @return {@link java.util.Calendar}
   * @throws Exception
   */
  @Deprecated
  protected java.util.Calendar getUserMoxtraCalendar() throws Exception {
    CalendarSetting setting = getCalendarSetting();
    java.util.Calendar calendar = Moxtra.getCalendar();
    calendar.setLenient(false);
    calendar.setFirstDayOfWeek(Integer.parseInt(setting.getWeekStartOn()));
    calendar.setMinimalDaysInFirstWeek(4);
    return calendar;
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
}
