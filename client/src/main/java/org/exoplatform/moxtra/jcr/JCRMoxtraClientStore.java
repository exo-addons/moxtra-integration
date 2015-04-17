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
package org.exoplatform.moxtra.jcr;

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.moxtra.MoxtraClientStore;
import org.exoplatform.moxtra.MoxtraStoreException;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraClientInitializer;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.oauth2.AccessToken;
import org.exoplatform.moxtra.oauth2.AccessTokenRefreshListener;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;

import java.util.Calendar;
import java.util.Date;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: JCRMoxtraClientStore.java 00000 Mar 25, 2015 pnedonosko $
 * 
 */
public class JCRMoxtraClientStore extends BaseComponentPlugin implements MoxtraClientStore {

  protected static final Log LOG = ExoLogger.getLogger(JCRMoxtraClientStore.class);

  protected class ClientListener implements AccessTokenRefreshListener {

    protected final String tokenNodeUUID;

    protected ClientListener(String tokenNodeUUID) {
      this.tokenNodeUUID = tokenNodeUUID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTokenRefresh(AccessToken token) {
      if (token.isInitialized()) {
        try {
          SessionProvider sp = jcrSessions.getSystemSessionProvider(null);
          ManageableRepository currentRepo = jcrService.getCurrentRepository();
          Session userSession = sp.getSession(currentRepo.getConfiguration().getDefaultWorkspaceName(),
                                              currentRepo);
          Node tokenNode = userSession.getNodeByUUID(tokenNodeUUID);
          persistToken(tokenNode, token);
          tokenNode.save();
        } catch (RepositoryException e) {
          LOG.error("Error saving client token", e);
        }
      }
    }
  }

  protected final RepositoryService      jcrService;

  protected final SessionProviderService jcrSessions;

  protected final NodeHierarchyCreator   nodeCreator;

