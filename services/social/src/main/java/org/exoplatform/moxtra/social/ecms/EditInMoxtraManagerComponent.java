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

import org.exoplatform.ecm.webui.component.explorer.UIDocumentContainer;
import org.exoplatform.ecm.webui.component.explorer.UIDocumentWorkspace;
import org.exoplatform.ecm.webui.component.explorer.UIDrivesArea;
import org.exoplatform.ecm.webui.component.explorer.UIJCRExplorer;
import org.exoplatform.ecm.webui.component.explorer.UIWorkingArea;
import org.exoplatform.ecm.webui.component.explorer.control.UIControl;
import org.exoplatform.ecm.webui.component.explorer.control.listener.UIWorkingAreaActionListener;
import org.exoplatform.ecm.webui.component.explorer.search.UISearchResult;
import org.exoplatform.ecm.webui.utils.JCRExceptionManager;
import org.exoplatform.moxtra.social.MoxtraSocialException;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.web.application.Parameter;
import org.exoplatform.web.application.RequireJS;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.event.Event;

import java.util.regex.Matcher;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Session;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: EditInMoxtraManagerComponent.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
@ComponentConfig(
                 events = { @EventConfig(
                                         listeners = EditInMoxtraManagerComponent.EditInMoxtraActionListener.class) })
public class EditInMoxtraManagerComponent extends BaseMoxtraSocialDocumentManagerComponent {

  public static final String OPEN_IN_NEW_WINDOW = "openInNewWindow";

  protected static final Log LOG                = ExoLogger.getLogger(EditInMoxtraManagerComponent.class);

