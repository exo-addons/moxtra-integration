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
package org.exoplatform.moxtra.social.space;

import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.social.webui.composer.PopupContainer;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPortletApplication;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

/**
 * WebUI tools used by MoxtraBinderSpace portlet (juzu).<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIMoxtraBinderSpaceTools.java 00000 Jun 5, 2015 pnedonosko $
 * 
 */
@ComponentConfig(
                 template = "classpath:templates/moxtra/social/space/UIMoxtraBinderSpaceTools.gtmpl",
                 events = { @EventConfig(listeners = UIMoxtraBinderSpaceTools.AddDocumentActionListener.class) })
public class UIMoxtraBinderSpaceTools extends UIContainer {

  public static final String SESSION_ID  = "sessionId";

  public static final String SESSION_KEY = "sessionKey";

  public static final String BINDER_ID   = "binderId";

  public static class AddDocumentActionListener extends EventListener<UIMoxtraBinderSpaceTools> {
    @Override
    public void execute(Event<UIMoxtraBinderSpaceTools> event) throws Exception {
      UIMoxtraBinderSpaceTools toolsContainer = event.getSource();

      UIPortletApplication uiApp = toolsContainer.getAncestorOfType(UIPortletApplication.class);

      String sessionId = event.getRequestContext().getRequestParameter(SESSION_ID);
      String sessionKey = event.getRequestContext().getRequestParameter(SESSION_KEY);
      String binderId = event.getRequestContext().getRequestParameter(BINDER_ID);

      if (binderId == null || binderId.length() == 0 || sessionId == null || sessionId.length() == 0) {
        uiApp.addMessage(new ApplicationMessage("UIAddDocumentSelector.msg.moxtraSessionNotDefined",
                                                null,
                                                ApplicationMessage.ERROR));
        ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
        return;
      }

      PopupContainer popupContainer = toolsContainer.getAncestorOfType(UIPortletApplication.class)
                                                    .findFirstComponentOfType(PopupContainer.class);
      if (popupContainer == null) {
        popupContainer = toolsContainer.addChild(PopupContainer.class, null, null);
        event.getRequestContext().addUIComponentToUpdateByAjax(toolsContainer);
      } else {
        event.getRequestContext().addUIComponentToUpdateByAjax(popupContainer);
      }
      UIAddDocumentSelector selectorContainer = popupContainer.activate(UIAddDocumentSelector.class,
                                                                        600,
                                                                        "UIAddDocumentSelectorPopup");
      selectorContainer.init(binderId, sessionKey, sessionId);
    }
  }

  /**
   * 
   */
  public UIMoxtraBinderSpaceTools() {
  }

}
