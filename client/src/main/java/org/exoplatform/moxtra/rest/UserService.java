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

import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.rest.resource.ResourceContainer;

import javax.annotation.security.RolesAllowed;
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
 * Access to eXo Users for invitation in Moxtra.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: OAuth2CodeAuthenticator.java 00000 Apr 20, 2015 pnedonosko $
 * 
 */
@Path("/moxtra/user")
@Produces(MediaType.APPLICATION_JSON)
public class UserService implements ResourceContainer {

  protected static final Log LOG = ExoLogger.getLogger(UserService.class);

  protected class UserInfo {
    final String userName;

    final String firstName;

    final String lastName;

    final String email;

    protected UserInfo(String userName, String firstName, String lastName, String email) {
      super();
      this.userName = userName;
      this.firstName = firstName;
      this.lastName = lastName;
      this.email = email;
    }

    /**
     * @return the userName
     */
    public String getUserName() {
      return userName;
    }

    /**
     * @return the firstName
     */
    public String getFirstName() {
      return firstName;
    }

    /**
     * @return the lastName
     */
    public String getLastName() {
      return lastName;
    }

    /**
     * @return the email
     */
    public String getEmail() {
      return email;
    }
  }

  protected class AuthInfo {
    final String  userName;

    final boolean authorized;

    final String  authLink;

    protected AuthInfo(String userId, boolean authorized, String authLink) {
      super();
      this.userName = userId;
      this.authorized = authorized;
      this.authLink = authLink;
    }

    /**
     * @return the authorized
     */
    public boolean isAuthorized() {
      return authorized;
    }

    /**
     * @return the authLink
     */
    public String getAuthLink() {
      return authLink;
    }

    /**
     * @return the userName
     */
    public String getUserName() {
      return userName;
    }
  }

  protected final MoxtraService       moxtra;

  protected final OrganizationService orgService;

  /**
   * 
   */
  public UserService(MoxtraService moxtra, OrganizationService orgService) {
    this.moxtra = moxtra;
    this.orgService = orgService;
  }

  @GET
  @RolesAllowed("users")
  @Path("/exo/{userName}")
  public Response getLocalUser(@Context UriInfo uriInfo, @PathParam("userName") String userName) {

    try {
      User exoUser = orgService.getUserHandler().findUserByName(userName);
      if (exoUser != null) {
        return Response.ok()
                       .entity(new UserInfo(exoUser.getUserName(),
                                            exoUser.getFirstName(),
                                            exoUser.getLastName(),
                                            exoUser.getEmail()))
                       .build();
      } else {
        return Response.status(Status.NOT_FOUND)
                       .entity(ErrorInfo.notFoundError("User not found " + userName))
                       .build();
      }
    } catch (Exception e) {
      LOG.error("Error reading user " + userName, e);
      return Response.serverError().entity(ErrorInfo.serverError("Error reading user")).build();
    }
  }

  @GET
  @RolesAllowed("users")
  @Path("/me")
  public Response getMoxtraCurrentUserAuth() {
    try {
      String userName = Moxtra.currentUserName();
      MoxtraClient client = moxtra.getClient();
      boolean authorized = client.isAuthorized();
      String authLink;
      if (!authorized) {
        authLink = client.authorizer().authorizationLink();
      } else {
        authLink = null;
      }
      return Response.ok().entity(new AuthInfo(userName, authorized, authLink)).build();
    } catch (Exception e) {
      LOG.error("Error getting authorization info", e);
      return Response.serverError().entity(ErrorInfo.serverError("Error getting authorization link")).build();
    }
  }
}
