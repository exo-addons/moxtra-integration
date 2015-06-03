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

import net.oauth.OAuthProblemException;

import org.apache.commons.io.IOUtils;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.rest.resource.ResourceContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

/**
 * Moxtra Javascript View Components services handled by eXo server-side client.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: ViewService.java 00000 May 25, 2015 pnedonosko $
 * 
 */
@Path("/moxtra")
@Produces(MediaType.TEXT_HTML)
public class ViewService implements ResourceContainer {

  protected static final Log            LOG = ExoLogger.getLogger(ViewService.class);

  protected final MoxtraService         moxtra;

  protected final ResourceBundleService resources;

  protected String                      pageViewTemplate;

  /**
   * 
   */
  public ViewService(MoxtraService moxtra, ResourceBundleService resources) {
    this.moxtra = moxtra;
    this.resources = resources;
  }

  @GET
  @Path("/page/{pagePath:.*}/")
  public Response pageView(@Context HttpServletRequest request,
                           @Context UriInfo uriInfo,
                           @Context SecurityContext security,
                           @PathParam("pagePath") String pagePath) {
    // ensure authorized user in eXo
    if (security.isUserInRole("users")) {
      if (pageViewTemplate == null) {
        // read template and store in runtime for next requests
        InputStream pageContent = ViewService.class.getResourceAsStream("/views/moxtra-page.html");
        if (pageContent != null) {
          try {
            pageViewTemplate = IOUtils.toString(pageContent, "UTF-8");
          } catch (IOException e) {
            LOG.error("Error reading page view: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error reading page view").build();
          } finally {
            try {
              pageContent.close();
            } catch (IOException e) {
              LOG.error("Error closing page view content stream: " + e.getMessage(), e);
            }
          }
        } else {
          LOG.error("Page view not found: pageView()");
          return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Page view not found").build();
        }
      }

      // TODO use Groovy template?
      // replace i18n parts
      String pageText = pageViewTemplate.replace("&{Moxtra.waitConversationPagePreparing}",
                                                 getString("Moxtra.waitConversationPagePreparing",
                                                           request.getLocale()));
      pageText = pageText.replace("&{Moxtra.conversationPageNotOpen}",
                                  getString("Moxtra.conversationPageNotOpen", request.getLocale()));
      pageText = pageText.replace("&{Moxtra.loginMoxtraHint}",
                                  getString("Moxtra.loginMoxtraHint", request.getLocale()));
      pageText = pageText.replace("&{Moxtra.loginMoxtra}",
                                  getString("Moxtra.loginMoxtra", request.getLocale()));

//      MoxtraClient client = moxtra.getClient();
//      if (!client.isAuthorized()) {
//        // Moxtra auth URL
//        try {
//          pageText = pageText.replace("${authLink}", client.authorizer().authorizationLink());
//        } catch (OAuthSystemException e) {
//          LOG.error("Error preparing page view: " + e.getMessage(), e);
//          return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error preparing page view").build();
//        }
//      }

      return Response.ok().entity(pageText).build();
    } else {
      // redirect not authorized user to portal login
      URI requestUri = uriInfo.getAbsolutePath();
      // https://peter.exoplatform.com.ua:8443/portal/login?initialURI=%2Fportal%2Fintranet%2F
      // String scheme,
      // String userInfo, String host, int port,
      // String path, String query, String fragment
      try {
        URI loginUrl = new URI(requestUri.getScheme(), null, // userInfo
                               requestUri.getHost(),
                               requestUri.getPort(),
                               "/portal/login",
                               "initialURI=" + uriInfo.getRequestUri().toString(),
                               null);
        return Response.temporaryRedirect(loginUrl).build();
      } catch (URISyntaxException e) {
        LOG.error("Error preparing login page redirect URI: pageView()", e);
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity("Error preparing login page link")
                       .build();
      }
    }
  }

  protected String getString(String key, Locale locale) {
    ResourceBundle res;
    try {
      res = resources.getResourceBundle("locale.moxtra.Moxtra", locale);
    } catch (MissingResourceException e) {
      try {
        res = resources.getResourceBundle("locale.moxtra.Moxtra", Locale.getDefault());
      } catch (MissingResourceException ed) {
        LOG.warn("Error loading resource bundle for Moxtra: " + e.getMessage());
      }
      res = null;
    }
    if (res != null) {
      try {
        return res.getString(key);
      } catch (Throwable t) {
        LOG.error("Error getting resource message " + key, t);
      }
    }
    return key;
  }
}
