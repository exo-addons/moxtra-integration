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

import org.apache.commons.io.IOUtils;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.web.application.RequestContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Moxtra Javascript View Components services handled by eXo server-side client.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: ViewService.java 00000 May 25, 2015 pnedonosko $
 * 
 */
@Path("/moxtra/view")
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
  @RolesAllowed("users")
  @Path("/pages")
  public Response pageView(@Context HttpServletRequest request) {
    if (pageViewTemplate == null) {
      InputStream pageContent = ViewService.class.getResourceAsStream("/views/moxtra-page-view.html");
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

    // replace i18n parts
    String pageText = pageViewTemplate.replace("&{Moxtra.waitConversationPagePreparing}",
                                               getString("Moxtra.waitConversationPagePreparing",
                                                         request.getLocale()));
    pageText = pageText.replace("&{Moxtra.conversationPageNotOpen}",
                                getString("Moxtra.conversationPageNotOpen", request.getLocale()));
    return Response.ok().entity(pageText).build();
  }

  protected String getString(String key, Locale locale) {
    ResourceBundle res;
    try {
      res = resources.getResourceBundle("locale.moxtra.social.Moxtra", locale);
    } catch (MissingResourceException e) {
      try {
        res = resources.getResourceBundle("locale.moxtra.social.Moxtra", Locale.getDefault());
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
