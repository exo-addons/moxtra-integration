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
package org.exoplatform.moxtra.social;

import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.social.space.UIMoxtraBinderSpaceTools;
import org.exoplatform.moxtra.webui.MoxtraApplication;
import org.exoplatform.moxtra.webui.MoxtraNotInitializedException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.core.UIApplication;

/**
 * Application a bridge between WebUI {@link UIApplication} associated with user profile in eXo and Moxtra
 * service. This app instance exists together with the UI app (portlet) and has the same life span.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraSocialApplication.java 00000 Apr 22, 2015 pnedonosko $
 * 
 */
public class MoxtraSocialApplication implements MoxtraApplication {

  public static final Log                    LOG   = ExoLogger.getLogger(MoxtraSocialApplication.class);

  /**
   * Associated WebUI app.
   */
  protected final ThreadLocal<UIApplication> uiApp = new ThreadLocal<UIApplication>();

  protected MoxtraService                    moxtra;

  public void init() {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    this.moxtra = (MoxtraService) container.getComponentInstanceOfType(MoxtraService.class);
  }

  public boolean isAuthorized() {
    return moxtra().getClient().isAuthorized();
  }

  public String getAuthorizationLink() throws OAuthSystemException {
    return moxtra().getClient().authorizer().authorizationLink();
  }

  /**
   * Associate with WebUI application and patch the UI app if required.
   * 
   * @param uiApp {@link UIApplication}
   */
  public void activate(UIApplication uiApp) {
    this.uiApp.set(uiApp);

    // XXX hardcoded list of supported portlet apps now
    String appId = uiApp.getId();
    if (appId.equals("UISpaceMenuPortlet")) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(">> activate UISpaceMenuPortlet ");
      }
      try {
        UIMoxtraBinderSpaceTools docSelector = uiApp.getChildById(UIMoxtraBinderSpaceTools.class.getSimpleName());
        if (docSelector == null) {
          docSelector = uiApp.addChild(UIMoxtraBinderSpaceTools.class, null, null);
          if (LOG.isDebugEnabled()) {
            LOG.debug("<< activated UISpaceMenuPortlet " + docSelector);
          }
        }
      } catch (Exception e) {
        LOG.error("Error adding document selector: " + e.getMessage(), e);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void deactivate(UIApplication uiApp) {
    this.uiApp.remove();
  }

  public boolean isInitialized() {
    return this.moxtra != null;
  }

  // ********* internals ***********

  protected MoxtraService moxtra() throws MoxtraNotInitializedException {
    MoxtraService moxtra = this.moxtra;
    if (moxtra != null) {
      return moxtra;
    } else {
      throw new MoxtraNotInitializedException("Moxtra application not initialized");
    }
  }

}
