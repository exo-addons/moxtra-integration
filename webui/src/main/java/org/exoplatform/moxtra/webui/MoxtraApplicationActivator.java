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

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.container.configuration.ConfigurationException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.core.UIApplication;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: AbstractMoxtraApplication.java 00000 Mar 11, 2015 pnedonosko $
 * 
 */
public class MoxtraApplicationActivator extends BaseComponentPlugin {

  public static final String CONF_APP_TYPE       = "app-type";

  public static final String CONF_COMPONENT_TYPE = "component-type";

  public static final String CONF_COMPONENT_ID   = "component-id";

  protected static final Log                            LOG  = ExoLogger.getLogger(MoxtraApplicationActivator.class);

  protected final Map<String, String>                   config;

  protected final Map<UIApplication, MoxtraApplication> apps = new WeakHashMap<UIApplication, MoxtraApplication>();

  /**
   * @throws ConfigurationException
   * 
   */
  public MoxtraApplicationActivator(InitParams params) throws ConfigurationException {
    PropertiesParam param = params.getPropertiesParam("app-configuration");
    if (param != null) {
      config = Collections.unmodifiableMap(param.getProperties());
    } else {
      throw new ConfigurationException("Property parameters app-configuration required.");
    }
  }

  public String getAppType() {
    return config.get(CONF_APP_TYPE);
  }

  public String getComponentType() {
    return config.get(CONF_COMPONENT_TYPE);
  }

  public String getComponentId() {
    return config.get(CONF_COMPONENT_ID);
  }

  /**
   * Check if this Moxtra app compatible with given WebUI app.
   * 
   * @param uiApp {@link UIApplication} WebUI app instance
   * @return boolean, <code>true</code> if this Moxtra app is compatible with given WebUI app,
   *         <code>false</code> otherwise
   */
  public boolean isCompatible(UIApplication uiApp) {
    boolean res;
    if (getComponentId().equals(uiApp.getId())) {
      res = true;
    } else {
      res = false;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("> isCompatible:" + res + " " + uiApp);
    }

    return res;
  }

  /**
   * Activate {@link MoxtraApplication} for given WebUI application.<br>
   * 
   * @param uiApp {@link UIApplication}
   */
  public void activate(UIApplication uiApp) {
    MoxtraApplication app = apps.get(uiApp);
    if (app == null) {
      synchronized (uiApp) {
        app = apps.get(uiApp);
        if (app == null) {
          try {
            String appType = getAppType();
            Class<?> appClass = Class.forName(appType);
            if (MoxtraApplication.class.isAssignableFrom(appClass)) {
              try {
                // empty constructor should be available
                app = (MoxtraApplication) appClass.newInstance();
                app.init();
                apps.put(uiApp, app);
              } catch (SecurityException e) {
                LOG.error("Cannot load app constructor for " + appType, e);
              } catch (InstantiationException e) {
                LOG.error("Cannot instantiate app " + appType, e);
              } catch (IllegalAccessException e) {
                LOG.error("Cannot access app constructor for " + appType, e);
              } catch (IllegalArgumentException e) {
                LOG.error("App constructor has wrong arguments in " + appType, e);
              }
            } else {
              LOG.error("Given app type is not an instance of " + MoxtraApplication.class.getName()
                  + " and cannot be used within " + uiApp);
            }
          } catch (ClassNotFoundException e) {
            LOG.error("Error activating Moxtra app for " + uiApp, e);
          }
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("> activate: " + uiApp + " = " + app);
      }
    } else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("> reactivate: " + uiApp + " = " + app);
      }
    }

    app.activate(uiApp);
  }

  /**
   * Deactivate {@link MoxtraApplication} for given WebUI application.<br>
   * 
   * @param uiApp {@link UIApplication}
   */
  public void deactivate(UIApplication uiApp) {
    MoxtraApplication app = apps.get(uiApp);
    if (app != null) {
      app.deactivate(uiApp);
      if (LOG.isDebugEnabled()) {
        LOG.debug("> deactivate: " + uiApp + " = " + app);
      }
    }
  }

  public MoxtraApplication getApplication(UIApplication uiApp) {
    synchronized (uiApp) {
      return apps.get(uiApp);
    }
  }
}
