/*
 * Copyright (C) 2003-2014 eXo Platform SAS.
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
package org.exoplatform.moxtra.social.portlet;

import juzu.Action;
import juzu.Path;
import juzu.Resource;
import juzu.Response;
import juzu.SessionScoped;
import juzu.View;

import org.exoplatform.commons.juzu.ajax.Ajax;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.portlet.PortletPreferences;

/**
 * Juzu controller for Moxtra Binder Space app.<br>
 * 
 * Created by The eXo Platform SAS<br>
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraBinderSpaceController.java 00000 May 13, 2015 pnedonosko $
 * 
 */
@SessionScoped
public class MoxtraBinderSpaceController {

  private static final Log                                      LOG = ExoLogger.getLogger(MoxtraBinderSpaceController.class);

  @Inject
  Provider<PortletPreferences>                                  preferences;

  @Inject
  MoxtraSocialService                                           moxtra;

  @Inject
  MoxtraBinderSpaceContext                                      context;

  @Inject
  @Path("index.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.index         index;

  @Inject
  @Path("currentBinder.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.currentBinder currentBinder;

  @Inject
  @Path("editBinder.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.editBinder    editBinder;

  @Inject
  @Path("selectBinder.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.selectBinder  selectBinder;

  @Inject
  @Path("error.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.error         error;

  @Inject
  @Path("errorMessage.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.errorMessage  errorMessage;

  @Inject
  @Path("warnMessage.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.warnMessage   warnMessage;

  @View
  public Response index() {
    if (moxtra.hasContextSpace()) {
      try {
        MoxtraBinderSpace binderSpace = context.get();
        if (binderSpace == null) {
          binderSpace = moxtra.getBinderSpace();
          if (binderSpace != null) {
            context.set(binderSpace);
          }
        }

        boolean isNew = binderSpace == null || binderSpace.isNew();

        if (moxtra.isAuthorized()) {
          return index.with().isNew(isNew).moxtraUser(moxtra.getUser().getName()).authLink("").ok();
        } else {
          return index.with().isNew(isNew).moxtraUser("").authLink(moxtra.getOAuth2Link()).ok();
        }
      } catch (Exception e) {
        LOG.error("Portlet error: " + e.getMessage(), e);
        return MoxtraBinderSpaceController_.error("Moxtra error " + e.getMessage());
      }
    } else {
      LOG.warn("Portlet must run in a space for Moxtra Binder integration.");
      return MoxtraBinderSpaceController_.error("Space required for Moxtra Binder integration");
    }
  }

  @Ajax
  @Resource
  public Response binder() {
    try {
      MoxtraBinderSpace binderSpace = context.get();
      if (binderSpace == null) {
        return editBinder.ok();
      } else {
        return currentBinder.with().binderName(binderSpace.getBinder().getName()).ok();
      }
    } catch (Exception e) {
      LOG.error("Error getting Moxtra Binder for space", e);
      return errorMessage("Error getting Moxtra Binder for space " + e.getMessage());
    }
  }

  @Ajax
  @Resource
  public Response bindersList() {
    try {
      return selectBinder.with().binders(moxtra.getBinders()).ok();
    } catch (Exception e) {
      LOG.error("Error reading Moxtra binders for current user", e);
      return errorMessage("Error reading Moxtra binders " + e.getMessage());
    }
  }

  @Action
  public Response.View save(String enableBinder,
                       String selectBinder,
                       String binderId,
                       Boolean autocreateUsers,
                       Boolean syncComments) {
    try {
      if ("on".equals(enableBinder)) {
        // TODO deal with autocreateUsers and syncComments
        if ("_new".equals(selectBinder)) {
          MoxtraBinderSpace binderSpace = moxtra.newBinderSpace();
          moxtra.createSpaceBinder(binderSpace);
          // TODO handle conflict of binder name (binder already exists with such name): ask user for a new
          // name or confirm use of existing binder
          context.set(binderSpace);
        } else if ("_existing".equals(selectBinder)) {
          MoxtraBinderSpace binderSpace = moxtra.newBinderSpace(moxtra.getBinder(binderId));
          moxtra.assignSpaceBinder(binderSpace);
          context.set(binderSpace);
        }
      } else {
        MoxtraBinderSpace binderSpace = moxtra.getBinderSpace();
        if (binderSpace != null) {
          moxtra.disableSpaceBinder(binderSpace);
        }
        context.reset();
      }
      return MoxtraBinderSpaceController_.index();
    } catch (Exception e) {
      LOG.error("Error saving Moxtra Binder in the space", e);
      return MoxtraBinderSpaceController_.error(e.getMessage());
    }
  }

  @Action
  public Response.View cancel() {
    context.reset();
    return MoxtraBinderSpaceController_.index();
  }

  @View
  public Response error(String message) {
    return error.with().message(message).ok();
  }

  Response errorMessage(String text) {
    return errorMessage.with().message(text).ok();
  }

  Response warnMessage(String text) {
    return warnMessage.with().message(text).ok();
  }

  // ***************** internals *****************

}
