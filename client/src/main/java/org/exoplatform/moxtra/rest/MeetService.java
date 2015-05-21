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
package org.exoplatform.moxtra.rest;

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.NotFoundException;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;

import java.util.Calendar;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

/**
 * Moxtra Meet services handled by eXo server-side client.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: OAuth2CodeAuthenticator.java 00000 Apr 20, 2015 pnedonosko $
 * 
 */
@Path("/moxtra/meet")
@Produces(MediaType.APPLICATION_JSON)
public class MeetService implements ResourceContainer {

  protected static final Log    LOG = ExoLogger.getLogger(MeetService.class);

  protected final MoxtraService moxtra;

  /**
   * 
   */
  public MeetService(MoxtraService moxtra) {
    this.moxtra = moxtra;
  }

  @GET
  @RolesAllowed("users")
  @Path("/{sessionKey}")
  public Response get(@Context UriInfo uriInfo, @PathParam("sessionKey") String sessionKey) {
    try {
      if (sessionKey != null) {
        return Response.ok().entity(moxtra.getClient().getMeet(sessionKey)).build();
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Meet session_key cannot be null"))
                       .build();
      }
    } catch (NotFoundException e) {
      return Response.status(Status.NOT_FOUND)
                     .entity(ErrorInfo.notFoundError("Meet not found " + sessionKey))
                     .build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error getting meet " + sessionKey))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error reading meet " + sessionKey, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading meet")).build();
    } catch (OAuthSystemException e) {
      LOG.error("Access error for reading meet " + sessionKey, e);
      return Response.serverError().entity(ErrorInfo.serverError("Access error for reading meet")).build();
    } catch (OAuthProblemException e) {
      LOG.warn("Access problem while reading meet " + sessionKey, e);
      return Response.status(Status.UNAUTHORIZED)
                     .entity(ErrorInfo.accessError("Acces problem while reading meet"))
                     .build();
    }
  }

  @DELETE
  @RolesAllowed("users")
  @Path("/{sessionKey}")
  public Response delete(@Context UriInfo uriInfo, @PathParam("sessionKey") String sessionKey) {
    try {
      if (sessionKey != null) {
        MoxtraClient client = moxtra.getClient();
        MoxtraMeet meet = client.getMeet(sessionKey);
        client.deleteMeet(meet);
        return Response.ok().build();
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Meet session_key cannot be null"))
                       .build();
      }
    } catch (NotFoundException e) {
      return Response.status(Status.NOT_FOUND)
                     .entity(ErrorInfo.notFoundError("Meet not found " + sessionKey))
                     .build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error deleting meet " + sessionKey))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error deleting meet " + sessionKey, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error deleting meet")).build();
    } catch (OAuthSystemException e) {
      LOG.error("Access error for deleting meet " + sessionKey, e);
      return Response.serverError().entity(ErrorInfo.serverError("Access error for deleting meet")).build();
    } catch (OAuthProblemException e) {
      LOG.warn("Access problem while deleting meet " + sessionKey, e);
      return Response.status(Status.UNAUTHORIZED)
                     .entity(ErrorInfo.accessError("Acces problem while deleting meet"))
                     .build();
    }
  }

  @POST
  @RolesAllowed("users")
  public Response createNew(@Context UriInfo uriInfo,
                            @FormParam("name") String name,
                            @FormParam("agenda") @DefaultValue("") String agenda,
                            @FormParam("startTime") String startTimeMs,
                            @FormParam("endTime") String endTimeMs,
                            @FormParam("autoRecording") @DefaultValue("false") String autoRecording) {
    try {
      if (name != null) {
        if (startTimeMs != null && endTimeMs != null) {
          MoxtraMeet meet = new MoxtraMeet().editor();
          // TODO fill meet fields
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

          MoxtraClient client = moxtra.getClient();
          client.createMeet(meet);
          // XXX need this to get the actual data including the host user in participants
          client.refreshMeet(meet);

          return Response.created(uriInfo.getRequestUri()).entity(meet).build();
        } else {
          return Response.status(Status.BAD_REQUEST)
                         .entity(ErrorInfo.clientError("Meet time(s) required"))
                         .build();
        }
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Meet name required"))
                       .build();
      }
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error creating meet " + name))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error creating meet " + name, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error creating meet")).build();
    } catch (OAuthSystemException e) {
      LOG.error("Access error for creating meet " + name, e);
      return Response.serverError().entity(ErrorInfo.serverError("Access error for creating meet")).build();
    } catch (OAuthProblemException e) {
      LOG.warn("Access problem while creating meet " + name, e);
      return Response.status(Status.UNAUTHORIZED)
                     .entity(ErrorInfo.accessError("Acces problem while creating meet " + name))
                     .build();
    }
  }

