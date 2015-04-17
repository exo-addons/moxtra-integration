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
package org.exoplatform.moxtra.client;

import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraException;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Moxtra meet as described in <a
 * href="https://developer.moxtra.com/docs/docs-rest-api/#meet-apis">
 * their REST API</a>.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraMeet.java 00000 Feb 27, 2015 pnedonosko $
 * 
 */
public class MoxtraMeet extends MoxtraBinder {

  public static final String SESSION_SCHEDULED            = "SESSION_SCHEDULED".intern();

  public static final String SESSION_STARTED              = "SESSION_STARTED".intern();

  public static final String SESSION_ENDED                = "SESSION_ENDED".intern();

  public static final String SESSION_DELETED              = "SESSION_DELETED".intern();

  /**
   * Maximum, and default, time for starting a meet before its scheduled start time from Moxtra API.
   */
  public static final int    SESSION_START_BEFORE_MINUTES = 30;

  /**
   * Create meet object from given data. Use this method for deserealization from local storage.
   * 
   * @param sessionKey
   * @param sessionId
   * @param binderId
   * @param name
   * @param agenda
   * @param revision
   * @param startMeetUrl
   * @param createdTime
   * @param updatedTime
   * @param startTime
   * @param endTime
   * @param autoRecording
   * @return
   */
  public static MoxtraMeet create(String sessionKey,
                                  String sessionId,
                                  String binderId,
                                  String name,
                                  String agenda,
                                  Long revision,
                                  String startMeetUrl,
                                  Date createdTime,
                                  Date updatedTime,
                                  Date startTime,
                                  Date endTime,
                                  Boolean autoRecording,
                                  List<MoxtraUser> users) {
    MoxtraMeet meet = new MoxtraMeet(sessionKey,
                                     sessionId,
                                     binderId,
                                     name,
                                     agenda,
                                     revision,
                                     startMeetUrl,
                                     createdTime,
                                     updatedTime,
                                     startTime,
                                     endTime,
                                     autoRecording,
                                     null);
    meet.setUsers(users);
    return meet;
  }

  /**
   * Session key it is a meet ID also. Can be used to build a start URL https://www.moxtra.com/{session_key}.
   */
  protected String                   sessionKey;

  /**
   * Optional. For use with JS API calls when inviting users via /meets/inviteuser REST service.
   */
  protected String                   sessionId;

  protected String                   agenda;

  protected String                   startMeetUrl;

  protected Boolean                  autoRecording;

  protected Date                     startTime;

  protected Date                     endTime;

  protected String                   status;

  protected MoxtraUser               hostUser;

  /**
   * Original meet in editor instance. In other cases it is <code>null</code>.
   */
  private transient final MoxtraMeet original;

  /**
   * Editor of this meet if any was created once by {@link #editor()} method or <code>null</code>.
   */
  private transient MoxtraMeet       editor;

  /**
   * JCR workspace used to save (auto) recorded video.
   */
  private transient String           videoWorkspace;

  /**
   * JCR path used to save (auto) recorded video.
   */
  private transient String           videoPath;

  protected MoxtraMeet(String sessionKey,
                       String sessionId,
                       String binderId,
                       String name,
                       String agenda,
                       Long revision,
                       String startMeetUrl,
                       Date createdTime,
                       Date updatedTime,
                       Date startTime,
                       Date endTime,
                       Boolean autoRecording,
                       String status) {
    super(binderId, name, revision, createdTime, updatedTime);
    this.sessionKey = sessionKey;
    this.sessionId = sessionId;
    this.agenda = agenda;
    this.startMeetUrl = startMeetUrl;
    this.startTime = startTime;
    this.endTime = endTime;
    this.autoRecording = autoRecording;
    this.status = status;
    this.original = null;
  }

  /**
   * Existing meet constructor (for use with JS API session).
   * 
   * @param sessionKey
   * @param sessionId
   * @param scheduleBinderId
   * @param name
   * @param revision
   * @param startMeetUrl
   * @param createdTime
   * @param updatedTime
   */
  public MoxtraMeet(String sessionKey,
                    String sessionId,
                    String binderId,
                    String name,
                    Long revision,
                    String startMeetUrl,
                    Date createdTime,
                    Date updatedTime) {
    this(sessionKey,
         sessionId,
         binderId,
         name,
         revision,
         startMeetUrl,
         createdTime,
         updatedTime,
         null,
         null,
         false);
  }

  /**
   * Existing meet constructor (for reading from Binder REST API).
   * 
   * @param binderId
   * @param name
   * @param revision
   * @param createdTime
   * @param updatedTime
   */
  @Deprecated
  public MoxtraMeet(String binderId, String name, Long revision, Date createdTime, Date updatedTime) {
    this(null, null, binderId, name, null, revision, null, createdTime, updatedTime, null, null, false, null);
  }

