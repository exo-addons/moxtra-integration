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
package org.exoplatform.moxtra.meetbutton;

import org.apache.commons.lang.reflect.FieldUtils;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.webui.MoxtraApplication;
import org.exoplatform.moxtra.webui.MoxtraNotActivatedException;
import org.exoplatform.moxtra.webui.MoxtraNotInitializedException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.JavascriptManager;
import org.exoplatform.web.application.RequireJS;
import org.exoplatform.webui.application.WebuiApplication;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.Component;
import org.exoplatform.webui.config.Event;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.event.EventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Application a bridge between WebUI {@link UIApplication} associated with user profile in eXo and Moxtra
 * service. This app instance exists together with the UI app (portlet) and has the same life span.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraMeetButtonApplication.java 00000 Apr 22, 2015 pnedonosko $
 * 
 */
public class MoxtraMeetButtonApplication implements MoxtraApplication {

  public static final Log                    LOG              = ExoLogger.getLogger(MoxtraMeetButtonApplication.class);

  /**
   * Associated WebUI app.
   */
  protected final ThreadLocal<UIApplication> uiApp            = new ThreadLocal<UIApplication>();

  /**
   * Associated component (container).
   */
  protected final ThreadLocal<UIContainer>   requestComponent = new ThreadLocal<UIContainer>();

  protected final Set<Component>             mergedConfigs    = new HashSet<Component>();

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
    this.uiApp.set(uiApp); // for information mainly

    // XXX hardcoded list of supported portlet apps now
    String appId = uiApp.getId();
    if (appId.equals("UISpaceActivityStreamPortlet") || appId.equals("UIUserActivityStreamPortlet")
        || appId.equals("UIMembersPortlet") || appId.equals("UIAllPeoplePortlet")
        || appId.equals("UIProfilePortlet") || appId.equals("UIConnectionsPortlet")) {
      try {
        WebuiRequestContext requestContext = (WebuiRequestContext) WebuiRequestContext.getCurrentInstance();
        JavascriptManager jsManager = requestContext.getJavascriptManager();
        RequireJS requireJS = jsManager.getRequireJS();

        Object obj = requestContext.getAttribute(USER_INIT_SCRIPT);
        if (obj == null) {
          String userName = requestContext.getRemoteUser();
          if (LOG.isDebugEnabled()) {
            LOG.debug(">> Activating app for Meet Button: " + uiApp + ", user " + userName);
          }

          requireJS.require("SHARED/exoMoxtra", "moxtra");
          if (isAuthorized()) {
            requireJS.addScripts("moxtra.initUser(\"" + userName + "\");");
          } else {
            requireJS.addScripts("moxtra.initUser(\"" + userName + "\", \"" + getAuthorizationLink() + "\");");
          }
          requestContext.setAttribute(USER_INIT_SCRIPT, userName);
        } else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("<< Application already activated for Meet Button: " + uiApp + ", user " + obj);
          }
        }
        requireJS.addScripts("moxtra.initMeetButton('" + appId + "');");
      } catch (OAuthSystemException e) {
        LOG.error("Error activating app for Meet Button: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Reset associated WebUI app in the context request (current thread). Moxtra app cannot be used after the
   * resetting without reactivation.
   */
  public void reset() {
    this.requestComponent.remove();
  }

  /**
   * {@inheritDoc}
   */
  public void deactivate(UIApplication uiApp) {
    this.uiApp.remove();
    reset();
  }

  public boolean isInitialized() {
    return this.moxtra != null;
  }

  // // ********* internals ***********

  protected MoxtraService moxtra() throws MoxtraNotInitializedException {
    MoxtraService moxtra = this.moxtra;
    if (moxtra != null) {
      return moxtra;
    } else {
      throw new MoxtraNotInitializedException("Moxtra application not initialized");
    }
  }

  protected UIContainer comp() throws MoxtraNotActivatedException {
    UIContainer comp = this.requestComponent.get();
    if (comp != null) {
      return comp;
    } else {
      throw new MoxtraNotActivatedException("Moxtra application not activated");
    }
  }

  /**
   * Merge component configurations: event with listeners and if template defined, from extension to original
   * component config. When template defined in extension it will replace the original.
   * 
   * @throws Exception
   */
  @SuppressWarnings("rawtypes")
  public void mergeConfigs(Component original, Class<? extends UIComponent> clazz) throws Exception {
    if (!mergedConfigs.contains(original)) {
      WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
      WebuiApplication webuiApp = (WebuiApplication) context.getApplication();
      Component extension = webuiApp.getConfigurationManager().getComponentConfig(clazz, null);
      Set<Event> extEvents = new LinkedHashSet<Event>(extension.getEvents());
      List<Event> events = original.getEvents();
      for (Event oe : events) {
        String eventName = oe.getName();
        for (Iterator<Event> eeiter = extEvents.iterator(); eeiter.hasNext();) {
          Event ee = eeiter.next();
          if (eventName.equals(ee.getName())) {
            // same name event: we merge extension listeners to the original instance

            // listener names (set to avoid duplicates)
            Set<String> names = new LinkedHashSet<String>();
            names.addAll(oe.getListeners()); // first original
            names.addAll(ee.getListeners()); // extended after
            oe.setListeners(new ArrayList<String>(names));

            // listeners instances (set to avoid duplicates)
            // ensure event configs cached listeners internally
            original.getUIComponentEventConfig(eventName);
            extension.getUIComponentEventConfig(eventName);
            Set<EventListener> listeners = new LinkedHashSet<EventListener>();
            listeners.addAll(oe.getCachedEventListeners());
            listeners.addAll(ee.getCachedEventListeners());
            oe.setCachedEventListeners(new ArrayList<EventListener>(listeners));

            // remove merged from ext events, to later add not merged to the original as new events
            eeiter.remove();
          }
        }
      }
      if (extEvents.size() > 0) {
        events.addAll(extEvents);
        // XXX we need reset internal map to let it be re-populated with added new events
        FieldUtils.writeDeclaredField(original, "eventMap", null, true);
      }
      if (extension.getTemplate() != null && extension.getTemplate().length() > 0) {
        // TODO implement template replacement: use new instance will be better?
        FieldUtils.writeDeclaredField(original, "template", extension.getTemplate(), true);
      }
      mergedConfigs.add(original);
    }
  }
}