  /**
   * 
   */
  public JCRMoxtraClientStore(RepositoryService jcrService,
                              SessionProviderService jcrSessions,
                              NodeHierarchyCreator nodeCreator) {
    this.jcrService = jcrService;
    this.jcrSessions = jcrSessions;
    this.nodeCreator = nodeCreator;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void saveUser(MoxtraClient client, MoxtraUser user) throws MoxtraStoreException {
    // TODO Auto-generated method stub

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MoxtraUser readUser(MoxtraClient client) throws MoxtraStoreException {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   * 
   * @throws RepositoryException
   */
  @Override
  // TODO NOT USED
  public void saveMeet(MoxtraClient client, MoxtraMeet meet) throws MoxtraStoreException {
    try {
      // 
      Node userNode = userNode();

      Node meetsNode;
      try {
        meetsNode = JCR.getMeets(userNode);
      } catch(PathNotFoundException e) {
        meetsNode = JCR.addMeets(userNode);
      }

      Node meetNode;
      if (meet.isNew()) {
        // create meet node from the event node
        meetNode = meetsNode.addNode(meet.getBinderId());
        Node usersNode = JCR.addUsers(meetNode);
        for (MoxtraUser participant : meet.getUsers()) {
          Node pnode = usersNode.addNode(participant.getId());
          JCR.setId(pnode, participant.getId());
          JCR.setName(pnode, participant.getName());
          JCR.setEmail(pnode, participant.getEmail());
        }
      } else {
        // update local node
        meetNode = meetsNode.getNode(meet.getBinderId());
        Node usersNode = JCR.getUsers(meetNode);
        if (meet.isUsersRemoved()) {
          for (MoxtraUser removed : meet.getRemovedUsers()) {
            usersNode.getNode(removed.getId()).remove();
          }
        }
        if (meet.isUsersAdded()) {
          for (MoxtraUser participant : meet.getAddedUsers()) {
            Node pnode = usersNode.addNode(participant.getId());
            JCR.setId(pnode, participant.getId());
            JCR.setName(pnode, participant.getName());
            JCR.setEmail(pnode, participant.getEmail());
          }
        }
      }
      // meet metadata
      JCR.setId(meetNode, meet.getBinderId());
      JCR.setName(meetNode, meet.getName());
      JCR.setAgenda(meetNode, meet.getAgenda());
      JCR.setStartMeetUrl(meetNode, meet.getStartMeetUrl());
      JCR.setStartTime(meetNode, meet.getStartTime());
      JCR.setEndTime(meetNode, meet.getEndTime());
      JCR.setCreatedTime(meetNode, meet.getCreatedTime());
      JCR.setUpdatedTime(meetNode, meet.getUpdatedTime());
      JCR.setAutoRecording(meetNode, meet.getAutoRecording());
      JCR.setRevision(meetNode, meet.getRevision());
      JCR.setSessionKey(meetNode, meet.getSessionKey());
      // JCR.setThumbnailUrl(meetNode, meet.getThumbnailUrl());
      // JCR.setSessionId(meetNode, meet.getSessionId());

      if (meetNode.isNew()) {
        meetsNode.save();
      } else {
        meetNode.save();
      }
    } catch (RepositoryException e) {
      throw new MoxtraStoreException("Error saving Moxtra meet " + meet.getName(), e);
    } catch (Exception e) {
      throw new MoxtraStoreException("Error saving Moxtra meet " + meet.getName(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MoxtraMeet readMeet(MoxtraClient client) throws MoxtraStoreException {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void save(MoxtraClient client) throws MoxtraStoreException {
    try {
      Node userNode = userNode();
      if (!JCR.isUserStore(userNode)) {
        JCR.addUserStore(userNode);
        // TODO save moxtra:profile node with user info if it authorized
        // otherwise, need do that on refresh token... other event?
        userNode.save();
      }

      saveListenToken(userNode, client);

      // TODO save other user data (if required it locally)
      // MoxtraUser user = client.getCurrentUser();
    } catch (Exception e) {
      throw new MoxtraStoreException("Error saving Moxtra client", e);
    }
  }

  /**
   * {@inheritDoc}
   * 
   */
  @Override
  public boolean load(MoxtraClient client) throws MoxtraStoreException {
    try {
      Node userNode = userNode();
      if (JCR.isUserStore(userNode)) {
        return loadListenToken(userNode, client);
      } else {
        return false;
      }
    } catch (Exception e) {
      throw new MoxtraStoreException("Error loading Moxtra client", e);
    }
  }

  // ************** internals **************

  protected Node userNode() throws Exception {
    // TODO cleanup
    // String workspaceName = event.getRequestContext().getRequestParameter("workspaceName");

    String currentUser = ConversationState.getCurrent().getIdentity().getUserId();
    return nodeCreator.getUserNode(jcrSessions.getSystemSessionProvider(null), currentUser);
  }

  /**
   * Save client token in given user node (should be already moxtra:userStore).
   * 
   * @param userNode {@link Node} of type moxtra:userStore
   * @param client {@link MoxtraClient}
   * @throws RepositoryException
   */
  protected void saveListenToken(Node userNode, MoxtraClient client) throws RepositoryException {
    MoxtraClientInitializer cli = new MoxtraClientInitializer(client);

    Node tokenNode;
    try {
      tokenNode = JCR.getOAuth2AccessToken(userNode);
    } catch (PathNotFoundException e) {
      tokenNode = JCR.addOAuth2AccessToken(userNode);
    }

    AccessToken accesstToken = cli.getClientToken();
    if (accesstToken.isInitialized()) {
      persistToken(tokenNode, accesstToken);
      if (tokenNode.isNew()) {
        userNode.save();
      } else {
        tokenNode.save();
      }
    } else if (tokenNode.isNew()) {
      // need save token node for future listener invocation
      userNode.save();
    }

    // node should exist and be referenceable ("moxtra:accessTokenStore")
    accesstToken.addListener(new ClientListener(tokenNode.getUUID()), true);
  }

  /**
   * Persist client token data to given user node (should be already moxtra:accessTokenStore).
   * 
   * @param tokenNode {@link Node} of type moxtra:accessTokenStore
   * @param accesstToken {@link AccessToken}
   * @throws RepositoryException
   */
  protected void persistToken(Node tokenNode, AccessToken accesstToken) throws RepositoryException {
    JCR.setAccessToken(tokenNode, accesstToken.getAccessToken());
    String refreshToken = accesstToken.getRefreshToken();
    if (refreshToken != null) {
      JCR.setRefreshToken(tokenNode, refreshToken);
    }
    JCR.setExpirationTime(tokenNode, accesstToken.getExpirationTime());
    String scope = accesstToken.getScope();
    if (scope != null) {
      JCR.setScope(tokenNode, scope);
    }

    Date now = Calendar.getInstance().getTime();
    try {
      JCR.getCreatedTime(tokenNode);
    } catch (PathNotFoundException e) {
      JCR.setCreatedTime(tokenNode, now);
    }
    JCR.setUpdatedTime(tokenNode, now);
  }

  protected boolean loadListenToken(Node userNode, MoxtraClient client) throws RepositoryException {
    try {
      Node tokenNode = JCR.getOAuth2AccessToken(userNode);
      MoxtraClientInitializer cli = new MoxtraClientInitializer(client);

      AccessToken savedToken = readToken(tokenNode);

      cli.initClientToken(savedToken);

      // node should exist and be referenceable ("moxtra:accessTokenStore")
      // add listener to token in client
      cli.getClientToken().addListener(new ClientListener(tokenNode.getUUID()), false);
      return true;
    } catch (PathNotFoundException e) {
      // token node not found, we cannot load a token
      return false;
    }
  }

  /**
   * Read client token data from given user node (should be already moxtra:accessTokenStore).
   * 
   * @param tokenNode {@link Node} of type moxtra:accessTokenStore
   * @return {@link AccessToken}
   * @throws RepositoryException
   */
  protected AccessToken readToken(Node tokenNode) throws RepositoryException {
    AccessToken newToken = AccessToken.newToken();
    String refreshToken;
    try {
      refreshToken = JCR.getRefreshToken(tokenNode).getString();
    } catch (PathNotFoundException e) {
      refreshToken = null;
    }
    String scope;
    try {
      scope = JCR.getScope(tokenNode).getString();
    } catch (PathNotFoundException e) {
      scope = null;
    }
    newToken.load(JCR.getAccessToken(tokenNode).getString(), refreshToken, JCR.getExpirationTime(tokenNode)
                                                                              .getDate(), scope);
    return newToken;
  }

}
