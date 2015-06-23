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

import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.calendar.service.CalendarEvent;
import org.exoplatform.calendar.service.CalendarService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.commons.BaseMoxtraJob;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.scheduler.JobSchedulerService;
import org.exoplatform.services.scheduler.impl.JobSchedulerServiceImpl;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import java.util.Calendar;

import javax.jcr.PathNotFoundException;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraMeetDownloadJob.java 00000 Apr 7, 2015 pnedonosko $
 * 
 */
public class MoxtraMeetDownloadJob extends BaseMoxtraJob {

  public static final String DATA_CALENDAR_ID                      = "calendar_id";

  public static final String DATA_CALENDAR_TYPE                    = "calendar_type";

  public static final String DATA_EVENT_ID                         = "event_id";

  public static final String DATA_MOXTRA_MEET_SESSION_KEY          = "moxtra_meet_session_key";

  public static final String DATA_MEET_NODE_WORKSPACE              = "meet_node_workspace";

  public static final String DATA_MEET_NODE_PATH                   = "meet_node_path";

  public static final String DATA_DOWNLOAD_ATTEMPTS                = "meet_download_attempts";

  public static final long   MEET_DOWNLOAD_ATTEMPTS_MAX            = 50;

  public static final int    MEET_DOWNLOAD_DELAY_MINUTES           = 4;

  public static final int    MEET_DOWNLOAD_ATTEMPT_DELAY_MINUTES   = 7;

  /**
   * Number of attempts for not ended meet downloads. Wait 1.5 day if delay by 15min.
   */
  public static final long   MEET_DOWNLOAD_NOT_ENDED_ATTEMPTS_MAX  = 150;

  public static final int    MEET_DOWNLOAD_NOT_ENDED_DELAY_MINUTES = 15;

  public static final String MEET_STATUS_UNDEFINED                 = "MEET_STATUS_UNDEFINED";

  public static final String MEET_STATUS_NOT_PROCESSED             = "MEET_STATUS_NOT_PROCESSED";

  public static final String MEET_STATUS_DOWNLOADING               = "MEET_STATUS_DOWNLOADING";

  public static final String MEET_STATUS_DOWNLOADED                = "MEET_STATUS_DOWNLOADED";

  protected static final Log LOG                                   = ExoLogger.getLogger(MoxtraMeetDownloadJob.class);

