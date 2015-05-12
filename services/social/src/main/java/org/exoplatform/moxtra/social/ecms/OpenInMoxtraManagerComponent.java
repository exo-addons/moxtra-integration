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
package org.exoplatform.moxtra.social.ecms;

import org.exoplatform.ecm.webui.component.explorer.UIJCRExplorer;
import org.exoplatform.ecm.webui.component.explorer.UIWorkingArea;
import org.exoplatform.ecm.webui.component.explorer.control.listener.UIWorkingAreaActionListener;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.event.Event;

import javax.jcr.Node;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: OpenInMoxtraManagerComponent.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
@ComponentConfig(
                 events = { @EventConfig(
                                         listeners = OpenInMoxtraManagerComponent.OpenInMoxtraActionListener.class) })
public class OpenInMoxtraManagerComponent extends BaseMoxtraSocialDocumentManagerComponent {

  public static class OpenInMoxtraActionListener extends UIWorkingAreaActionListener<UIWorkingArea> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void processEvent(Event<UIWorkingArea> event) throws Exception {
      // TODO upload context node to context binder, wait it will be processed by Moxtra and open in JCR
      // explorer in a file view (it will be note or draw regarding the mime type in ext filter)

      UIWorkingArea uiWorkingArea = event.getSource();
      //event.getRequestContext().getAttribute(type)
      
      UIJCRExplorer uiExplorer = uiWorkingArea.getAncestorOfType(UIJCRExplorer.class);
      if (uiExplorer != null) {
        MoxtraSocialService moxtra = uiWorkingArea.getApplicationComponent(MoxtraSocialService.class);
        Node node = uiExplorer.getCurrentNode();
        
        
      } else {
        LOG.error("Cannot find ancestor of type UIJCRExplorer in component " + uiWorkingArea);
      }
    }

  }

}
