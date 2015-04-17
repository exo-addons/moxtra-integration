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
import org.exoplatform.calendar.service.CalendarService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.scheduler.impl.JobSchedulerServiceImpl;
import org.exoplatform.services.security.ConversationState;
import org.quartz.InterruptableJob;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import java.util.Calendar;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraMeetDownloadJob.java 00000 Apr 7, 2015 pnedonosko $
 * 
 */
public class MoxtraMeetDownloadJob implements Job, InterruptableJob {

  public static final String DATA_USER_ID                 = "user_id";

  public static final String DATA_CALENDAR_ID             = "calendar_id";

  public static final String DATA_EVENT_ID                = "event_id";

  public static final String DATA_MOXTRA_USER_ID          = "moxtra_user_id";

  public static final String DATA_MOXTRA_USER_EMAIL       = "moxtra_user_email";

  public static final String DATA_MOXTRA_BINDER_ID        = "moxtra_binder_id";

  public static final String DATA_MOXTRA_MEET_SESSION_KEY = "moxtra_meet_session_key";

  public static final String DATA_MEET_NODE_WORKSPACE     = "meet_node_workspace";

  public static final String DATA_MEET_NODE_PATH          = "meet_node_path";

  protected static final Log LOG                          = ExoLogger.getLogger(MoxtraMeetDownloadJob.class);

  /**
   * 
   */
  public MoxtraMeetDownloadJob() {
    // TODO Auto-generated constructor stub
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void interrupt() throws UnableToInterruptJobException {
    // TODO Auto-generated method stub

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    JobDetail job = context.getJobDetail();
    JobDataMap data = job.getJobDataMap();

    ExoContainer container = ExoContainerContext.getCurrentContainer();
    CalendarService calendar = (CalendarService) container.getComponentInstance(CalendarService.class);
    MoxtraCalendarService moxtra = (MoxtraCalendarService) container.getComponentInstance(MoxtraCalendarService.class);

    try {
      moxtra.prepareJobEnvironment(job);
      try {
        String exoUserId = data.getString(DATA_USER_ID);
        CalendarEvent event = calendar.getEvent(exoUserId, data.getString(DATA_EVENT_ID));
        String status = moxtra.downloadMeetVideo(exoUserId, event);
        if (status != null
            && (status.equals(MoxtraMeet.SESSION_SCHEDULED) || status.equals(MoxtraMeet.SESSION_STARTED))) {
          // TODO if no recordings available (or error requesting it) - wait for several minutes and
          // try again for 30-40mins more, then fail with error.
          JobSchedulerServiceImpl schedulerService = (JobSchedulerServiceImpl) container.getComponentInstance(JobSchedulerServiceImpl.class);

          String jobName = job.getKey().getName();
          String jobGroup = job.getKey().getGroup();

          // new job
          JobDetailImpl newJob = new JobDetailImpl();
          newJob.setName(jobName);
          newJob.setGroup(jobGroup);
          newJob.setJobClass(job.getJobClass());
          newJob.setDescription(newJob.getDescription());
          newJob.getJobDataMap().putAll(job.getJobDataMap());

          // schedule the new job in 5 min
          SimpleTriggerImpl trigger = new SimpleTriggerImpl();
          trigger.setName(jobName);
          trigger.setGroup(jobGroup);
          // use default calendar as we don't rely on meet end time anymore
          Calendar downloadTime = Calendar.getInstance();
          downloadTime.add(Calendar.MINUTE, 5);
          trigger.setStartTime(downloadTime.getTime());

          schedulerService.addJob(job, trigger);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Meet recordings download for event " + event.getSummary()
                + " not ready and rescheduled to " + downloadTime.getTime());
          }
        } else if (MoxtraMeet.SESSION_DELETED.equals(status)) {
          LOG.warn("Meet for event " + event.getSummary()
              + " was deleted and video recordings cannot be download");
        } else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Meet recordings download for event " + event.getSummary()
                + " canceled. See above log messages for a cause.");
          }
        }
      } finally {
        moxtra.cleanupJobEnvironment(job);
      }
    } catch (MoxtraException e) {
      throw new JobExecutionException("Error processing Moxtra meet video download", e);
    } catch (Exception e) {
      throw new JobExecutionException("Error processing Moxtra meet video download", e);
    }
  }

}
