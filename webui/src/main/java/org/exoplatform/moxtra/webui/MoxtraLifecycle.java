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
package org.exoplatform.moxtra.webui;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.Application;
import org.exoplatform.web.application.ApplicationLifecycle;
import org.exoplatform.web.application.RequestFailure;
import org.exoplatform.webui.application.StateManager;
import org.exoplatform.webui.application.WebuiApplication;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.core.UIApplication;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraLifecycle.java 00000 Mar 11, 2015 pnedonosko $
 * 
 */
public class MoxtraLifecycle implements ApplicationLifecycle<WebuiRequestContext> {
  
  public static final Log LOG = ExoLogger.getLogger(MoxtraLifecycle.class);

  /**
   * 
   */
  public MoxtraLifecycle() {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onInit(Application app) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onStartRequest(Application app, WebuiRequestContext context) throws Exception {
    activate(app, context);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onFailRequest(Application app, WebuiRequestContext context, RequestFailure failureType) {
    try {
      deactivate(app, context);
    } catch (Exception e) {
      LOG.warn("Error deactivating Moxtra apps on request failure", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onEndRequest(Application app, WebuiRequestContext context) throws Exception {
    deactivate(app, context);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onDestroy(Application app) throws Exception {
  }

  // ********** internal **********

  protected void activate(Application app, WebuiRequestContext context) throws Exception {
    ExoContainer container = app.getApplicationServiceContainer();
    if (container != null) {
      MoxtraApplicationService moxtraApps = (MoxtraApplicationService) container.getComponentInstanceOfType(MoxtraApplicationService.class);

      // TODO cleanup
      // PortalApplication portalApp = (PortalApplication) app;
      // PortalRequestContext prContext = (PortalRequestContext) context;

      UIApplication uiApp = context.getUIApplication();
      StateManager stateManager = null;
      if (uiApp == null) {
        WebuiApplication webuiApp = (WebuiApplication) app;//((WebuiApplication) app).getStateManager().restoreUIRootComponent(context);
        stateManager = webuiApp.getStateManager();
        uiApp = stateManager.restoreUIRootComponent(context);
      }

      if (uiApp != null) {
        moxtraApps.activate(uiApp);
        if (stateManager != null) {
          context.setUIApplication(uiApp);
          stateManager.storeUIRootComponent(context);
        }
      }
    }
  }

  protected void deactivate(Application app, WebuiRequestContext context) throws Exception {
    ExoContainer container = app.getApplicationServiceContainer();
    if (container != null) {
      MoxtraApplicationService moxtraApps = (MoxtraApplicationService) container.getComponentInstanceOfType(MoxtraApplicationService.class);
      UIApplication uiApp = context.getUIApplication();
      if (uiApp != null) {
        moxtraApps.deactivate(uiApp);
      }
    }
  }

}
