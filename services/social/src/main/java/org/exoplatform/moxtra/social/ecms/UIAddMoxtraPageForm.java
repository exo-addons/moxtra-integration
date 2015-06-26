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

import org.apache.commons.lang.StringUtils;
import org.exoplatform.ecm.webui.component.explorer.UIJCRExplorer;
import org.exoplatform.ecm.webui.utils.JCRExceptionManager;
import org.exoplatform.moxtra.client.MoxtraPage;
import org.exoplatform.moxtra.social.MoxtraSocialException;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.web.application.RequireJS;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIPopupComponent;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormStringInput;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeDefinition;

/**
 * Form to create documents (whiteboard, note etc.) in Moxtra and then in eXo.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIAddMoxtraPageForm.java 00000 Jun 23, 2015 pnedonosko $
 * 
 */

@ComponentConfig(lifecycle = UIFormLifecycle.class,
                 template = "classpath:templates/moxtra/social/ecms/UIAddMoxtraPageForm.gtmpl", events = {
                     @EventConfig(listeners = UIAddMoxtraPageForm.CreateActionListener.class),
                     @EventConfig(listeners = UIAddMoxtraPageForm.CancelActionListener.class,
                                  phase = Phase.DECODE) })
public class UIAddMoxtraPageForm extends UIForm implements UIPopupComponent {

  private static final Log   LOG                   = ExoLogger.getLogger(UIAddMoxtraPageForm.class);

  public static final String FIELD_TITLE_PAGE_NAME = "pageNameTitle";

  public static final String ACTION_CREATE         = "Create";

  public static final String ACTION_CANCEL         = "Cancel";

