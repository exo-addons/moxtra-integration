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

import static org.exoplatform.moxtra.Moxtra.cleanValue;

import juzu.Action;
import juzu.Path;
import juzu.PropertyType;
import juzu.Resource;
import juzu.Response;
import juzu.SessionScoped;
import juzu.View;
import juzu.request.RequestContext;

import org.exoplatform.commons.juzu.ajax.Ajax;
import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MeetEvent;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

  private static final Log                                         LOG = ExoLogger.getLogger(MoxtraBinderSpaceController.class);

  @Inject
  Provider<PortletPreferences>                                     preferences;

  @Inject
  MoxtraSocialService                                              moxtra;

  @Inject
  MoxtraBinderSpaceContext                                         context;

  @Inject
  @Path("index.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.index            index;

  @Inject
  @Path("currentBinder.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.currentBinder    currentBinder;

  @Inject
  @Path("binderConfig.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.binderConfig     binderConfig;

  @Inject
  @Path("selectBinder.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.selectBinder     selectBinder;

  @Inject
  @Path("binderData.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.binderData       binderData;

  @Inject
  @Path("settingsPopup.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.settingsPopup    settingsPopup;

  @Inject
  @Path("meetParticipants.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.meetParticipants meetParticipants;

  @Inject
  @Path("meetCreated.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.meetCreated      meetCreated;

  @Inject
  @Path("meetUpdated.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.meetUpdated      meetUpdated;

  @Inject
  @Path("error.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.error            error;

  @Inject
  @Path("errorMessage.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.errorMessage     errorMessage;

  @Inject
  @Path("warnMessage.gtmpl")
  org.exoplatform.moxtra.social.portlet.templates.warnMessage      warnMessage;

  @View
  public Response index(RequestContext resourceContext) {
    if (moxtra.hasContextSpace()) {
      try {
        MoxtraBinderSpace binderSpace = context.get();
        if (binderSpace == null) {
          binderSpace = moxtra.getBinderSpace();
          if (binderSpace != null) {
            context.set(binderSpace);
            // ensure the user is a member of the binder when this possible
            binderSpace.ensureBinderMember();
          }
        }

        String exoUser = resourceContext.getSecurityContext().getRemoteUser();
        boolean isNew;
        boolean isManager;
        String binderId, spaceName;
        if (binderSpace != null) {
          isNew = binderSpace.isNew();
          isManager = binderSpace.isCurrentUserManager();
          binderId = binderSpace.getBinder().getBinderId();
          spaceName = binderSpace.getSpace().getPrettyName();
        } else {
          isManager = moxtra.isContextSpaceManager();
          isNew = true; // if no binder space then it's new binder
          binderId = "";
          spaceName = moxtra.getContextSpace().getPrettyName();
        }
        if (moxtra.isAuthorized()) {
          return index.with()
                      .isNew(isNew)
                      .exoUser(exoUser)
                      .isManager(isManager)
                      .isAuthorized(true)
                      .authLink("")
                      .binderId(binderId)
                      .spaceName(spaceName)
                      .ok();
        } else {
          return index.with()
                      .isNew(isNew)
                      .exoUser(exoUser)
                      .isManager(isManager)
                      .isAuthorized(false)
                      .authLink(moxtra.getOAuth2Link())
                      .binderId(binderId)
                      .spaceName(spaceName)
                      .ok();
        }
      } catch (Throwable e) {
        LOG.error("Portlet error: " + e.getMessage(), e);
        return MoxtraBinderSpaceController_.error(e.getMessage());
      }
    } else {
      LOG.warn("Portlet must run in a space for Moxtra Binder integration.");
      return MoxtraBinderSpaceController_.error("Space required for Moxtra Binder integration");
    }
  }

  @Ajax
  @Resource
  public Response binderData() {
    return binderData.ok();
  }

  @Ajax
  @Resource
  @Deprecated
  public Response settingsPopup() {
    try {
      MoxtraBinderSpace binderSpace = context.get();
      boolean isNew = binderSpace == null || binderSpace.isNew();
      return settingsPopup.with().isNew(isNew).ok();
    } catch (Exception e) {
      LOG.error("Error getting Moxtra Binder space settings", e);
      return errorMessage("Error getting Moxtra Binder space settings" + e.getMessage());
    }
  }

  @Ajax
  @Resource
  public Response binder() {
    try {
      MoxtraBinderSpace binderSpace = context.get();
      if (binderSpace == null) {
        return binderConfig.ok();
      } else {
        MoxtraBinder binder = binderSpace.getBinder();
        return currentBinder.with().binderId(binder.getBinderId()).binderName(binder.getName()).ok();
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

  @Ajax
  @Resource
  public Response contactsList() {
    try {
      // remove space members from the contacts
      List<MoxtraUser> parts = new ArrayList<MoxtraUser>();
      List<MoxtraUser> spaceMembers = moxtra.getBinderSpace().getMembers(false);
      for (MoxtraUser user : moxtra.getContacts()) {
        if (!spaceMembers.contains(user)) {
          parts.add(user);
        }
      }
      return meetParticipants.with().isMoxtra(true).users(parts).ok();
    } catch (Exception e) {
      LOG.error("Error reading Moxtra contacts for current user", e);
      return errorMessage("Error reading Moxtra contacts " + e.getMessage());
    }
  }

  @Ajax
  @Resource
  public Response spaceMembersList() {
    try {
      return meetParticipants.with().isMoxtra(false).users(moxtra.getBinderSpace().getMembers(false)).ok();
    } catch (Exception e) {
      LOG.error("Error reading Moxtra contacts for current user", e);
      return errorMessage("Error reading Moxtra contacts " + e.getMessage());
    }
  }

  @Ajax
  @Resource
  public Response createMeet(String name,
                             String agenda,
                             String startTime,
                             String endTime,
                             String autoRecording,
                             String users) {
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace();
      if (binderSpace != null) {
        if (name != null && name.length() > 0) {
          if (startTime != null && endTime != null) {
            MoxtraMeet meet = new MoxtraMeet().editor();
            meet.editName(name);
            meet.editAgenda(agenda);

            try {
              meet.editStartTime(Moxtra.getDate(Long.parseLong(startTime)));
            } catch (NumberFormatException e) {
              return errorMessage("Error parsing meet start date " + startTime);
            }
            try {
              meet.editEndTime(Moxtra.getDate(Long.parseLong(endTime)));
            } catch (NumberFormatException e) {
              return errorMessage("Error parsing meet end date " + endTime);
            }
            meet.editAutoRecording(Boolean.valueOf(autoRecording));

            // parse users
            Set<MoxtraUser> userSet = new LinkedHashSet<MoxtraUser>();
            for (String u : users.split(",")) {
              String[] uparts = u.split("\\+");
              String email, uniqueId, orgId;
              if (uparts.length >= 3) {
                email = cleanValue(uparts[0]);
                uniqueId = cleanValue(uparts[1]);
                orgId = cleanValue(uparts[2]);
              } else if (uparts.length == 2) {
                email = cleanValue(uparts[0]);
                uniqueId = cleanValue(uparts[1]);
                orgId = null;
              } else {
                email = u;
                uniqueId = orgId = null;
              }
              userSet.add(new MoxtraUser(uniqueId, orgId, email));
            }
            for (MoxtraUser user : userSet) {
              meet.addUser(user);
            }

            MeetEvent event = moxtra.createMeet(binderSpace, meet);
            return meetCreated.with()
                              .binderId(meet.getBinderId())
                              .sessionKey(meet.getSessionKey())
                              .startLink(meet.getStartMeetUrl())
                              .startTime(meet.getStartTime().getTime())
                              .endTime(meet.getEndTime().getTime())
                              .eventLink(event.getEventActivityLink())
                              .eventId(event.getEventId())
                              .ok();
          } else {
            return errorMessage("Meet time required");
          }
        } else {
          return errorMessage("Meet name required");
        }
      } else {
        LOG.warn("Space binder not found " + moxtra.getContextSpace());
        return errorMessage("Space binder not found");
      }
    } catch (Exception e) {
      LOG.error("Error creating Moxtra meet", e);
      return errorMessage("Error creating Moxtra meet " + e.getMessage());
    }
  }

  @Ajax
  @Resource
  public Response refreshMeet(String eventId) {
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace();
      if (binderSpace != null) {
        if (binderSpace.ensureSpaceMember()) {
          MeetEvent event = moxtra.updateMeet(binderSpace, eventId);
          return meetUpdated.with()
                            .startTime(event.getStartTime().getTime())
                            .endTime(event.getEndTime().getTime())
                            .eventId(event.getEventId())
                            .ok();
        } else {
          return errorMessage("Not sufficient permissions to access the space");
        }
      } else {
        LOG.warn("Space binder not found " + moxtra.getContextSpace());
        return errorMessage("Space binder not found");
      }
    } catch (Exception e) {
      LOG.error("Error creating Moxtra meet", e);
      return errorMessage("Error creating Moxtra meet " + e.getMessage());
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
        if ("_new".equals(selectBinder)) {
          MoxtraBinderSpace binderSpace = moxtra.newBinderSpace();
          moxtra.createSpaceBinder(binderSpace);
          // TODO handle conflict of binder name (binder already exists with such name): ask user for a new
          // name or confirm use of existing binder
          context.set(binderSpace);
        } else if ("_existing".equals(selectBinder)) {
          if (binderId != null && binderId.length() > 0) {
            MoxtraBinderSpace binderSpace = moxtra.newBinderSpace(moxtra.getBinder(binderId));
            moxtra.assignSpaceBinder(binderSpace);
            context.set(binderSpace);
          } else {
            return MoxtraBinderSpaceController_.error("Existing binder cannot be empty");
          }
        }
      } else {
        MoxtraBinderSpace binderSpace = moxtra.getBinderSpace();
        if (binderSpace != null) {
          moxtra.disableSpaceBinder(binderSpace);
        }
        context.reset();
      }
      return MoxtraBinderSpaceController_.index().withNo(PropertyType.REDIRECT_AFTER_ACTION);
    } catch (Throwable e) {
      LOG.error("Error saving Moxtra Binder in the space", e);
      return MoxtraBinderSpaceController_.error(e.getMessage());
    }
  }

  @Action
  public Response.View cancel() {
    context.reset();
    return MoxtraBinderSpaceController_.index().withNo(PropertyType.REDIRECT_AFTER_ACTION);
  }

  @View
  public Response error(String message) {
    return error.with().message(message != null ? message : "").ok();
  }

  Response errorMessage(String text) {
    return errorMessage.with().message(text != null ? text : "").ok();
  }

  Response warnMessage(String text) {
    return warnMessage.with().message(text != null ? text : "").ok();
  }

  // ***************** internals *****************

}
