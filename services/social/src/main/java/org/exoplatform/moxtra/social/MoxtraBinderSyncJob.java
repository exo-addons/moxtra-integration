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
package org.exoplatform.moxtra.social;

import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.commons.BaseMoxtraJob;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraBinderSyncJob.java 00000 Apr 7, 2015 pnedonosko $
 * 
 */
public class MoxtraBinderSyncJob extends BaseMoxtraJob {

  public static final String DATA_SYNC_ATTEMPTS           = "binder_sync_attempts";

  public static final String DATA_BINDER_ID               = "binder_id";

  public static final long   SYNC_ATTEMPTS_MAX            = 10;

  public static final int    SYNC_DELAY_MINUTES           = 3;

  public static final int    SYNC_INTERVAL_MS             = 90000;                                         // 1.5min

  public static final int    SYNC_RESCHEDULE_THRESHOLD_MS = SYNC_INTERVAL_MS * 5;

  public static final int    SYNC_PERIOD_MINUTES          = 30;

  public static final int    SYNC_ATTEMPT_DELAY_MINUTES   = 7;

  protected static final Log LOG                          = ExoLogger.getLogger(MoxtraBinderSyncJob.class);

  /**
   * 
   */
  public MoxtraBinderSyncJob() {
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
    JobDetail job = context.getJobDetail();
    JobDataMap data = job.getJobDataMap();

    ExoContainer container = ExoContainerContext.getCurrentContainer();
    MoxtraSocialService moxtra = (MoxtraSocialService) container.getComponentInstance(MoxtraSocialService.class);

    try {
      moxtra.prepareJobEnvironment(job);
      try {
        String spaceId = data.getString(DATA_SPACE_ID);
        // binderId and groupId for information purpose only (logs)
        String binderId = data.getString(DATA_BINDER_ID);
        String groupId = data.getString(DATA_GROUP_ID);
        try {
          MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(spaceId);
          if (binderSpace != null) {
            binderSpace.syncPages();
          } else {
            LOG.warn("Binder not enabled for space " + groupId + ", job canceled.");
            cancel(context);
          }
        } catch (MoxtraSocialException e) {
          Throwable cause = e.getCause();
          if (cause != null && cause instanceof OAuthSystemException) {
            cause = cause.getCause();
            if (cause != null && cause.getClass().getPackage().getName().startsWith("java.net")) {
              // XXX if OAuth system error was caused by network exception (java.net.*) then postpone the
              // job for later time
              LOG.warn("Network error while synchronizing binder space. " + cause.getMessage()
                  + ". Will try sync " + binderId + "@" + groupId + " next time");
            } else {
              throw e;
            }
          } else {
            throw e;
          }
        }
      } finally {
        moxtra.cleanupJobEnvironment(job);
      }
    } catch (MoxtraException e) {
      cancel(context);
      throw new JobExecutionException("Moxtra error while processing binder space synchronization", e);
    } catch (Throwable e) {
      cancel(context);
      throw new JobExecutionException("Error processing Moxtra binder space synchronization", e);
    }
  }

  protected void cancel(JobExecutionContext context) {
    try {
      context.getScheduler().deleteJob(context.getJobDetail().getKey());
    } catch (SchedulerException e) {
      LOG.error("Error canceling the job " + context.getJobDetail().getKey());
    }
  }
}
