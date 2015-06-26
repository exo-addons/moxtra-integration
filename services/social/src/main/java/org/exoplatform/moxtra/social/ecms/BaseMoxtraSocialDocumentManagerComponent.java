/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
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
package org.exoplatform.moxtra.social.ecms;

import org.exoplatform.ecm.webui.component.explorer.UIJCRExplorer;
import org.exoplatform.ecm.webui.utils.JCRExceptionManager;
import org.exoplatform.moxtra.social.MoxtraSocialException;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.web.application.RequireJS;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIPopupContainer;
import org.exoplatform.webui.ext.manager.UIAbstractManager;
import org.exoplatform.webui.ext.manager.UIAbstractManagerComponent;

/**
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: BaseMoxtraSocialDocumentManagerComponent.java 00000 Apr 28, 2015 pnedonosko $
 */
public abstract class BaseMoxtraSocialDocumentManagerComponent extends UIAbstractManagerComponent {

  public static final String MOXTRA_DOCUMENTS_CONTEXT = "__moxtra_documents_context";

  protected static final Log LOG                      = ExoLogger.getLogger(BaseMoxtraSocialDocumentManagerComponent.class);

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<? extends UIAbstractManager> getUIAbstractManagerClass() {
    return null;
  }

  protected void initContext() throws Exception {
    WebuiRequestContext context = (WebuiRequestContext) WebuiRequestContext.getCurrentInstance();
    initContext(context);
  }

  protected MoxtraBinderSpace initContext(WebuiRequestContext context) throws Exception {
    UIApplication uiApp = getAncestorOfType(UIApplication.class);
    MoxtraSocialService moxtra = getApplicationComponent(MoxtraSocialService.class);

    try {
      // add Moxtra JS to proceed auth if required
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace();
      if (binderSpace != null) {
        if (context.getAttribute(MOXTRA_DOCUMENTS_CONTEXT) == null) {
          RequireJS requireJS = context.getJavascriptManager().getRequireJS();
          requireJS.require("SHARED/exoMoxtra", "moxtra");
          if (moxtra.isAuthorized()) {
            requireJS.addScripts("moxtra.initUser(\"" + context.getRemoteUser() + "\");");
          } else {
            requireJS.addScripts("moxtra.initUser(\"" + context.getRemoteUser() + "\", \""
                + moxtra.getOAuth2Link() + "\");");
          }
          String binderId = binderSpace.getBinder().getBinderId();
          String spaceId = binderSpace.getSpace().getId();
          requireJS.addScripts("moxtra.initDocuments(\"" + spaceId + "\", \"" + binderId + "\");");

          context.setAttribute(MOXTRA_DOCUMENTS_CONTEXT, true);
        }
        return binderSpace;
      } else {
        Space space = moxtra.getContextSpace();
        if (space != null) {
          LOG.warn("Binder not enabled for " + space.getGroupId());
        } else {
          LOG.warn("No space in the context " + context.getRequestContextPath());
        }
        uiApp.addMessage(new ApplicationMessage("Moxtra.error.noMoxraBinder", null, ApplicationMessage.ERROR));
      }
    } catch (MoxtraSocialException e) {
      LOG.error("Error initializing Moxtra in space: " + moxtra.getContextSpace() + ". " + e.getMessage(), e);
      uiApp.addMessage(new ApplicationMessage("Moxtra.error.errorInitMoxtra", null, ApplicationMessage.ERROR));
    } catch (Exception e) {
      JCRExceptionManager.process(uiApp, e);
    }

    return null;
  }

  protected void initNewPage(String type, WebuiRequestContext context) throws Exception {
    UIJCRExplorer uiExplorer = getAncestorOfType(UIJCRExplorer.class);
    if (uiExplorer != null) {
      MoxtraBinderSpace binderSpace = initContext(context);
      if (binderSpace != null) {
        UIPopupContainer uiPopupContainer = uiExplorer.getChild(UIPopupContainer.class);
        UIAddMoxtraPageForm pageForm = uiExplorer.createUIComponent(UIAddMoxtraPageForm.class, null, null);
        pageForm.init(type);
        uiPopupContainer.activate(pageForm, 420, 220, false);

        context.addUIComponentToUpdateByAjax(uiPopupContainer);
      }
    } else {
      LOG.error("Cannot find ancestor of type UIJCRExplorer in component " + this);
    }
  }
}
