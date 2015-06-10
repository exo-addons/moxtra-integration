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

import org.exoplatform.moxtra.client.MoxtraBinder;

import java.util.Calendar;
import java.util.Date;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

/**
 * Moxtra storage utility methods for JCR.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraStorage.java 00000 Mar 19, 2015 pnedonosko $
 * 
 */
public class JCR {

  public static final String NODETYPE_SERVICES           = "moxtra:services";

  public static final String NODETYPE_REFERENCEABLE      = "moxtra:referenceable";

  public static final String NODETYPE_USER_STORE         = "moxtra:userStore";

  public static final String NODETYPE_BASE               = "moxtra:base";

  public static final String NODETYPE_OBJECT             = "moxtra:object";

  public static final String NODETYPE_BINDER_OBJECT      = "moxtra:binderObject";

  public static final String NODETYPE_BINDERS_LIST       = "moxtra:bindersList";

  public static final String NODETYPE_MEET_OBJECT        = "moxtra:meetObject";

  public static final String NODETYPE_MEETS_LIST         = "moxtra:meetsList";

  public static final String NODETYPE_USER_OBJECT        = "moxtra:userObject";

  public static final String NODETYPE_USERS_LIST         = "moxtra:usersList";

  public static final String NODETYPE_PAGES_LIST         = "moxtra:pagesList";

  public static final String NODETYPE_PAGE_DOCUMENT      = "moxtra:pageDocument";

  public static final String NODETYPE_PAGE_CONTENT       = "moxtra:pageContent";

  public static final String NODETYPE_ACCESS_TOKEN_STORE = "moxtra:accessTokenStore";

  public static final String VALUE_CREATING_ID           = "_creating";
  
  public static final String VALUE_EMPTY   = "";

  public static boolean isUserStore(Node node) throws RepositoryException {
    return node.isNodeType(NODETYPE_USER_STORE);
  }

  public static void addUserStore(Node node) throws RepositoryException {
    node.addMixin(NODETYPE_USER_STORE);
    // owner and creation time for information purpose
    node.setProperty("moxtra:creator", node.getSession().getUserID());
    node.setProperty("moxtra:createdTime", Calendar.getInstance());
  }

  public static void removeUserStore(Node node) throws RepositoryException {
    try {
      node.getProperty("moxtra:creator").remove();
    } catch (PathNotFoundException e) {
      // ignore it
    }
    try {
      node.getProperty("moxtra:createdTime").remove();
    } catch (PathNotFoundException e) {
      // ignore it
    }

    for (NodeIterator niter = node.getNodes(); niter.hasNext();) {
      Node child = niter.nextNode();
      if (child.isNodeType(NODETYPE_BASE)) {
        child.remove();
      }
    }
    node.removeMixin(NODETYPE_USER_STORE);
  }

  public static boolean isServices(Node node) throws RepositoryException {
    return node.isNodeType(NODETYPE_SERVICES);
  }

  public static void addServices(Node node) throws RepositoryException {
    node.addMixin(NODETYPE_SERVICES);
    // owner and creation time for information purpose
    node.setProperty("moxtra:creator", node.getSession().getUserID());
    node.setProperty("moxtra:createdTime", Calendar.getInstance());
  }

  public static void removeServices(Node node) throws RepositoryException {
    // TODO cleanup
    // try {
    // node.getProperty("moxtra:creator").remove();
    // } catch (PathNotFoundException e) {
    // // ignore it
    // }
    // try {
    // node.getProperty("moxtra:createdTime").remove();
    // } catch (PathNotFoundException e) {
    // // ignore it
    // }

    // removeMoxtraItems(node);
    for (NodeIterator niter = node.getNodes(); niter.hasNext();) {
      cleanBaseNodeReferences(niter.nextNode());
    }
    node.removeMixin(NODETYPE_SERVICES); // this also removes the type nodes
  }