  /**
   * 
   */
  public MoxtraMeetDownloadJob() {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void interrupt() throws UnableToInterruptJobException {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
      JobDetail job = context.getJobDetail();
      JobDataMap data = job.getJobDataMap();

      ExoContainer container = ExoContainerContext.getCurrentContainer();
      CalendarService calendar = (CalendarService) container.getComponentInstance(CalendarService.class);
      MoxtraCalendarService moxtra = (MoxtraCalendarService) container.getComponentInstance(MoxtraCalendarService.class);

      String exoUserId = data.getString(DATA_USER_ID);
      String calType = data.getString(DATA_CALENDAR_TYPE);
      String calId = data.getString(DATA_CALENDAR_ID);
      String eventId = data.getString(DATA_EVENT_ID);

      // FIXME attempts has two maximums: for error attempts and for not ended meet, in some condition they
      // can be mixed and actual delay may be less of expected (when not ended faces with net error or vice
      // versa).
      Long attempts = data.getLong(DATA_DOWNLOAD_ATTEMPTS);

      moxtra.prepareJobEnvironment(job);
      try {
        CalendarEvent event;
        try {
          if (String.valueOf(org.exoplatform.calendar.service.Calendar.TYPE_PUBLIC).equals(calType)) {
            event = calendar.getGroupEvent(calId, eventId);
          } else {
            // user calendar
            event = calendar.getEvent(exoUserId, eventId);
          }
        } catch (PathNotFoundException e) {
          LOG.warn("Meet event " + eventId + " not found in " + calId + ". Recordings download job "
              + context.getJobDetail().getKey() + " canceled due to error: " + e.getMessage());
          return;
        }
        if (event != null) {
          try {
            String status;
            try {
              status = moxtra.downloadMeetRecordings(exoUserId, event);
            } catch (MoxtraCalendarException e) {
              Throwable cause = e.getCause();
              if (cause != null && cause instanceof OAuthSystemException) {
                cause = cause.getCause();
                if (cause != null && cause.getClass().getPackage().getName().startsWith("java.net")) {
                  // XXX if OAuth system error was caused by network exception (java.net.*) then postpone the
                  // job for later time
                  if (attempts > MEET_DOWNLOAD_ATTEMPTS_MAX) {
                    LOG.error("Network error while downloading meet recordings " + cause.getMessage()
                        + ". Download canceled for " + event.getSummary() + " in " + calId + " after "
                        + attempts + " attempts.", e);
                    return;
                  }
                  attempts++;
                  Calendar downloadTime = reschedule(job,
                                                     container,
                                                     attempts,
                                                     MEET_DOWNLOAD_ATTEMPT_DELAY_MINUTES);
                  LOG.warn("Network error while downloading meet recordings " + cause.getMessage()
                      + ". Download for " + event.getSummary() + " in " + calId + " rescheduled to "
                      + downloadTime.getTime() + ", attempt #" + attempts + " from "
                      + MEET_DOWNLOAD_ATTEMPTS_MAX);
                  return;
                } else {
                  throw e;
                }
              } else {
                throw e;
              }
            }

            if (status != null) {
              if (MoxtraMeet.SESSION_SCHEDULED.equals(status) || MoxtraMeet.SESSION_STARTED.equals(status)) {
                if (attempts > MEET_DOWNLOAD_NOT_ENDED_ATTEMPTS_MAX) {
                  LOG.warn("Meet not started or not ended yet. Recordings download for event '"
                      + event.getSummary() + "' in " + calId + " canceled after " + attempts + " attempts.");
                } else {
                  attempts++;
                  Calendar downloadTime = reschedule(job,
                                                     container,
                                                     attempts,
                                                     MEET_DOWNLOAD_NOT_ENDED_DELAY_MINUTES);
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet not ended yet. Recordings download for event '" + event.getSummary()
                        + "' in " + calId + " not ready and rescheduled to " + downloadTime.getTime()
                        + ", attempt #" + attempts + " from " + MEET_DOWNLOAD_NOT_ENDED_ATTEMPTS_MAX);
                  }
                }
              } else if (MEET_STATUS_NOT_PROCESSED.equals(status)) {
                if (attempts > MEET_DOWNLOAD_ATTEMPTS_MAX) {
                  LOG.warn("Meet recordings found empty in Moxtra. " + ". Download canceled for "
                      + event.getSummary() + " in " + calId + " after " + attempts + " attempts.");
                } else {
                  attempts++;
                  Calendar downloadTime = reschedule(job,
                                                     container,
                                                     attempts,
                                                     MEET_DOWNLOAD_ATTEMPT_DELAY_MINUTES);
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet recordings for event '" + event.getSummary() + "' in " + calId
                        + " not ready (empty), download rescheduled to " + downloadTime.getTime()
                        + ", attempt #" + attempts + " from " + MEET_DOWNLOAD_ATTEMPTS_MAX);
                  }
                }
              } else if (MoxtraMeet.SESSION_DELETED.equals(status)) {
                LOG.warn("Meet for event '" + event.getSummary() + "' in " + calId
                    + " was deleted and recordings cannot be download.");
              } else if (MoxtraMeet.SESSION_ENDED.equals(status)) {
                // OK, it's job done
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Meet recordings download completed for event '" + event.getSummary() + "' in "
                      + calId + ".");
                }
              } else if (MEET_STATUS_DOWNLOADING.equals(status)) {
                // it's OK, nothing to do
                LOG.warn("Meet recordings is downloading currently for event '" + event.getSummary()
                    + "' in " + calId + ".");
              } else if (MEET_STATUS_DOWNLOADED.equals(status)) {
                // it's OK, nothing to do
                LOG.warn("Meet recordings already downloaded for event '" + event.getSummary() + "' in "
                    + calId + ".");
              } else {
                LOG.warn("Unefined meet status '" + status + "'. Meet recordings download for event "
                    + event.getSummary() + " in " + calId + " canceled.");
              }
            } else if (status == null) {
              LOG.warn("Meet status is null (auto-recording was disabled?). Meet recordings download for event "
                  + event.getSummary() + " in " + calId + " canceled.");
            }
          } catch (MoxtraException e) {
            throw new JobExecutionException("Moxtra error while processing meet recordings download for '"
                + event.getSummary() + "' in " + calId + ". " + e.getMessage(), e);
          } catch (Throwable e) {
            throw new JobExecutionException("Error processing Moxtra meet recordings download for '"
                + event.getSummary() + "' in " + calId + ". " + e.getMessage(), e);
          }
        } else {
          LOG.warn("Meet event not found in " + eventId + " not found in " + calId
              + ". Recordings download job " + context.getJobDetail().getKey()
              + " canceled. See above log messages for a cause.");
        }
      } finally {
        moxtra.cleanupJobEnvironment(job);
      }
    } catch (Throwable e) {
      throw new JobExecutionException("Error preparing Moxtra meet recordings download. " + e.getMessage(), e);
    }
  }

  protected Calendar reschedule(JobDetail job, ExoContainer container, long attempts, int forMinutes) throws Exception {
    JobSchedulerServiceImpl schedulerService = (JobSchedulerServiceImpl) container.getComponentInstance(JobSchedulerService.class);

    String jobName = job.getKey().getName();
    String jobGroup = job.getKey().getGroup();

    // new job
    JobDetailImpl newJob = new JobDetailImpl();
    newJob.setName(jobName);
    newJob.setGroup(jobGroup);
    newJob.setJobClass(job.getJobClass());
    newJob.setDescription(newJob.getDescription());
    newJob.getJobDataMap().putAll(job.getJobDataMap());
    // update incremented attempts
    newJob.getJobDataMap().put(DATA_DOWNLOAD_ATTEMPTS, attempts);

    // schedule the new job in step min
    SimpleTriggerImpl trigger = new SimpleTriggerImpl();
    trigger.setName(jobName);
    trigger.setGroup(jobGroup);
    // use default calendar as we don't rely on meet end time anymore
    Calendar downloadTime = Calendar.getInstance();
    downloadTime.add(Calendar.MINUTE, forMinutes);
    trigger.setStartTime(downloadTime.getTime());

    schedulerService.addJob(job, trigger);

    return downloadTime;
  }
}