  public static class EditInMoxtraActionListener extends
                                                UIWorkingAreaActionListener<EditInMoxtraManagerComponent> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void processEvent(Event<EditInMoxtraManagerComponent> event) throws Exception {
      EditInMoxtraManagerComponent editAction = event.getSource();
      UIJCRExplorer uiExplorer = editAction.getAncestorOfType(UIJCRExplorer.class);
      if (uiExplorer != null) {
        UIApplication uiApp = editAction.getAncestorOfType(UIApplication.class);
        String nodePath = event.getRequestContext().getRequestParameter(OBJECTID);
        Node selectedNode = null;
        if (nodePath != null && nodePath.length() != 0) {
          Matcher matcher = UIWorkingArea.FILE_EXPLORER_URL_SYNTAX.matcher(nodePath);
          String wsName = null;
          if (matcher.find()) {
            wsName = matcher.group(1);
            nodePath = matcher.group(2);
          } else {
            throw new IllegalArgumentException("The ObjectId is invalid '" + nodePath + "'");
          }
          Session session = uiExplorer.getSessionByWorkspace(wsName);
          try {
            // Use the method getNodeByPath because it is link aware
            selectedNode = uiExplorer.getNodeByPath(nodePath, session);
          } catch (PathNotFoundException path) {
            uiApp.addMessage(new ApplicationMessage("UIPopupMenu.msg.path-not-found-exception",
                                                    null,
                                                    ApplicationMessage.WARNING));

            return;
          } catch (AccessDeniedException ace) {
            uiApp.addMessage(new ApplicationMessage("UIDocumentInfo.msg.null-exception",
                                                    null,
                                                    ApplicationMessage.WARNING));

            return;
          } catch (Exception e) {
            JCRExceptionManager.process(uiApp, e);
            return;
          }
        }

        WebuiRequestContext context = event.getRequestContext();
        UIWorkingArea uiWorkingArea = uiExplorer.getChild(UIWorkingArea.class);
        context.addUIComponentToUpdateByAjax(uiWorkingArea);

        try {
          if (selectedNode == null) {
            selectedNode = uiExplorer.getCurrentNode();
          }

          MoxtraSocialService moxtra = editAction.getApplicationComponent(MoxtraSocialService.class);
          MoxtraBinderSpace binderSpace = moxtra.getBinderSpace();
          if (binderSpace != null) {
            // if not a page yet, create a such in Moxtra
            boolean isPageCreating;
            if (!binderSpace.hasPage(selectedNode)) {
              binderSpace.createPage(selectedNode);
              isPageCreating = true;
            } else {
              isPageCreating = false;
            }

            uiExplorer.updateAjax(event);

            String editInNewWindow = event.getRequestContext().getRequestParameter(OPEN_IN_NEW_WINDOW);
            if (editInNewWindow != null && Boolean.valueOf(editInNewWindow)) {
              // TODO Moxtra Edit open in new window, we need provide a proper URL for it in this response
              RequireJS requireJS = context.getJavascriptManager().getRequireJS();
              // TODO do we really need require and initUser() here?
              requireJS.require("SHARED/exoMoxtra", "moxtra");
              requireJS.addScripts("moxtra.initUser('" + context.getRemoteUser() + "', "
                  + moxtra.isAuthorized() + ");");
              if (isPageCreating) {
                // set pageId=null and provide space name and node UUID for waiting for page creation
                requireJS.addScripts("moxtra.openPage('" + binderSpace.getBinder().getBinderId()
                    + "', null, '" + binderSpace.getSpace().getPrettyName() + "', '" + selectedNode.getUUID()
                    + "');");
              } else {
                // open existing page
                requireJS.addScripts("moxtra.openPage('" + binderSpace.getBinder().getBinderId() + "', '"
                    + binderSpace.getPage(selectedNode).getId() + "');");
              }
            } else {
              // set current node after page creation
              uiExplorer.setSelectNode(selectedNode.getPath());

              // add editor UI:
              // show UIMoxtraEditComponent (idea similar to ECMS's EditDocumentActionComponent)
              UIMoxtraEditComponent uiEditor = editAction.createUIComponent(UIMoxtraEditComponent.class,
                                                                            null,
                                                                            "EditInMoxtraComponent");

              UIDocumentWorkspace uiDocumentWorkspace = uiWorkingArea.getChild(UIDocumentWorkspace.class);
              if (!uiDocumentWorkspace.isRendered()) {
                uiWorkingArea.getChild(UIDrivesArea.class).setRendered(false);
                uiDocumentWorkspace.setRendered(true);
              }
              uiDocumentWorkspace.getChild(UIDocumentContainer.class).setRendered(false);
              uiDocumentWorkspace.getChild(UISearchResult.class).setRendered(false);
              UIMoxtraEditComponent prevEditor = uiDocumentWorkspace.removeChild(UIMoxtraEditComponent.class);
              if (prevEditor != null) {
                // TODO what we do need in the prev?
              }
              uiDocumentWorkspace.addChild(uiEditor);
              uiEditor.initMoxtra(moxtra, selectedNode.getUUID(), isPageCreating);
              uiEditor.setRendered(true);
            }

            context.addUIComponentToUpdateByAjax(uiExplorer.getChild(UIControl.class));
          } else {
            LOG.warn("No binder space emabled for space node " + selectedNode.getPath());
            uiApp.addMessage(new ApplicationMessage("Moxtra.error.noMoxraBinder",
                                                    null,
                                                    ApplicationMessage.ERROR));
          }
        } catch (MoxtraSocialException e) {
          LOG.error("Error opening file " + selectedNode.getPath() + " in Moxtra " + e.getMessage(), e);
          uiApp.addMessage(new ApplicationMessage("Moxtra.error.errorOpeningInMoxtra",
                                                  null,
                                                  ApplicationMessage.ERROR));
        }
      } else {
        LOG.error("Cannot find ancestor of type UIJCRExplorer in component " + editAction);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String renderEventURL(boolean ajax, String name, String beanId, Parameter[] params) throws Exception {
    WebuiRequestContext context = (WebuiRequestContext) WebuiRequestContext.getCurrentInstance();
    if (context.getAttribute(name) == null) {
      initContext(context, true); // editInNewWindow=true
      context.setAttribute(name, true);
    }
    // force open Moxtra editor in new window
    Parameter[] newParams;
    if (params == null) {
      newParams = new Parameter[1];
    } else {
      newParams = new Parameter[params.length + 1];
      System.arraycopy(params, 0, newParams, 0, params.length);
    }
    newParams[newParams.length - 1] = new Parameter(OPEN_IN_NEW_WINDOW, String.valueOf(true));
    return super.renderEventURL(ajax, name, beanId, newParams);
  }

  protected void initContext(WebuiRequestContext context, boolean editInNewWindow) {
    // add Moxtra JS to proceed auth if required
    MoxtraSocialService moxtra = context.getUIApplication()
                                        .getApplicationComponent(MoxtraSocialService.class);
    RequireJS requireJS = context.getJavascriptManager().getRequireJS();
    requireJS.require("SHARED/exoMoxtra", "moxtra");
    requireJS.addScripts("moxtra.initUser(\"" + context.getRemoteUser() + "\", " + moxtra.isAuthorized()
        + ");");
    requireJS.addScripts("moxtra.initDocuments(true);"); // openInNewWindow=true
  }

}