  protected static void removeMoxtraItems(Node node) throws RepositoryException {
    // Nodes will be removed by removeBaseNode() in deep traversing
    // for (NodeIterator miter = node.getNodes("moxtra:*"); miter.hasNext();) {
    // miter.nextNode().remove();
    // }
    for (NodeIterator niter = node.getNodes(); niter.hasNext();) {
      removeBaseNode(niter.nextNode());
    }
    for (PropertyIterator miter = node.getProperties("moxtra:*"); miter.hasNext();) {
      miter.nextProperty().remove();
    }
  }

  protected static void removeBaseNode(Node node) throws RepositoryException {
    if (node.isNodeType(NODETYPE_BASE)) {
      // clean child references recursively
      cleanBaseNodeReferences(node);
      node.remove();
    }
  }

  protected static void cleanBaseNodeReferences(Node node) throws RepositoryException {
    if (node.isNodeType(NODETYPE_BASE)) {
      for (NodeIterator niter = node.getNodes(); niter.hasNext();) {
        cleanBaseNodeReferences(niter.nextNode());
      }
      removeReferences(node);
    }
  }

  protected static void removeReferences(Node node) throws RepositoryException {
    for (PropertyIterator piter = node.getReferences(); piter.hasNext();) {
      Property refp = piter.nextProperty();
      Node target = refp.getParent();
      // Remove Reference property prior the removing mixins on target to avoid infinitive loops,
      // but Reference property should not be mandatory!
      refp.remove();
      if (target.isNodeType(NODETYPE_REFERENCEABLE)) {
        removeMoxtraMixins(target);
      }
    }
  }

  protected static void removeMoxtraMixins(Node node) throws RepositoryException {
    // moxtra referenceable itself can be referenced from other moxtra item, e.g. page document and
    // its conversations content
    removeReferences(node);
    for (NodeType nt : node.getMixinNodeTypes()) {
      String mixinName = nt.getName();
      if (mixinName.startsWith("moxtra:")) {
        node.removeMixin(mixinName);
      }
    }
    removeMoxtraItems(node);
  }

  // ********* moxtra:accessTokenStore ***********

  public static Node addOAuth2AccessToken(Node parentNode) throws RepositoryException {
    return parentNode.addNode("moxtra:oauth2AccessToken");
  }

  public static Node getOAuth2AccessToken(Node parentNode) throws RepositoryException {
    return parentNode.getNode("moxtra:oauth2AccessToken");
  }

  public static Property setAccessToken(Node tokenNode, String accessToken) throws RepositoryException {
    return tokenNode.setProperty("moxtra:accessToken", accessToken);
  }

  public static Property getAccessToken(Node tokenNode) throws RepositoryException {
    return tokenNode.getProperty("moxtra:accessToken");
  }

  public static Property setRefreshToken(Node tokenNode, String refreshToken) throws RepositoryException {
    return tokenNode.setProperty("moxtra:refreshToken", refreshToken);
  }

  public static Property getRefreshToken(Node tokenNode) throws RepositoryException {
    return tokenNode.getProperty("moxtra:refreshToken");
  }

  public static Property setExpirationTime(Node tokenNode, Calendar time) throws RepositoryException {
    return tokenNode.setProperty("moxtra:expirationTime", time);
  }

  public static Property getExpirationTime(Node tokenNode) throws RepositoryException {
    return tokenNode.getProperty("moxtra:expirationTime");
  }

  public static Property setScope(Node tokenNode, String scope) throws RepositoryException {
    return tokenNode.setProperty("moxtra:scope", scope);
  }

  public static Property getScope(Node tokenNode) throws RepositoryException {
    return tokenNode.getProperty("moxtra:scope");
  }

  // ///////

  public static Node addBinders(Node userNode) throws RepositoryException {
    return userNode.addNode("moxtra:binders");
  }

  public static Node getBinders(Node userNode) throws RepositoryException {
    return userNode.getNode("moxtra:binders");
  }

  public static Node addMeet(Node userNode) throws RepositoryException {
    return userNode.addNode("moxtra:meet");
  }

  public static Node getMeet(Node userNode) throws RepositoryException {
    return userNode.getNode("moxtra:meet");
  }

  public static boolean hasMeet(Node userNode) throws RepositoryException {
    return userNode.hasNode("moxtra:meet");
  }

