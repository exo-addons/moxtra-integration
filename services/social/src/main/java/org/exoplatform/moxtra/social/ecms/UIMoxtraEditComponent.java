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
package org.exoplatform.moxtra.social.ecms;

import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.social.MoxtraSocialException;
import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPopupComponent;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIMoxtraEditComponent.java 00000 May 20, 2015 pnedonosko $
 * 
 */
@ComponentConfig(template = "classpath:templates/moxtra/social/ecms/UIMoxtraEditComponent.gtmpl")
public class UIMoxtraEditComponent extends UIContainer implements UIPopupComponent {

  protected static final Log    LOG = ExoLogger.getLogger(UIMoxtraEditComponent.class);

  protected MoxtraSocialService moxtra;

  protected Node                pageNode;
  
  protected boolean isPageCreating;

  /**
   * 
   */
  public UIMoxtraEditComponent() {
    // TODO
  }

  public void initMoxtra(MoxtraSocialService moxtra, Node pageNode, boolean isPageCreating) {
    this.moxtra = moxtra;
    this.pageNode = pageNode;
    this.isPageCreating = isPageCreating;
  }

  public String getSpaceName() throws MoxtraSocialException {
    return moxtra.getBinderSpace().getSpace().getPrettyName();
  }

  public String getBinderId() throws MoxtraSocialException {
    return moxtra.getBinderSpace().getBinder().getBinderId();
  }

  /**
   * @return the pageNodeUUID
   * @throws RepositoryException
   */
  public String getPageNodeUUID() throws RepositoryException {
    return pageNode.getUUID();
  }

  public boolean isPageCreating() throws MoxtraClientException, RepositoryException, MoxtraException {
    return isPageCreating;
  }

  public Long getPageId() throws MoxtraClientException, RepositoryException, MoxtraException {
    return moxtra.getBinderSpace().getPage(pageNode).getId();
  }

  public String getAuthLink() throws MoxtraSocialException,
                             OAuthSystemException,
                             MoxtraConfigurationException {
    return moxtra.getOAuth2Link();
  }

  public boolean isAuthorized() throws MoxtraSocialException {
    return moxtra.isAuthorized();
  }

  public String getString(String id) throws Exception {
    WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
    ResourceBundle res = context.getApplicationResourceBundle();
    try {
      return res.getString(id);
    } catch (MissingResourceException e) {
      return id;
    }
  }

  public void showError(Throwable e) {
    LOG.error("Error opening EditInMoxtra form", e);
    UIApplication uiApp = getAncestorOfType(UIApplication.class);
    uiApp.addMessage(new ApplicationMessage("Moxtra.editInMoxtraError", null, ApplicationMessage.ERROR));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activate() {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deActivate() {
    // nothing
  }

}
