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
import org.exoplatform.container.PortalContainer;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

/**
 * OAuth2 authenticator for code redirects.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: OAuth2CodeAuthenticator.java 00000 Feb 27, 2015 pnedonosko $
 * 
 */
@Path("/moxtra/login")
@Produces(MediaType.TEXT_HTML)
public class OAuthCodeAuthenticator implements ResourceContainer {

  public static final String          CLIENT_CODE_COOKIE     = "moxtra-client-code";

  public static final int             CLIENT_COOKIE_EXPIRE   = 20;                                               // 20sec

  public static final String          CLIENT_ERROR_COOKIE    = "moxtra-client-error";

  public static final String          CLIENT_CODE_AUTHORIZED = "authorized";

  protected static final Log          LOG                    = ExoLogger.getLogger(OAuthCodeAuthenticator.class);

  protected static final String       EMPTY                  = "".intern();

  protected final MoxtraService       moxtra;

  protected final OrganizationService orgService;

  /**
   * 
   */
  public OAuthCodeAuthenticator(MoxtraService moxtra, OrganizationService orgService) {
    this.moxtra = moxtra;
    this.orgService = orgService;
  }

  @GET
  public Response auth(@Context UriInfo uriInfo,
                       @QueryParam("code") String code,
                       @CookieParam("JSESSIONID") Cookie jsessionsId,
                       @CookieParam("JSESSIONIDSSO") Cookie jsessionsIdSSO) {

    try {
      moxtra.getClient().authorizer().authorize(code);
      return Response.ok()
                     .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                           CLIENT_CODE_AUTHORIZED,
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           CLIENT_COOKIE_EXPIRE,
                                           false))
                     .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                           "OAuth2 system error",
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           0, // reset error cookie
                                           false))
                     .entity("<!doctype html><html><head><script type='text/javascript'>/*window.close();*/</script>"
                         + "</head><body><div id='messageString'>Connecting to Moxtra...</div></body></html>")
                     .build();
    } catch (OAuthSystemException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("OAuth2 system error", e);
      }
      return Response.serverError()
                     .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                           "OAuth2 system error",
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           CLIENT_COOKIE_EXPIRE,
                                           false))
                     .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                           CLIENT_CODE_AUTHORIZED,
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           0, // reset auth cookie
                                           false))
                     .entity("System error. " + e.getMessage())
                     .build();
    } catch (OAuthProblemException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("OAuth2 problem", e);
      }
      String msg = e.getMessage();
      if (msg != null && msg.indexOf("invalid_request") >= 0) {
        // use cause to get details
        Throwable cause = e.getCause();
        msg = "Invalid request. " + cause.getMessage();
      }
      return Response.status(Status.BAD_REQUEST)
                     .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                           "OAuth2 problem",
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           CLIENT_COOKIE_EXPIRE,
                                           false))
                     .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                           CLIENT_CODE_AUTHORIZED,
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           0, // reset auth cookie
                                           false))
                     .entity("Authorization problem. " + msg)
                     .build();
    }
  }

  @GET
  @RolesAllowed("users")
  @Path("/org")
  public Response authOrg(@Context UriInfo uriInfo,
                          @QueryParam("code") String code,
                          @CookieParam("JSESSIONID") Cookie jsessionsId,
                          @CookieParam("JSESSIONIDSSO") Cookie jsessionsIdSSO) {

    try {
      ConversationState currentConvo = ConversationState.getCurrent();
      if (currentConvo != null) {
        String userId = currentConvo.getIdentity().getUserId();
        User user;
        try {
          user = orgService.getUserHandler().findUserByName(userId);
        } catch (Exception e) {
          LOG.error("Error getting user object " + userId, e);
          return Response.serverError()
                         .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                               "System error",
                                               "/",
                                               uriInfo.getRequestUri().getHost(),
                                               EMPTY,
                                               CLIENT_COOKIE_EXPIRE,
                                               false))
                         .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                               CLIENT_CODE_AUTHORIZED,
                                               "/",
                                               uriInfo.getRequestUri().getHost(),
                                               EMPTY,
                                               0, // reset auth cookie
                                               false))
                         .entity("System error: cannot find organization user")
                         .build();
        }

        // org_id it's base portal URL
        String orgId = "PD8LdFdvtdmBUvazMUMYnL5";//uriInfo.getBaseUri().toString();// TODO replace rest with portal?

        moxtra.getClient().authorizer().authorizeOrg(orgId, userId, user.getFirstName(), user.getLastName());
        return Response.ok()
                       .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                             CLIENT_CODE_AUTHORIZED,
                                             "/",
                                             uriInfo.getRequestUri().getHost(),
                                             EMPTY,
                                             CLIENT_COOKIE_EXPIRE,
                                             false))
                       .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                             "OAuth2 system error",
                                             "/",
                                             uriInfo.getRequestUri().getHost(),
                                             EMPTY,
                                             0, // reset error cookie
                                             false))
                       .entity("<!doctype html><html><head><script type='text/javascript'>/*window.close();*/</script>"
                           + "</head><body><div id='messageString'>Connecting to Moxtra...</div></body></html>")
                       .build();
      } else {
        return Response.status(Status.BAD_REQUEST)
                       .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                             "Auth problem",
                                             "/",
                                             uriInfo.getRequestUri().getHost(),
                                             EMPTY,
                                             CLIENT_COOKIE_EXPIRE,
                                             false))
                       .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                             CLIENT_CODE_AUTHORIZED,
                                             "/",
                                             uriInfo.getRequestUri().getHost(),
                                             EMPTY,
                                             0, // reset auth cookie
                                             false))
                       .entity("Identity problem. Cannot find eXo user.")
                       .build();
      }
    } catch (OAuthSystemException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("OAuth2 system error", e);
      }
      return Response.serverError()
                     .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                           "OAuth2 system error",
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           CLIENT_COOKIE_EXPIRE,
                                           false))
                     .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                           CLIENT_CODE_AUTHORIZED,
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           0, // reset auth cookie
                                           false))
                     .entity("System error. " + e.getMessage())
                     .build();
    } catch (OAuthProblemException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("OAuth2 problem", e);
      }
      String msg = e.getMessage();
      if (msg != null && msg.indexOf("invalid_request") >= 0) {
        // use cause to get details
        Throwable cause = e.getCause();
        msg = "Invalid request. " + cause.getMessage();
      }
      return Response.status(Status.BAD_REQUEST)
                     .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                           "OAuth2 problem",
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           CLIENT_COOKIE_EXPIRE,
                                           false))
                     .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                           CLIENT_CODE_AUTHORIZED,
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           0, // reset auth cookie
                                           false))
                     .entity("Authorization problem. " + msg)
                     .build();
    } catch (MoxtraClientException e) {
      LOG.error("Error preparing org client: " + e.getMessage(), e);
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .cookie(new NewCookie(CLIENT_ERROR_COOKIE,
                                           "Moxtra problem",
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           CLIENT_COOKIE_EXPIRE,
                                           false))
                     .cookie(new NewCookie(CLIENT_CODE_COOKIE,
                                           CLIENT_CODE_AUTHORIZED,
                                           "/",
                                           uriInfo.getRequestUri().getHost(),
                                           EMPTY,
                                           0, // reset auth cookie
                                           false))
                     .entity("System problem: cannot initialize organization client.")
                     .build();
    }
  }

  @GET
  @RolesAllowed("users")
  @Path("/accesstoken")
  @Produces(MediaType.APPLICATION_JSON)
  public Response currentAccess(@Context UriInfo uriInfo, @QueryParam("code") String code) {
    // TODO cleanup of method params not used
    return Response.ok()
                   .entity("{\"clientId\":\"" + moxtra.getOAuthConfig().getClientId()
                       + "\",\"accessToken\":\"" + moxtra.getClient().getOAuthAccessToken() + "\"}")
                   .build();
  }
}
