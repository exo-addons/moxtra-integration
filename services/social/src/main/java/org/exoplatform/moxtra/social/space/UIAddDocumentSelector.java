/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.moxtra.social.space;

import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.NotFoundException;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.commons.UIDocumentSelector;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPopupComponent;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.core.UIPortletApplication;
import org.exoplatform.webui.core.lifecycle.Lifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Created by The eXo Platform SAS
 * Code adapted from Social's UIDocActivitySelector.
 */
@ComponentConfig(lifecycle = Lifecycle.class,
                 template = "classpath:templates/moxtra/social/space/UIAddDocumentSelector.gtmpl", events = {
                     @EventConfig(listeners = UIAddDocumentSelector.CancelActionListener.class),
                     @EventConfig(listeners = UIAddDocumentSelector.SelectedFileActionListener.class) }

)
public class UIAddDocumentSelector extends UIContainer implements UIPopupComponent {

  public static final String UIDOCUMENTSELECTOR = "UIDocumentSelector";

  public static final String CANCEL             = "Cancel";

  public static final String SELECTEDFILE       = "SelectedFile";

  private static final Log   LOG                = ExoLogger.getLogger(UIAddDocumentSelector.class);

  public static class CancelActionListener extends EventListener<UIAddDocumentSelector> {
    public void execute(Event<UIAddDocumentSelector> event) throws Exception {
      UIAddDocumentSelector uiDocActivitySelector = event.getSource();
      uiDocActivitySelector.deActivate();
    }
  }

  public static class SelectedFileActionListener extends EventListener<UIAddDocumentSelector> {
    public void execute(Event<UIAddDocumentSelector> event) throws Exception {
      UIAddDocumentSelector selectorContainer = event.getSource();
      UIPortletApplication uiApp = selectorContainer.getAncestorOfType(UIPortletApplication.class);
      UIDocumentSelector selector = selectorContainer.getChild(UIDocumentSelector.class);
      String rawPath = selector.getSeletedFile();
      if (rawPath == null || rawPath.trim().length() <= 0) {
        uiApp.addMessage(new ApplicationMessage("UIDocActivitySelector.msg.not-a-file",
                                                null,
                                                ApplicationMessage.WARNING));
        ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
        return;
      } else {
        MoxtraSocialService moxtra = selectorContainer.getApplicationComponent(MoxtraSocialService.class);

        String filePath = selector.getSeletedFile();
        if (filePath == null) {
          uiApp.addMessage(new ApplicationMessage("UIAddDocumentSelector.msg.exoFileRequired",
                                                  null,
                                                  ApplicationMessage.WARNING));
          ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
          return;
        }

        Item fileItem = session(selectorContainer).getItem(filePath);
        Node fileNode;
        if (fileItem.isNode()) {
          fileNode = (Node) fileItem;
        } else {
          uiApp.addMessage(new ApplicationMessage("UIAddDocumentSelector.msg.fileNotNode",
                                                  null,
                                                  ApplicationMessage.ERROR));
          ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
          return;
        }

        try {
          if (selectorContainer.sessionKey != null && selectorContainer.sessionKey.length() > 0) {
            // if session key exists it's meet (or clip but we don't support it right now)
            moxtra.uploadMeetDocument(fileNode, selectorContainer.sessionKey, selectorContainer.sessionId);
          } else {
            // otherwise we assume it's page upload to binder
            try {
              MoxtraBinder binder = moxtra.getBinder(selectorContainer.binderId);
              MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(binder);
              if (binderSpace != null) {
                if (binderSpace.hasPage(fileNode)) {
                  uiApp.addMessage(new ApplicationMessage("UIAddDocumentSelector.msg.alreadyPage",
                                                          null,
                                                          ApplicationMessage.INFO));
                  ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
                  return;
                } else {
                  binderSpace.createPage(fileNode);
                }
              } else {
                uiApp.addMessage(new ApplicationMessage("UIAddDocumentSelector.msg.binderSpaceNotFound",
                                                        null,
                                                        ApplicationMessage.ERROR));
                ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
                return;
              }
            } catch (NotFoundException e) {
              ApplicationMessage msg = new ApplicationMessage("UIAddDocumentSelector.msg.binderNotFound",
                                                              new String[] { e.getMessage() },
                                                              ApplicationMessage.ERROR);
              msg.setArgsLocalized(false);
              uiApp.addMessage(msg);
              ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
              return;
            }
          }
          selectorContainer.deActivate();
        } catch (MoxtraException e) {
          ApplicationMessage msg = new ApplicationMessage("UIAddDocumentSelector.msg.uploadError",
                                                          new String[] { e.getMessage() },
                                                          ApplicationMessage.ERROR);
          msg.setArgsLocalized(false);
          uiApp.addMessage(msg);
          ((PortalRequestContext) event.getRequestContext().getParentAppRequestContext()).ignoreAJAXUpdateOnPortlets(true);
          return;
        }
      }
    }

    protected Session session(UIComponent comp) throws RepositoryException {
      SessionProviderService sessions = comp.getApplicationComponent(SessionProviderService.class);
      RepositoryService jcr = comp.getApplicationComponent(RepositoryService.class);
      ManageableRepository repo = jcr.getCurrentRepository();
      return sessions.getSessionProvider(null).getSession(repo.getConfiguration().getDefaultWorkspaceName(),
                                                          repo);
    }
  }

  /**
   * Moxtra object (binder or meet) identifiers.
   */
  protected String binderId, sessionKey, sessionId;

  public UIAddDocumentSelector() throws Exception {
    addChild(UIDocumentSelector.class, null, UIDOCUMENTSELECTOR);
  }

  @Override
  public void activate() {

  }

  @Override
  public void deActivate() {
    UIPopupWindow popup = (UIPopupWindow) this.getParent();
    popup.setUIComponent(null);
    popup.setShow(false);
    popup.setRendered(false);
    ((WebuiRequestContext) WebuiRequestContext.getCurrentInstance()).addUIComponentToUpdateByAjax(popup.getParent());
  }

  public void init(String binderId, String sessionKey, String sessionId) {
    this.binderId = binderId;
    this.sessionKey = sessionKey;
    this.sessionId = sessionId;
  }

}