  /**
   * Existing meet constructor (for returning from Schedule Meet REST API).
   * 
   * @param sessionKey
   * @param scheduleBinderId
   * @param name
   * @param agenda
   * @param revision
   * @param startMeetUrl
   * @param createdTime
   * @param updatedTime
   * @param startTime
   * @param endTime
   * @param autoRecording
   */
  @Deprecated
  public MoxtraMeet(String sessionKey,
                    String scheduleBinderId,
                    String name,
                    String agenda,
                    Long revision,
                    String startMeetUrl,
                    Date createdTime,
                    Date updatedTime,
                    Date startTime,
                    Date endTime,
                    Boolean autoRecording) {
    this(sessionKey,
         null,
         scheduleBinderId,
         name,
         agenda,
         revision,
         startMeetUrl,
         createdTime,
         updatedTime,
         startTime,
         endTime,
         autoRecording,
         null);
  }

  /**
   * New meet constructor. For creating a new meet in Moxtra.
   */
  public MoxtraMeet() {
    super();
    this.original = null;
  }

  /**
   * New meet constructor (for creation in REST API).
   * 
   * @param name
   * @param agenda
   * @param startTime
   * @param endTime
   * @param autoRecording
   */
  @Deprecated
  public MoxtraMeet(String name, String agenda, Date startTime, Date endTime, Boolean autoRecording) {
    this(null, null, null, name, agenda, null, null, null, null, startTime, endTime, autoRecording, null);
  }

  /**
   * Editor constructor.
   * 
   * @param otherMeet
   */
  protected MoxtraMeet(MoxtraMeet otherMeet) {
    super(otherMeet);
    this.sessionKey = null;
    this.sessionId = null;
    this.agenda = null;
    this.startMeetUrl = null;
    this.startTime = null;
    this.endTime = null;
    this.autoRecording = null;
    this.original = otherMeet;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    String bid = getBinderId();
    if (bid != null) {
      str.append(bid);
    }
    String id = getSessionKey();
    if (id != null) {
      str.append('[');
      str.append(id);
      str.append(']');
    }
    if (str.length() > 0) {
      str.append(' ');
    }
    str.append(getName());
    return str.toString();
  }

  /**
   * {@inheritDoc}
   */
  public MoxtraMeet editor() {
    if (editor != null) {
      return editor;
    } else if (isEditor()) {
      return this;
    } else {
      return editor = new MoxtraMeet(this);
    }
  }

  /**
   * User that hosts this meet.
   * 
   * @return {@link MoxtraUser}
   * @throws MoxtraException when owner cannot not defined
   */
  public MoxtraUser getHostUser() throws MoxtraException {
    if (hostUser != null) {
      return hostUser;
    }
    for (MoxtraUser user : getUsers()) {
      if (USER_TYPE_BOARD_OWNER.equals(user.getType())) {
        return user;
      }
    }
    throw new MoxtraException("Cannot find meet owner in participants");
  }

  /**
   * @return the sessionKey
   */
  public String getSessionKey() {
    return sessionKey != null ? sessionKey : (isEditor() ? original.getSessionKey() : null);
  }

  /**
   * @return the sessionId
   */
  public String getSessionId() {
    return sessionId != null ? sessionId : (isEditor() ? original.getSessionId() : null);
  }

  /**
   * @return the agenda
   */
  public String getAgenda() {
    return agenda != null ? agenda : (isEditor() ? original.getAgenda() : null);
  }

  /**
   * @return the startMeetUrl
   */
  public String getStartMeetUrl() {
    return startMeetUrl != null ? startMeetUrl : (isEditor() ? original.getStartMeetUrl() : null);
  }
  
  /**
   * @return the status
   */
  public String getStatus() {
    return status != null ? status : (isEditor() ? original.getStatus() : null);
  }

  /**
   * Meet's start time as scheduled. If start time was not defined then it will be set to a next minute.
   * 
   * @return the startTime
   */
  public Date getStartTime() {
    if (startTime != null) {
      return startTime;
    } else if (isEditor()) {
      return original.getStartTime();
    } else {
      return null;
    }
  }

  /**
   * Meet's end time as scheduled.
   * 
   * @return the endTime
   */
  public Date getEndTime() {
    if (this.endTime != null) {
      return this.endTime;
    } else if (isEditor()) {
      return original.getEndTime();
    } else {
      return null;
    }
  }

  /**
   * @return the autoRecording
   */
  public Boolean getAutoRecording() {
    return autoRecording != null ? autoRecording : (isEditor() ? original.getAutoRecording() : false);
  }

