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
package org.exoplatform.moxtra.commons;

import org.quartz.InterruptableJob;
import org.quartz.Job;

/**
 * Basic class for Moxtra related jobs in eXo scheduler.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: BaseMoxtraJob.java 00000 Jun 19, 2015 pnedonosko $
 * 
 */
public abstract class BaseMoxtraJob implements Job, InterruptableJob {

  public static final String DATA_USER_ID                 = "user_id";

  public static final String DATA_GROUP_ID                = "group_id";

  public static final String DATA_MOXTRA_USER_ID          = "moxtra_user_id";

  public static final String DATA_MOXTRA_USER_EMAIL       = "moxtra_user_email";

  public static final String DATA_MOXTRA_BINDER_ID        = "moxtra_binder_id";

  /**
   * 
   */
  public BaseMoxtraJob() {
  }
}
