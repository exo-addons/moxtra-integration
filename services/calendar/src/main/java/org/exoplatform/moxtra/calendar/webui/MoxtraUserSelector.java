/**
 * Copyright (C) 2009 eXo Platform SAS.
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

import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.calendar.MoxtraCalendarApplication;
import org.exoplatform.moxtra.calendar.MoxtraCalendarException;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.webui.core.UIComponent;

/**
 * Created by The eXo Platform SAS
 */
public interface MoxtraUserSelector {
  public static final String FIELD_KEYWORD = "Quick Search";

  public static final String FIELD_FILTER  = "filter";

  public static final String USER_NAME     = "name";

  public static final String EMAIL         = "email";

  void init(MoxtraCalendarApplication moxtra) throws MoxtraCalendarException,
                                             MoxtraException,
                                             MoxtraConfigurationException;

  <T extends UIComponent> T getComponent();

}
