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
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.NotFoundException;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraPage;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;

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
 * Moxtra binder services handled by eXo server-side client.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: BinderService.java 00000 May 20, 2015 pnedonosko $
 * 
 */
@Path("/moxtra/binder")
@Produces(MediaType.APPLICATION_JSON)
public class BinderService implements ResourceContainer {

  protected static final Log    LOG = ExoLogger.getLogger(BinderService.class);

  protected final MoxtraService moxtra;

  /**
   * 
   */
  public BinderService(MoxtraService moxtra) {
    this.moxtra = moxtra;
  }

  @GET
  @RolesAllowed("users")
  @Path("/{binderId}")
  public Response get(@Context UriInfo uriInfo, @PathParam("binderId") String binderId) {
    try {
      if (binderId != null) {
        return Response.ok().entity(moxtra.getClient().getBinder(binderId)).build();
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Binder ID cannot be null"))
                       .build();
      }
    } catch (NotFoundException e) {
      return Response.status(Status.NOT_FOUND)
                     .entity(ErrorInfo.notFoundError("Binder not found " + binderId))
                     .build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error getting binder " + binderId))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error reading binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder")).build();
    } catch (OAuthSystemException e) {
      LOG.error("Access error for reading binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Access error for reading binder")).build();
    } catch (OAuthProblemException e) {
      LOG.warn("Access problem while reading binder " + binderId, e);
      return Response.status(Status.UNAUTHORIZED)
                     .entity(ErrorInfo.accessError("Acces problem while reading binder"))
                     .build();
    }
  }

  @DELETE
  @RolesAllowed("users")
  @Path("/{binderId}")
  public Response delete(@Context UriInfo uriInfo, @PathParam("binderId") String binderId) {
    try {
      if (binderId != null) {
        MoxtraClient client = moxtra.getClient();
        MoxtraBinder binder = client.getBinder(binderId);
        client.deleteBinder(binder);
        return Response.ok().build();
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Binder ID cannot be null"))
                       .build();
      }
    } catch (NotFoundException e) {
      return Response.status(Status.NOT_FOUND)
                     .entity(ErrorInfo.notFoundError("Binder not found " + binderId))
                     .build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error deleting binder " + binderId))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error deleting binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error deleting binder")).build();
    } catch (OAuthSystemException e) {
      LOG.error("Access error for deleting binder " + binderId, e);
      return Response.serverError().entity(ErrorInfo.serverError("Access error for deleting binder")).build();
    } catch (OAuthProblemException e) {
      LOG.warn("Access problem while deleting binder " + binderId, e);
      return Response.status(Status.UNAUTHORIZED)
                     .entity(ErrorInfo.accessError("Acces problem while deleting binder"))
                     .build();
    }
  }

  @POST
  @RolesAllowed("users")
  public Response createNew(@Context UriInfo uriInfo,
                            @FormParam("name") String name,
                            @FormParam("agenda") @DefaultValue("") String agenda) {
    try {
      if (name != null) {
        MoxtraBinder binder = new MoxtraBinder().editor();
        binder.editName(name);
        MoxtraClient client = moxtra.getClient();
        client.createBinder(client.getCurrentUser(), binder);
        return Response.created(uriInfo.getRequestUri()).entity(binder).build();
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Binder name required"))
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
  @Path("/{binderId}/page")
  public Response findPageByName(@Context UriInfo uriInfo,
                           @PathParam("binderId") String binderId,
                           @QueryParam("name") String pageName) {
    try {
      if (pageName != null) {
        MoxtraClient client = moxtra.getClient();
        MoxtraBinder binder = client.getBinder(binderId);
        for (MoxtraPage page : binder.getPages()) {
          if (pageName.equals(page.getOriginalFileName())) {
            // TODO do we need check page type or other things?
            // we could ensure it was just created by created_time
            return Response.ok().entity(page).build();
          }
        }
      }
      return Response.status(Status.NOT_FOUND).entity("{\"code\":\"page_not_found\"}").build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error searching binder page by name " + pageName))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error searching binder page by name " + pageName, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder page")).build();
    } catch (OAuthSystemException e) {
      LOG.error("Access error for searching meets with invitee " + pageName, e);
      return Response.serverError()
                     .entity(ErrorInfo.serverError("Access error for searching binder page"))
                     .build();
    } catch (OAuthProblemException e) {
      LOG.warn("Access problem while searching binder page " + pageName, e);
      return Response.status(Status.UNAUTHORIZED)
                     .entity(ErrorInfo.accessError("Acces problem while searching binder page "))
                     .build();
    }
  }

  @POST
  @RolesAllowed("users")
  @Path("/{binderId}/inviteusers")
  public Response inviteUsers(@Context UriInfo uriInfo,
                              @PathParam("binderId") String binderId,
                              @FormParam("message") String message,
                              @FormParam("users[]") List<String> users) {
    try {
      if (binderId != null) {
        if (users != null && users.size() > 0) {
          MoxtraClient client = moxtra.getClient();
          try {
            MoxtraBinder binder = client.getBinder(binderId).editor();
            for (String email : users) {
              binder.addUser(new MoxtraUser(email));
            }
            client.inviteUsers(binder);

            return Response.ok().entity(binder).build();
          } catch (MoxtraClientException e) {
            return Response.status(Status.BAD_REQUEST)
                           .entity(ErrorInfo.clientError("Error inviting user(s) to binder"))
                           .build();
          } catch (OAuthSystemException e) {
            LOG.error("Access error for inviting users to binder " + binderId, e);
            return Response.serverError()
                           .entity(ErrorInfo.serverError("Access error for inviting users"))
                           .build();
          } catch (OAuthProblemException e) {
            LOG.warn("Access problem while inviting users to binder " + binderId, e);
            return Response.status(Status.UNAUTHORIZED)
                           .entity(ErrorInfo.accessError("Access problem while inviting users to binder"))
                           .build();
          }
        } else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("> Empty users in binder invitation - do nothing: " + users);
          }
          return Response.noContent().build(); // no users, no content
        }
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .entity(ErrorInfo.clientError("Binder ID required"))
                       .build();
      }
    } catch (MoxtraException e) {
      LOG.error("Error inviting users to binder " + binderId, e);
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
