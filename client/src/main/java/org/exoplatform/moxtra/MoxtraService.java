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
import org.exoplatform.services.security.ConversationState;
import org.picocontainer.Startable;

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

  protected static final Log                        LOG   = ExoLogger.getLogger(MoxtraService.class);

  protected OAuthClientConfiguration                oAuthConfig;

  protected MoxtraClientStore                       usersStore;

  protected ConcurrentHashMap<String, MoxtraClient> users = new ConcurrentHashMap<String, MoxtraClient>();

  /**
   * No dependency constructor.
   */
  public MoxtraService() {
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
    users.clear();
  }

  /**
   * Get {@link MoxtraClient} from clients pool. If no client pooled it will be created and stored
   * first. Best fit authentication model will be chosen for the client. <br>
   * 
   * @return {@link MoxtraClient}
   * @throws MoxtraConfigurationException
   */
  public MoxtraClient getClient() {
    // TODO clear pool on user logout or removal: use listeners to related services
    String currentUser = ConversationState.getCurrent().getIdentity().getUserId();
    MoxtraClient client = users.get(currentUser);
    if (client == null) {
      synchronized (currentUser) {
        client = users.get(currentUser);
        if (client == null) {
          users.put(currentUser, client = createOAuthClient());
        }
      }
    }
    return client;
  }

  /**
   * Create Moxtra client for OAuth2 authorization method. This client instance will not be stored in the
   * clients pool. <br>
   * 
   * @return
   * @throws MoxtraException
   * @throws MoxtraAuthenticationException
   */
  protected MoxtraClient createOAuthClient() {
    // TODO create clients per eXo user with single HTTP pool!!!
    MoxtraClient client = new MoxtraClient(oAuthConfig.getClientId(),
                                           oAuthConfig.getClientSecret(),
                                           oAuthConfig.getClientSchema(),
                                           oAuthConfig.getClientHost());
    if (usersStore != null) {
      try {
        if (!usersStore.load(client)) {
          usersStore.save(client);
        }
      } catch (MoxtraStoreException e) {
        LOG.error("Error processing client store operation: " + e.getMessage(), e);
      }
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

}
