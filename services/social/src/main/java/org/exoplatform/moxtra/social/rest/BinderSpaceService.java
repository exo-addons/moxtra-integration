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

import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.rest.ErrorInfo;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;

import javax.annotation.security.RolesAllowed;
import javax.jcr.RepositoryException;
import javax.ws.rs.GET;
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
  @Path("/{spaceName}/page/{pageNodeUUID}")
  public Response getPage(@Context UriInfo uriInfo,
                          @PathParam("spaceName") String spaceName,
                          @PathParam("pageNodeUUID") String pageNodeUUID) {
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(spaceName);
      if (binderSpace != null) {
        if (binderSpace.hasPage(pageNodeUUID)) {
          return Response.ok().entity(binderSpace.getPage(pageNodeUUID)).build();
        }
      }
      return Response.status(Status.NOT_FOUND).entity("{\"code\":\"page_not_found\"}").build();
    } catch (MoxtraClientException e) {
      return Response.status(Status.BAD_REQUEST)
                     .entity(ErrorInfo.clientError("Error getting binder page " + spaceName + " " + pageNodeUUID))
                     .build();
    } catch (MoxtraException e) {
      LOG.error("Error getting binder page " + spaceName + " " + pageNodeUUID, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error getting binder page")).build();
    } catch (RepositoryException e) {
      LOG.error("Error reading binder page " + spaceName + " " + pageNodeUUID, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading binder page")).build();
    }
  }

}