  public static class CreateActionListener extends EventListener<UIAddMoxtraPageForm> {
    public void execute(Event<UIAddMoxtraPageForm> event) throws Exception {
      UIAddMoxtraPageForm uiDocForm = event.getSource();
      UIJCRExplorer uiExplorer = uiDocForm.getAncestorOfType(UIJCRExplorer.class);
      UIApplication uiApp = uiDocForm.getAncestorOfType(UIApplication.class);
      WebuiRequestContext context = event.getRequestContext();

      // Get title and name
      String title = uiDocForm.getUIStringInput(FIELD_TITLE_PAGE_NAME).getValue();

      // Validate input
      Node parentNode = uiExplorer.getCurrentNode();

      if (uiExplorer.nodeIsLocked(parentNode)) {
        uiApp.addMessage(new ApplicationMessage("UIPopupMenu.msg.node-locked",
                                                null,
                                                ApplicationMessage.WARNING));
        event.getRequestContext().addUIComponentToUpdateByAjax(uiDocForm);
        return;
      }
      if (StringUtils.isBlank(title)) {
        uiApp.addMessage(new ApplicationMessage("UIFolderForm.msg.name-invalid",
                                                null,
                                                ApplicationMessage.WARNING));
        event.getRequestContext().addUIComponentToUpdateByAjax(uiDocForm);
        return;
      }

      try {
        MoxtraSocialService moxtra = uiDocForm.getApplicationComponent(MoxtraSocialService.class);

        // check is valid name
        String nodeName = moxtra.cleanNodeName(title);
        if (StringUtils.isEmpty(nodeName)) {
          uiApp.addMessage(new ApplicationMessage("UIFolderForm.msg.name-invalid",
                                                  null,
                                                  ApplicationMessage.WARNING));
          event.getRequestContext().addUIComponentToUpdateByAjax(uiDocForm);
          return;
        }

        MoxtraBinderSpace binderSpace = moxtra.getBinderSpace();
        if (binderSpace != null) {
          try {
            if (MoxtraPage.PAGE_TYPE_NOTE.equals(uiDocForm.pageType)) {
              title += ".html";
            } else {
              title += ".png";
            }
            String nodeUUID = binderSpace.newPage(title, uiDocForm.pageType, parentNode);

            RequireJS requireJS = context.getJavascriptManager().getRequireJS();
            requireJS.require("SHARED/exoMoxtra", "moxtra");
            if (moxtra.isAuthorized()) {
              requireJS.addScripts("moxtra.initUser(\"" + context.getRemoteUser() + "\");");
            } else {
              requireJS.addScripts("moxtra.initUser(\"" + context.getRemoteUser() + "\", \""
                  + moxtra.getOAuth2Link() + "\");");
            }
            // set pageId=null and provide space ID and node UUID for waiting for page creation
            requireJS.addScripts("moxtra.openPage(\"" + binderSpace.getBinder().getBinderId()
                + "\", null, \"" + binderSpace.getSpace().getId() + "\", \"" + nodeUUID + "\");");

            uiExplorer.updateAjax(event);
            // context.addUIComponentToUpdateByAjax(uiExplorer.getChild(UIControl.class));
          } catch (AccessDeniedException accessDeniedException) {
            uiApp.addMessage(new ApplicationMessage("UIFolderForm.msg.repository-exception-permission",
                                                    null,
                                                    ApplicationMessage.WARNING));
          } catch (ItemExistsException re) {
            uiApp.addMessage(new ApplicationMessage("UIFolderForm.msg.not-allow-sameNameSibling",
                                                    null,
                                                    ApplicationMessage.WARNING));
          } catch (RepositoryException re) {
            String key = "UIFolderForm.msg.repository-exception";
            NodeDefinition[] definitions = parentNode.getPrimaryNodeType().getChildNodeDefinitions();
            boolean isSameNameSiblingsAllowed = false;
            for (NodeDefinition def : definitions) {
              if (def.allowsSameNameSiblings()) {
                isSameNameSiblingsAllowed = true;
                break;
              }
            }
            if (parentNode.hasNode(nodeName) && !isSameNameSiblingsAllowed) {
              key = "UIFolderForm.msg.not-allow-sameNameSibling";
            }
            uiApp.addMessage(new ApplicationMessage(key, null, ApplicationMessage.WARNING));
          }
        } else {
          LOG.warn("No binder space enabled for space node " + parentNode.getPath());
          uiApp.addMessage(new ApplicationMessage("Moxtra.error.noMoxraBinder",
                                                  null,
                                                  ApplicationMessage.ERROR));
        }
      } catch (MoxtraSocialException e) {
        LOG.error("Error creating page '" + title + "' in " + parentNode.getPath() + ". " + e.getMessage(), e);
        uiApp.addMessage(new ApplicationMessage("Moxtra.error.errorOpeningInMoxtra",
                                                null,
                                                ApplicationMessage.ERROR));
      } catch (NumberFormatException nume) {
        uiApp.addMessage(new ApplicationMessage("UIFolderForm.msg.numberformat-exception",
                                                null,
                                                ApplicationMessage.WARNING));
      } catch (Exception e) {
        JCRExceptionManager.process(uiApp, e);
      }
    }
  }

  public static class CancelActionListener extends EventListener<UIAddMoxtraPageForm> {
    public void execute(Event<UIAddMoxtraPageForm> event) throws Exception {
      UIJCRExplorer uiExplorer = event.getSource().getAncestorOfType(UIJCRExplorer.class);
      uiExplorer.cancelAction();
    }
  }

  protected String pageType;

  /**
   * 
   */
  public UIAddMoxtraPageForm() {
    // Title
    UIFormStringInput titleTextBox = new UIFormStringInput(FIELD_TITLE_PAGE_NAME, FIELD_TITLE_PAGE_NAME, null);
    this.addUIFormInput(titleTextBox);

    // Set action
    this.setActions(new String[] { "Save", "Cancel" });
  }

  @Override
  public void activate() {
    // nothing
  }

  @Override
  public void deActivate() {
    // nothing
  }

  public void init(String pageType) {
    this.pageType = pageType;
  }

}