  @GET
  @RolesAllowed("users")
  @Path("/find")
  public Response find(@Context UriInfo uriInfo, @QueryParam("invitee") String inviteeEmail) {
    try {
      if (inviteeEmail != null) {
        MoxtraClient client = moxtra.getClient();
        Calendar from = Moxtra.getCalendar();
        from.add(Calendar.MINUTE, -60); // all created a hour ago
        for (MoxtraMeet meet : client.getMeets(from.getTime(), 1)) {
          if (meet.canStart()) {
            List<MoxtraUser> users = meet.getUsers();
            MoxtraUser invited = findUser(users, inviteeEmail);
            if (invited != null && users.size() == 2) {
              MoxtraUser hoster = meet.getHostUser();
              if (!hoster.equals(invited) && meet.hasUser(hoster)) {
                return Response.ok().entity(meet).build();
              }
            }
          }
        }
      }
      return Response.noContent().build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error searching meets with invitee " + inviteeEmail))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error searching meets with invitee " + inviteeEmail, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading meet")).build();
    } catch (OAuthSystemException e) {
      LOG.error("Access error for searching meets with invitee " + inviteeEmail, e);
      return Response.serverError().entity(ErrorInfo.serverError("Access error for reading meet")).build();
    } catch (OAuthProblemException e) {
      LOG.warn("Access problem while searching meets with invitee " + inviteeEmail, e);
      return Response.status(Status.UNAUTHORIZED)
                     .entity(ErrorInfo.accessError("Acces problem while searching meet"))
                     .build();
    }
  }

  @POST
  @RolesAllowed("users")
  @Path("/{sessionKey}/inviteusers")
  public Response inviteUsers(@Context UriInfo uriInfo,
                              @PathParam("sessionKey") String sessionKey,
                              @FormParam("message") String message,
                              @FormParam("users[]") List<String> users) {
    try {
      if (sessionKey != null) {
        if (users != null && users.size() > 0) {
          MoxtraClient client = moxtra.getClient();
          try {
            MoxtraMeet meet = client.getMeet(sessionKey).editor();
            for (String email : users) {
              meet.addUser(new MoxtraUser(email));
            }
            client.inviteMeetUsers(meet);

            return Response.ok().entity(meet).build();
          } catch (MoxtraClientException e) {
            return Response.status(Status.BAD_REQUEST)
                           .entity(ErrorInfo.clientError("Error inviting user(s) to meet"))
                           .build();
          } catch (OAuthSystemException e) {
            LOG.error("Access error for inviting users to meet " + sessionKey, e);
            return Response.serverError()
                           .entity(ErrorInfo.serverError("Access error for inviting users"))
                           .build();
          } catch (OAuthProblemException e) {
            LOG.warn("Access problem while inviting users to meet " + sessionKey, e);
            return Response.status(Status.UNAUTHORIZED)
                           .entity(ErrorInfo.accessError("Access problem while inviting users to meet"))
                           .build();
          }
        } else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("> Empty users in meet invitation - do nothing: " + users);
          }
          return Response.noContent().build(); // no users, no content
        }
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Meet's session key required"))
                       .build();
      }
    } catch (MoxtraException e) {
      LOG.error("Error inviting users to meet " + sessionKey, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error inviting users")).build();
    }
  }

  protected MoxtraUser findUser(List<MoxtraUser> users, String email) {
    for (MoxtraUser user : users) {
      if (email.equals(user.getEmail())) {
        return user;
      }
    }
    return null;
  }
}