  public static Node addMeets(Node userNode) throws RepositoryException {
    return userNode.addNode("moxtra:meets");
  }

  public static Node getMeets(Node userNode) throws RepositoryException {
    return userNode.getNode("moxtra:meets");
  }

  public static Node addUsers(Node userNode) throws RepositoryException {
    return userNode.addNode("moxtra:users");
  }

  public static Node getUsers(Node userNode) throws RepositoryException {
    return userNode.getNode("moxtra:users");
  }

  public static boolean hasUsers(Node userNode) throws RepositoryException {
    return userNode.hasNode("moxtra:users");
  }

  public static Node addBinder(Node userNode) throws RepositoryException {
    return userNode.addNode("moxtra:binder");
  }

  public static Node getBinder(Node userNode) throws RepositoryException {
    return userNode.getNode("moxtra:binder");
  }

  public static boolean hasBinder(Node userNode) throws RepositoryException {
    return userNode.hasNode("moxtra:binder");
  }

  public static Node addPages(Node binderNode) throws RepositoryException {
    return binderNode.addNode("moxtra:pages");
  }

  public static Node getPages(Node binderNode) throws RepositoryException {
    return binderNode.getNode("moxtra:pages");
  }

  public static boolean hasPages(Node binderNode) throws RepositoryException {
    return binderNode.hasNode("moxtra:pages");
  }

  public static Node addPage(Node binderNode, String nodeName, String pageName) throws RepositoryException {
    Node pagesNode;
    try {
      pagesNode = getPages(binderNode);
    } catch (PathNotFoundException e) {
      pagesNode = addPages(binderNode);
    }
    Node pageNode = pagesNode.addNode(nodeName);
    // set initial fake page ID
    setId(pageNode, VALUE_CREATING_ID);
    setName(pageNode, pageName);
    return pageNode;
  }

  public static boolean hasPage(Node binderNode, String nodeName) throws RepositoryException {
    return binderNode.hasNode("moxtra:pages/" + nodeName);
  }

  public static Node getPage(Node binderNode, String nodeName) throws RepositoryException {
    return binderNode.getNode("moxtra:pages/" + nodeName);
  }

  public static Property addPageRef(Node document, Node pageNode) throws RepositoryException {
    return document.setProperty("moxtra:page", pageNode);
  }

  public static boolean hasPageRef(Node document) throws RepositoryException {
    return document.hasProperty("moxtra:page");
  }

  public static Property getPageRef(Node document) throws RepositoryException {
    return document.getProperty("moxtra:page");
  }

  public static NodeIterator findPageDocument(Node spaceNode, String pageName) throws RepositoryException {
    QueryManager qm = spaceNode.getSession().getWorkspace().getQueryManager();
    Query q = qm.createQuery("SELECT * FROM " + NODETYPE_PAGE_DOCUMENT + " WHERE exo:title='" + pageName
        + "' AND jcr:path LIKE '" + spaceNode.getPath() + "/%'", Query.SQL);
    NodeIterator res = q.execute().getNodes();
    if (res.getSize() == 0) {
      // try by exo:name
      q = qm.createQuery("SELECT * FROM " + NODETYPE_PAGE_DOCUMENT + " WHERE exo:name='" + pageName
          + "' AND jcr:path LIKE '" + spaceNode.getPath() + "/%'", Query.SQL);
      res = q.execute().getNodes();
      // TODO page also can be created by node name (if not exo:title or exo:name were found)
    }

    return res;
  }

  public static void addPageDocument(Node document) throws RepositoryException {
    document.addMixin(NODETYPE_PAGE_DOCUMENT);
  }

  public static boolean isPageDocument(Node document) throws RepositoryException {
    return document.isNodeType(NODETYPE_PAGE_DOCUMENT);
  }

  public static NodeIterator findBinder(Node node, MoxtraBinder binder) throws RepositoryException {
    QueryManager qm = node.getSession().getWorkspace().getQueryManager();
    Query q = qm.createQuery("SELECT * FROM " + NODETYPE_BINDER_OBJECT + " WHERE moxtra:id='"
                                 + binder.getBinderId() + "' AND jcr:path LIKE '" + node.getPath() + "/%'",
                             Query.SQL);
    return q.execute().getNodes();
  }

