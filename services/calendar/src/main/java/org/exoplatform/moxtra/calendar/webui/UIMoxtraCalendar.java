/**
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 **/
package org.exoplatform.moxtra.calendar.webui;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.core.UIContainer;

/**
 * WebUI support for Moxtra Calendar integration.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIMoxtraCalendar.java 00000 Mar 2, 2015 pnedonosko $
 */
@ComponentConfig(
    template = "classpath:templates/calendar/webui/UIMoxtraCalendar.gtmpl"
    )
@Deprecated
public class UIMoxtraCalendar extends UIContainer {

  private static final Log   LOG         = ExoLogger.getExoLogger(UIMoxtraCalendar.class);

  public UIMoxtraCalendar(String id) {
    setComponentConfig(getClass(), null);
  }
}
