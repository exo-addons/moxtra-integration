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
package org.exoplatform.moxtra.app;

import juzu.Action;
import juzu.Path;
import juzu.Response;
import juzu.SessionScoped;
import juzu.View;
import juzu.request.RequestContext;

import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.inject.Inject;

/**
 * Juzu controller for Moxtra views.<br>
 * 
 * Created by The eXo Platform SAS<br>
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraController.java 00000 Jun 4, 2015 pnedonosko $
 * 
 */
@SessionScoped
public class MoxtraController {

  private static final Log                   LOG = ExoLogger.getLogger(MoxtraController.class);

  @Inject
  MoxtraService moxtra;
  
  @Inject
  MoxtraSocialService                        moxtraSocial;

  @Inject
  @Path("index.gtmpl")
  org.exoplatform.moxtra.app.templates.index index;

  @Inject
  @Path("error.gtmpl")
  org.exoplatform.moxtra.app.templates.error error;

  @View
  public Response index(RequestContext resourceContext) {
    if (moxtraSocial.hasContextSpace()) {
      try {
        String exoUser = resourceContext.getSecurityContext().getRemoteUser();
        boolean isNew;
        boolean isManager;
        String binderId;
        MoxtraBinderSpace binderSpace = moxtraSocial.getBinderSpace();
        if (binderSpace != null) {
          isNew = binderSpace.isNew();
          isManager = binderSpace.isCurrentUserManager();
          binderId = binderSpace.getBinder().getBinderId();
        } else {
          isManager = moxtraSocial.isContextSpaceManager();
          isNew = true; // if no binder space then it's new space
          binderId = "";
        }
        if (moxtraSocial.isAuthorized()) {
          return index.with()
                      .isNew(isNew)
                      .exoUser(exoUser)
                      .isManager(isManager)
                      .isAuthorized(true)
                      .authLink("")
                      .binderId(binderId)
                      .ok();
        } else {
          return index.with()
                      .isNew(isNew)
                      .exoUser(exoUser)
                      .isManager(isManager)
                      .isAuthorized(false)
                      .authLink(moxtraSocial.getOAuth2Link())
                      .binderId(binderId)
                      .ok();
        }
      } catch (Throwable e) {
        LOG.error("Portlet error: " + e.getMessage(), e);
        return MoxtraController_.error("Moxtra error " + e.getMessage());
      }
    } else {
      LOG.warn("Portlet must run in a space for Moxtra Binder integration.");
      return MoxtraController_.error("Space required for Moxtra Binder integration");
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
          MoxtraBinderSpace binderSpace = moxtraSocial.newBinderSpace();
          moxtraSocial.createSpaceBinder(binderSpace);
          // TODO handle conflict of binder name (binder already exists with such name): ask user for a new
          // name or confirm use of existing binder
        } else if ("_existing".equals(selectBinder)) {
          MoxtraBinderSpace binderSpace = moxtraSocial.newBinderSpace(moxtraSocial.getBinder(binderId));
          moxtraSocial.assignSpaceBinder(binderSpace);
        }
      } else {
        MoxtraBinderSpace binderSpace = moxtraSocial.getBinderSpace();
        if (binderSpace != null) {
          moxtraSocial.disableSpaceBinder(binderSpace);
        }
      }
      return MoxtraController_.index();
    } catch (Throwable e) {
      LOG.error("Error saving Moxtra Binder in the space", e);
      return MoxtraController_.error(e.getMessage());
    }
  }

  @Action
  public Response.View cancel() {
    return MoxtraController_.index();
  }

  @View
  public Response error(String message) {
    return error.with().message(message).ok();
  }

  // ***************** internals *****************

}