  public static Property setCreatingTime(Node document, Date time) throws RepositoryException {
    Calendar cal;
    if (time != null) {
      cal = Calendar.getInstance();
      cal.setTime(time);
    } else {
      cal = null;
    }
    return document.setProperty("moxtra:creatingTime", cal);
  }

  public static Property getCreatingTime(Node document) throws RepositoryException {
    return document.getProperty("moxtra:creatingTime");
  }

  public static boolean hasCreatingTime(Node document) throws RepositoryException {
    return document.hasProperty("moxtra:creatingTime");
  }

  public static Property getOriginalFileName(Node node) throws RepositoryException {
    return node.getProperty("moxtra:originalFileName");
  }

  public static Property setOriginalFileName(Node node, String name) throws RepositoryException {
    return node.setProperty("moxtra:originalFileName", name);
  }

  public static Property getPageUrl(Node node) throws RepositoryException {
    return node.getProperty("moxtra:pageUrl");
  }

  public static Property setPageUrl(Node node, String url) throws RepositoryException {
    return node.setProperty("moxtra:pageUrl", url);
  }

  public static Property getId(Node node) throws RepositoryException {
    return node.getProperty("moxtra:id");
  }
  
  public static String getIdString(Node node) throws RepositoryException {
    String id = node.getProperty("moxtra:id").getString();
    return id.equals(VALUE_EMPTY) ? null : id;
  }

  public static Property setId(Node node, String id) throws RepositoryException {
    return node.setProperty("moxtra:id", id != null ? id : VALUE_EMPTY);
  }

  public static Property setId(Node node, Long id) throws RepositoryException {
    return node.setProperty("moxtra:id", id);
  }

  public static Property getUniqueId(Node node) throws RepositoryException {
    return node.getProperty("moxtra:uniqueId");
  }

  public static String getUniqueIdString(Node node) throws RepositoryException {
    try {
      return node.getProperty("moxtra:uniqueId").getString();
    } catch (PathNotFoundException e) {
      return null;
    }
  }

  public static boolean hasUniqueId(Node node) throws RepositoryException {
    return node.hasProperty("moxtra:uniqueId");
  }

  public static Property setUniqueId(Node node, String uniqueId) throws RepositoryException {
    return node.setProperty("moxtra:uniqueId", uniqueId);
  }

  public static Property getOrgId(Node node) throws RepositoryException {
    return node.getProperty("moxtra:orgId");
  }
  
  public static String getOrgIdString(Node node) throws RepositoryException {
    try {
      return node.getProperty("moxtra:orgId").getString();
    } catch (PathNotFoundException e) {
      return null;
    }
  }

  public static boolean hasOrgId(Node node) throws RepositoryException {
    return node.hasProperty("moxtra:orgId");
  }

  public static Property setOrgId(Node node, String orgId) throws RepositoryException {
    return node.setProperty("moxtra:orgId", orgId);
  }
  
  public static Property getPictureUri(Node node) throws RepositoryException {
    return node.getProperty("moxtra:pictureUri");
  }
  
  public static String getPictureUriString(Node node) throws RepositoryException {
    try {
      return node.getProperty("moxtra:pictureUri").getString();
    } catch (PathNotFoundException e) {
      return null;
    }
  }

  public static boolean hasPictureUri(Node node) throws RepositoryException {
    return node.hasProperty("moxtra:pictureUri");
  }

  public static Property setPictureUri(Node node, String pictureUri) throws RepositoryException {
    return node.setProperty("moxtra:pictureUri", pictureUri);
  }

  public static Property getName(Node node) throws RepositoryException {
    return node.getProperty("moxtra:name");
  }

  public static Property setName(Node node, String name) throws RepositoryException {
    return node.setProperty("moxtra:name", name);
  }

  public static Property getFirstName(Node node) throws RepositoryException {
    return node.getProperty("moxtra:firstName");
  }

  public static Property setFirstName(Node node, String name) throws RepositoryException {
    return node.setProperty("moxtra:firstName", name);
  }

