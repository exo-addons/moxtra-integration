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

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.form.UIForm;

/**
 * This component used as a config with action listeners that should be added to original UIQuickAddEvent from
 * calendar webapp .<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIQuickAddEvent.java 00000 Mar 31, 2015 pnedonosko $
 * 
 */
@ComponentConfig(events = { @EventConfig(listeners = UIQuickAddEvent.MoreDetailActionListener.class, phase = Phase.DECODE) })
public class UIQuickAddEvent extends UIForm {

  private static final Log LOG = ExoLogger.getExoLogger(UIQuickAddEvent.class);

  public static class MoreDetailActionListener extends EventListener<UIForm> {
    public void execute(Event<UIForm> event) throws Exception {
      // TODO if meet was enabled in quick-add form, transfer it to a details form in UIEmeetingTab
      UIForm uiForm = event.getSource();

      String objId = event.getRequestContext().getRequestParameter(OBJECTID);
      // int index = Integer.parseInt(indexParam);

      if (LOG.isDebugEnabled()) {
        LOG.debug("> MoreDetailAction " + uiForm + " " + objId);
      }
    }
  }

  /**
   * 
   */
  public UIQuickAddEvent() {
  }

}