  /**
   * @param newAutoRecording the new autoRecording
   */
  public void editAutoRecording(Boolean newAutoRecording) {
    if (isEditor()) {
      this.autoRecording = newAutoRecording;
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  public boolean isAutoRecordingChanged() {
    if (isEditor() && this.autoRecording != null) {
      return !this.autoRecording.equals(original.getAutoRecording());
    } else {
      return false;
    }
  }

  /**
   * Set new start time. If it is after the current end time, then it will be adjusted for 30 min before the
   * end time.
   * 
   * @param newStartTime the startTime to set
   */
  public void editStartTime(Date newStartTime) {
    if (isEditor()) {
      this.startTime = newStartTime;
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  public boolean isStartTimeChanged() {
    if (isEditor() && this.startTime != null) {
      return !this.startTime.equals(original.getStartTime());
    } else {
      return false;
    }
  }

  /**
   * Set new end time. If it is before the current start time, then it will be adjusted for 30 min after the
   * start time.
   * 
   * @param newEndTime the endTime to set
   */
  public void editEndTime(Date newEndTime) {
    if (isEditor()) {
      this.endTime = newEndTime;
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  public boolean isEndTimeChanged() {
    if (isEditor() && this.endTime != null) {
      return !this.endTime.equals(original.getEndTime());
    } else {
      return false;
    }
  }

  /**
   * @param agenda the agenda to set
   */
  public void editAgenda(String newAgenda) {
    if (isEditor()) {
      this.agenda = newAgenda;
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  public boolean isAgendaChanged() {
    if (isEditor() && this.agenda != null) {
      return !this.agenda.equals(original.getAgenda());
    } else {
      return false;
    }
  }

  /**
   * @param videoWorkspace the videoWorkspace to set
   */
  public void setVideoWorkspace(String videoWorkspace) {
    this.videoWorkspace = videoWorkspace;
  }

  /**
   * @param videoPath the videoPath to set
   */
  public void setVideoPath(String videoPath) {
    this.videoPath = videoPath;
  }

  /**
   * @return the videoWorkspace, can be <code>null</code>
   */
  public String getVideoWorkspace() {
    return videoWorkspace;
  }

  /**
   * @return the videoPath, can be <code>null</code>
   */
  public String getVideoPath() {
    return videoPath;
  }

  // ******* internals *******

  /**
   * @param hostUser the hostUser to set
   */
  protected void setHostUser(MoxtraUser hostUser) {
    this.hostUser = hostUser;
  }

  /**
   * @param autoRecording the autoRecording to set
   */
  protected void setAutoRecording(Boolean autoRecording) {
    if (isEditor()) {
      this.original.setAutoRecording(autoRecording);
      this.autoRecording = null;
    } else {
      this.autoRecording = autoRecording;
    }
  }

  /**
   * @param startTime the startTime to set
   */
  protected void setStartTime(Date startTime) {
    if (isEditor()) {
      this.original.setStartTime(startTime);
      this.startTime = null;
    } else {
      this.startTime = startTime;
    }
  }

  /**
   * @param endTime the endTime to set
   */
  protected void setEndTime(Date endTime) {
    if (isEditor()) {
      this.original.setEndTime(endTime);
      this.endTime = null;
    } else {
      this.endTime = endTime;
    }
  }

  /**
   * @param agenda the agenda to set
   */
  protected void setAgenda(String agenda) {
    if (isEditor()) {
      this.original.setAgenda(agenda);
      this.agenda = null;
    } else {
      this.agenda = agenda;
    }
  }

  /**
   * @param sessionKey the sessionKey to set
   */
  protected void setSessionKey(String sessionKey) {
    if (isEditor()) {
      this.original.setSessionKey(sessionKey);
      this.sessionKey = null;
    } else {
      this.sessionKey = sessionKey;
    }
  }

  /**
   * @param sessionId the sessionId to set
   */
  protected void setSessionId(String sessionId) {
    if (isEditor()) {
      this.original.setSessionId(sessionId);
      this.sessionId = null;
    } else {
      this.sessionId = sessionId;
    }
  }

  /**
   * @param startMeetUrl the startMeetUrl to set
   */
  protected void setStartMeetUrl(String startMeetUrl) {
    if (isEditor()) {
      this.original.setStartMeetUrl(startMeetUrl);
      this.startMeetUrl = null;
    } else {
      this.startMeetUrl = startMeetUrl;
    }
  }

  /**
   * @param status the status to set
   */
  protected void setStatus(String status) {
    if (isEditor()) {
      this.original.setStatus(status);
      this.status = null;
    } else {
      this.status = status;
    }
  }

  /**
   * Check and align if required the time for start and end to ensure they are in natural order (end after the
   * start).
   */
  protected void checkTime() {
    // FIXME such "artificial" logic may confuse an user if one of time fields will not be set properly
    // consider for UI level validation if required
    Date startTime = getStartTime();
    Date endTime = getEndTime();
    if (startTime == null && endTime != null) {
      // generate start time as 30 min before the end time
      Calendar start = Moxtra.getCalendar();
      start.setTime(endTime);
      start.add(Calendar.MINUTE, -30);
      this.startTime = start.getTime();
    } else if (startTime != null
        && (endTime == null || endTime.before(startTime) || endTime.equals(startTime))) {
      // generate end time as 30 min after the start time
      Calendar end = Moxtra.getCalendar();
      end.setTime(startTime);
      end.add(Calendar.MINUTE, 30);
      this.endTime = end.getTime();
    }
  }

}
