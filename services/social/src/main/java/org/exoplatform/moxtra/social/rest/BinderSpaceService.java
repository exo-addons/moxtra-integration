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
package org.exoplatform.moxtra.social.rest;

import static org.exoplatform.moxtra.Moxtra.cleanValue;

import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraPage;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.rest.ErrorInfo;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MeetEvent;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
import javax.jcr.RepositoryException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

/**
 * Moxtra binder services handled by eXo server-side client.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: BinderSpaceService.java 00000 May 20, 2015 pnedonosko $
 * 
 */
@Path("/moxtra/binder/space")
@Produces(MediaType.APPLICATION_JSON)
public class BinderSpaceService implements ResourceContainer {

  protected static final Log          LOG = ExoLogger.getLogger(BinderSpaceService.class);

  protected final MoxtraSocialService moxtra;

  /**
   * 
   */
  public BinderSpaceService(MoxtraSocialService moxtra) {
    this.moxtra = moxtra;
  }

  @GET
  @RolesAllowed("users")
  @Path("/{spaceId}/page/{pageNodeUUID}")
  public Response getPage(@Context UriInfo uriInfo,
                          @PathParam("spaceId") String spaceId,
                          @PathParam("pageNodeUUID") String pageNodeUUID) {
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(spaceId);
      if (binderSpace != null) {
        if (binderSpace.ensureSpaceMember()) {
          if (binderSpace.hasPage(pageNodeUUID)) {
            MoxtraPage page = binderSpace.getPage(pageNodeUUID);
            if (page.isCreated()) {
              return Response.ok().entity(page).build();
            }
          }
        } else {
          return Response.status(Status.FORBIDDEN)
                         .entity(ErrorInfo.clientError("Not sufficient permissions to access space '"
                             + spaceId + "'"))
                         .build();
        }
      }
      // TODO would use of "Accepted" response be more correct?
      return Response.status(Status.NOT_FOUND).entity("{\"code\":\"page_not_found\"}").build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error getting binder page " + spaceId + " "
                         + pageNodeUUID))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error getting binder page " + spaceId + " " + pageNodeUUID, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error getting binder page")).build();
    } catch (RepositoryException e) {
      LOG.error("Error reading binder page " + spaceId + " " + pageNodeUUID, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder page")).build();
    }
  }

  @POST
  @RolesAllowed("users")
  @Path("/pages/{binderId}/{pageId}")
  @Deprecated
  // TODO NOT USED
  public Response savePage(@Context UriInfo uriInfo,
                           @PathParam("binderId") String binderId,
                           @PathParam("pageId") String pageId) {
    try {
      MoxtraBinder binder = moxtra.getBinder(binderId);
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(binder);
      if (binderSpace != null) {
        if (binderSpace.ensureSpaceMember()) {
          MoxtraPage page = binderSpace.findPageById(pageId);
          if (page != null) {
            // TODO save page here
            return Response.ok().entity(page).build();
          }
        } else {
          return Response.status(Status.FORBIDDEN)
                         .entity(ErrorInfo.clientError("Not sufficient permissions to access space '"
                             + binderId + "'"))
                         .build();
        }
      }
      return Response.status(Status.NOT_FOUND).entity("{\"code\":\"page_not_found\"}").build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error saving binder page " + binderId + "/" + pageId))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error getting binder page " + binderId + "/" + pageId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error getting binder page")).build();
    } catch (RepositoryException e) {
      LOG.error("Error reading binder page " + binderId + "/" + pageId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder page")).build();
    }
  }

  @POST
  @RolesAllowed("users")
  @Path("/sync/{binderId}")
  @Deprecated
  // TODO not used
  public Response syncPagesByBinder(@Context UriInfo uriInfo, @PathParam("binderId") String binderId) {
    try {
      MoxtraBinder binder = moxtra.getBinder(binderId);
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(binder);
      if (binderSpace != null) {
        if (binderSpace.ensureSpaceMember()) {
          binderSpace.syncPages();
          return Response.status(Status.ACCEPTED).entity("{\"code\":\"accepted\"}").build();
        } else {
          return Response.status(Status.FORBIDDEN)
                         .entity(ErrorInfo.clientError("Not sufficient permissions to access space '"
                             + binderId + "'"))
                         .build();
        }
      }
      return Response.status(Status.NOT_FOUND).entity("{\"code\":\"page_not_found\"}").build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error synchronizing binder pages " + binderId))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error getting binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error getting binder")).build();
    } catch (RepositoryException e) {
      LOG.error("Error reading binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder")).build();
    }
  }

  @POST
  @RolesAllowed("users")
  @Path("{spaceId}/sync/{binderId}")
  public Response syncPages(@Context UriInfo uriInfo,
                            @PathParam("spaceId") String spaceId,
                            @PathParam("binderId") String binderId) {
    try {
      MoxtraBinderSpace binderSpace;
      if (spaceId == null || spaceId.equals("null")) {
        MoxtraBinder binder = moxtra.getLocalBinder(binderId);
        if (binder != null) {
          binderSpace = moxtra.getBinderSpace(binder);
        } else {
          binderSpace = null;
        }
      } else {
        binderSpace = moxtra.getBinderSpace(spaceId);
      }
      if (binderSpace != null) {
        if (binderSpace.ensureSpaceMember()) {
          binderSpace.syncPages();
          return Response.status(Status.ACCEPTED).entity("{\"code\":\"accepted\"}").build();
        } else {
          return Response.status(Status.FORBIDDEN)
                         .entity(ErrorInfo.clientError("Not sufficient permissions to access space '"
                             + binderId + "'"))
                         .build();
        }
      }
      return Response.status(Status.NOT_FOUND).entity("{\"code\":\"page_not_found\"}").build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error synchronizing binder pages " + binderId))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error getting binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error getting binder")).build();
    } catch (RepositoryException e) {
      LOG.error("Error reading binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder")).build();
    } catch (Exception e) {
      LOG.error("Error reading binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder")).build();
    }
  }

  @POST
  @RolesAllowed("users")
  @Path("/{spaceId}/meet/event/{eventId}")
  @Deprecated
  // TODO doesn't work as needs portal request
  public Response updateMeet(@Context UriInfo uriInfo,
                             @PathParam("spaceId") String spaceId,
                             @PathParam("eventId") String eventId) {
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(spaceId);
      if (binderSpace != null) {
        if (binderSpace.ensureSpaceMember()) {
          moxtra.updateMeet(binderSpace, eventId);
        } else {
          return Response.status(Status.FORBIDDEN)
                         .entity(ErrorInfo.clientError("Not sufficient permissions to access space '"
                             + spaceId + "'"))
                         .build();
        }
      }
      return Response.status(Status.NOT_FOUND).entity("{\"code\":\"event_not_found\"}").build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error updating meet " + spaceId + "/" + eventId))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error updating meet " + spaceId + "/" + eventId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error updating meet event")).build();
    } catch (RepositoryException e) {
      LOG.error("Error updating meet " + spaceId + "/" + eventId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error updating meet event")).build();
    } catch (Exception e) {
      LOG.error("Error updating meet in '" + spaceId + "' space", e);
      return Response.serverError().entity(ErrorInfo.serverError("Error updating meet in space")).build();
    }
  }

  @POST
  @RolesAllowed("users")
  @Path("/{spaceId}/meets")
  @Deprecated
  // TODO doesn't work as needs portal request
  public Response createMeet(@Context UriInfo uriInfo,
                             @PathParam("spaceId") String spaceId,
                             @FormParam("name") String name,
                             @FormParam("agenda") @DefaultValue("") String agenda,
                             @FormParam("startTime") String startTimeMs,
                             @FormParam("endTime") String endTimeMs,
                             @FormParam("autoRecording") @DefaultValue("false") String autoRecording,
                             @FormParam("users[]") List<String> users) {

    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(spaceId);
      if (binderSpace != null) {
        if (name != null && name.length() > 0) {
          if (startTimeMs != null && endTimeMs != null) {
            MoxtraMeet meet = new MoxtraMeet().editor();
            meet.editName(name);
            meet.editAgenda(agenda);

            try {
              meet.editStartTime(Moxtra.getDate(Long.parseLong(startTimeMs)));
            } catch (NumberFormatException e) {
              return Response.status(Status.BAD_REQUEST)
                             .entity(ErrorInfo.clientError("Error parsing meet start date " + startTimeMs))
                             .build();
            }
            try {
              meet.editEndTime(Moxtra.getDate(Long.parseLong(endTimeMs)));
            } catch (NumberFormatException e) {
              return Response.status(Status.BAD_REQUEST)
                             .entity(ErrorInfo.clientError("Error parsing meet end date " + endTimeMs))
                             .build();
            }
            meet.editAutoRecording(Boolean.parseBoolean(autoRecording));

            // parse users
            Set<MoxtraUser> userSet = new LinkedHashSet<MoxtraUser>();
            for (String u : users) {
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
            return Response.ok().entity(meet).build();
          } else {
            return Response.status(Status.BAD_REQUEST)
                           .entity(ErrorInfo.clientError("Meet time required"))
                           .build();
          }
        } else {
          return Response.status(Status.BAD_REQUEST)
                         .entity(ErrorInfo.clientError("Meet name required"))
                         .build();
        }
      } else {
        return Response.status(Status.NOT_FOUND).entity("{\"code\":\"spacebinder_not_found\"}").build();
      }
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error creating meet '" + name + "' in '" + spaceId
                         + "' space "))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error creating meet in '" + spaceId + "' space", e);
      return Response.serverError().entity(ErrorInfo.serverError("Error creating meet in space")).build();
    } catch (RepositoryException e) {
      LOG.error("Error saving meet in '" + spaceId + "' space", e);
      return Response.serverError().entity(ErrorInfo.serverError("Error saving meet in space")).build();
    } catch (Exception e) {
      LOG.error("Error creating meet in '" + spaceId + "' space", e);
      return Response.serverError().entity(ErrorInfo.serverError("Error creating meet in space")).build();
    }
  }

}
