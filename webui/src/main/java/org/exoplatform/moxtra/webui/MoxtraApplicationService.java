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

import org.exoplatform.container.component.ComponentPlugin;
import org.exoplatform.webui.core.UIApplication;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Moxtra capable applications coordinator and integrator for eXo WebUI.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraApplicationService.java 00000 Mar 11, 2015 pnedonosko $
 * 
 */
public class MoxtraApplicationService {

  protected final Set<MoxtraApplicationActivator> apps = new LinkedHashSet<MoxtraApplicationActivator>();

  /**
   * 
   */
  public MoxtraApplicationService() {
  }

  public void addApplicationPlugin(ComponentPlugin appPlugin) {
    if (appPlugin instanceof MoxtraApplicationActivator) {
      apps.add((MoxtraApplicationActivator) appPlugin);
    }
  }
  
  public void activate(UIApplication app) {
    for (MoxtraApplicationActivator moxtraApp : apps) {
      if (moxtraApp.isCompatible(app)) {
        moxtraApp.activate(app);
      }
    }
  }

  public void deactivate(UIApplication app) {
    for (MoxtraApplicationActivator moxtraApp : apps) {
      if (moxtraApp.isCompatible(app)) {
        moxtraApp.deactivate(app);
      }
    }
  }

  public MoxtraApplication getApplication(UIApplication uiApp) {
    for (MoxtraApplicationActivator moxtraApp : apps) {
      MoxtraApplication app = moxtraApp.getApplication(uiApp);
      if (app != null) {
        return app;
      }
    }
    return null;
  }

}