  public static Property getLastName(Node node) throws RepositoryException {
    return node.getProperty("moxtra:lastName");
  }

  public static Property setLastName(Node node, String name) throws RepositoryException {
    return node.setProperty("moxtra:lastName", name);
  }

  public static Property getCreatedTime(Node node) throws RepositoryException {
    return node.getProperty("moxtra:createdTime");
  }

  public static Property getType(Node node) throws RepositoryException {
    return node.getProperty("moxtra:type");
  }

  public static Property setType(Node node, String name) throws RepositoryException {
    return node.setProperty("moxtra:type", name);
  }

  public static Property setCreatedTime(Node node, Date time) throws RepositoryException {
    Calendar cal;
    if (time != null) {
      cal = Calendar.getInstance();
      cal.setTime(time);
    } else {
      cal = null;
    }
    return node.setProperty("moxtra:createdTime", cal);
  }

  public static Property getUpdatedTime(Node node) throws RepositoryException {
    return node.getProperty("moxtra:updatedTime");
  }

  public static Property setUpdatedTime(Node node, Date time) throws RepositoryException {
    Calendar cal;
    if (time != null) {
      cal = Calendar.getInstance();
      cal.setTime(time);
    } else {
      cal = null;
    }
    return node.setProperty("moxtra:updatedTime", cal);
  }

  public static Property getEmail(Node node) throws RepositoryException {
    return node.getProperty("moxtra:email");
  }

  public static Property setEmail(Node node, String email) throws RepositoryException {
    return node.setProperty("moxtra:email", email);
  }

  public static Property getRevision(Node node) throws RepositoryException {
    return node.getProperty("moxtra:revision");
  }

  public static Property setRevision(Node node, Long revision) throws RepositoryException {
    return node.setProperty("moxtra:revision", revision);
  }

  public static Property getThumbnailUrl(Node node) throws RepositoryException {
    return node.getProperty("moxtra:thumbnailUrl");
  }

  public static Property setThumbnailUrl(Node node, String url) throws RepositoryException {
    return node.setProperty("moxtra:thumbnailUrl", url);
  }

  public static Property getSessionKey(Node node) throws RepositoryException {
    return node.getProperty("moxtra:sessionKey");
  }

  public static Property setSessionKey(Node node, String key) throws RepositoryException {
    return node.setProperty("moxtra:sessionKey", key);
  }

  public static Property getSessionId(Node node) throws RepositoryException {
    return node.getProperty("moxtra:sessionId");
  }

  public static Property setSessionId(Node node, String id) throws RepositoryException {
    return node.setProperty("moxtra:sessionId", id);
  }

  public static Property getStartMeetUrl(Node node) throws RepositoryException {
    return node.getProperty("moxtra:startMeetUrl");
  }

  public static Property setStartMeetUrl(Node node, String url) throws RepositoryException {
    return node.setProperty("moxtra:startMeetUrl", url);
  }

  public static Property getAgenda(Node node) throws RepositoryException {
    return node.getProperty("moxtra:agenda");
  }

  public static Property setAgenda(Node node, String agenda) throws RepositoryException {
    return node.setProperty("moxtra:agenda", agenda);
  }

  public static Property getAutoRecording(Node node) throws RepositoryException {
    return node.getProperty("moxtra:autoRecording");
  }

  public static Property setAutoRecording(Node node, Boolean autoRecording) throws RepositoryException {
    return node.setProperty("moxtra:autoRecording", autoRecording);
  }

  public static Property getStartTime(Node node) throws RepositoryException {
    return node.getProperty("moxtra:startTime");
  }

  public static Property setStartTime(Node node, Date time) throws RepositoryException {
    Calendar cal = Calendar.getInstance();
    cal.setTime(time);
    return node.setProperty("moxtra:startTime", cal);
  }

  public static Property getEndTime(Node node) throws RepositoryException {
    return node.getProperty("moxtra:endTime");
  }

  public static Property setEndTime(Node node, Date time) throws RepositoryException {
    Calendar cal = Calendar.getInstance();
    cal.setTime(time);
    return node.setProperty("moxtra:endTime", cal);
  }

}
