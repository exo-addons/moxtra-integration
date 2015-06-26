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
package org.exoplatform.moxtra;

import org.exoplatform.container.component.ComponentPlugin;
import org.exoplatform.moxtra.client.MoxtraAuthenticationException;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.picocontainer.Startable;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moxtra component in eXo container. It is an entry point to Moxtra support in eXo. <br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraService.java 00000 Feb 27, 2015 pnedonosko $
 * 
 */
public class MoxtraService implements Startable {

  protected static final Log                        LOG     = ExoLogger.getLogger(MoxtraService.class);

  protected final OrganizationService               orgService;

  protected OAuthClientConfiguration                oAuthConfig;

  protected MoxtraClientStore                       usersStore;

  protected ConcurrentHashMap<String, MoxtraClient> clients = new ConcurrentHashMap<String, MoxtraClient>();

  /**
   * No dependency constructor.
   */
  public MoxtraService() {
    this.orgService = null;
  }

  /**
   * Construct MoxtrService using organizational service.
   */
  public MoxtraService(OrganizationService orgService) {
    this.orgService = orgService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void start() {
    // ensure we have authorization config
    if (oAuthConfig == null) {
      throw new RuntimeException(new MoxtraConfigurationException("OAuth2 configuration not defined"));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void stop() {
    // cleanup
    clients.clear();
  }

  /**
   * Get {@link MoxtraClient} from clients pool. If no client pooled it will be created and stored
   * first. Best fit authentication model will be chosen for the client. <br>
   * 
   * @return {@link MoxtraClient}
   * @throws ConversationStateNotFoundException
   */
  public MoxtraClient getClient() {
    // TODO clear pool on user logout or removal: use listeners to related services
    String currentUser = Moxtra.currentUserName();
    MoxtraClient client = clients.get(currentUser);
    if (client == null) {
      synchronized (currentUser) {
        client = clients.get(currentUser);
        if (client == null) {
          clients.put(currentUser, client = createOAuthClient(currentUser));
        }
      }
    }
    return client;
  }

  /**
   * Get {@link MoxtraClient} for given eXo user in clients pool. If no client pooled it will be created and
   * stored first. Best fit authentication model will be chosen for the client. If user doesn't exist in
   * Organization service or no appropriate rights the current user has, then exception will be thrown.<br>
   * 
   * @return {@link MoxtraClient}
   * @throws {@link UserNotFoundException} when eXo user cannot be found by given user name
   */
  public MoxtraClient getClient(String userName) throws UserNotFoundException {
    try {
      User exoUser = orgService.getUserHandler().findUserByName(userName);
      if (exoUser == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("User not found in organization " + userName);
        }
        throw new UserNotFoundException("User not found in organization '" + userName + "'");
      }
    } catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Error searching user " + userName + " in organization", e);
      }
      throw new UserNotFoundException("Error searching user '" + userName + "' in organization", e);
    }

    // TODO clear pool on user logout or removal: use listeners to related services
    MoxtraClient client = clients.get(userName);
    if (client == null) {
      synchronized (userName) {
        client = clients.get(userName);
        if (client == null) {
          clients.put(userName, client = createOAuthClient(userName));
        }
      }
    }
    return client;
  }

  /**
   * Create Moxtra client for OAuth2 authorization method. This client instance will not be stored in the
   * clients pool. <br>
   * 
   * @param userName {@link String} eXo user name
   * @return {@link MoxtraClient}
   * @throws MoxtraException
   * @throws MoxtraAuthenticationException
   */
  protected MoxtraClient createOAuthClient(String userName) {
    // TODO create clients per eXo user with single HTTP pool!!!
    MoxtraClient client = new MoxtraClient(oAuthConfig.getClientId(),
                                           oAuthConfig.getClientSecret(),
                                           oAuthConfig.getClientSchema(),
                                           oAuthConfig.getClientHost(),
                                           oAuthConfig.getClientAuthMethod(),
                                           oAuthConfig.getClientOrgId(),
                                           userName,
                                           oAuthConfig.isAllowRemoteOrgUsers(),
                                           orgService);
    if (usersStore != null) {
      try {
        if (!usersStore.load(client, userName)) {
          client.authorizer().tryAuthorize();
          usersStore.save(client, userName);
        }
      } catch (MoxtraStoreException e) {
        LOG.error("Error processing client store operation: " + e.getMessage(), e);
      }
    } else {
      client.authorizer().tryAuthorize();
    }
    return client;
  }

  /**
   * Configuration for OAuth2 clients.
   * 
   * @return {@link OAuthClientConfiguration}
   */
  public OAuthClientConfiguration getOAuthConfig() {
    return oAuthConfig;
  }

  public void addPlugin(ComponentPlugin plugin) {
    if (plugin instanceof OAuthClientConfiguration) {
      this.oAuthConfig = (OAuthClientConfiguration) plugin;
      LOG.info("OAuth2 client configuration set to " + plugin);
    } else if (plugin instanceof MoxtraClientStore) {
      this.usersStore = (MoxtraClientStore) plugin;
      LOG.info("Client store set to " + plugin);
    } else {
      LOG.warn("Unrecognized plugin ignored " + plugin);
    }
  }

  /**
   * For internal user: return a blank content for future empty Moxtra Note.
   * 
   * @return {@link InputStream}
   */
  public InputStream getBlankNoteContent() {
    return getClass().getResourceAsStream("/blanks/empty-note.html");
  }

  /**
   * For internal user: return a blank content for future empty Moxtra Whiteboard.
   * 
   * @return {@link InputStream}
   */
  public InputStream getBlankWhiteboardContent() {
    return getClass().getResourceAsStream("/blanks/empty-draw-w.png");
  }
}
