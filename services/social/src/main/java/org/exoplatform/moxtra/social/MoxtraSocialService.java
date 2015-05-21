package org.exoplatform.moxtra.social;

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraAuthenticationException;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.client.MoxtraPage;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.commons.BaseMoxtraService;
import org.exoplatform.moxtra.jcr.JCR;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.scheduler.impl.JobSchedulerServiceImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.space.SpaceApplicationConfigPlugin;
import org.exoplatform.social.core.space.SpaceApplicationConfigPlugin.SpaceApplication;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.webui.Utils;
import org.picocontainer.Startable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraSocialService.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
public class MoxtraSocialService extends BaseMoxtraService implements Startable {

  public static final long   MAX_PAGE_CREATING_TIME = 1000 * 60 * 3;                                 // 3min
                                                                                                      // max
                                                                                                      // period
                                                                                                      // to
                                                                                                      // wait
                                                                                                      // for
                                                                                                      // page
                                                                                                      // created

  protected static final Log LOG                    = ExoLogger.getLogger(MoxtraSocialService.class);

  /**
   * Binder associated with Social space.
   */
  public class MoxtraBinderSpace {

    protected final Space               space;

    protected final MoxtraBinder        binder;

    protected final Map<String, String> creatingPages = new HashMap<String, String>();

    protected boolean                   isNew;

    protected MoxtraBinderSpace(Space space, MoxtraBinder binder, boolean isNew) throws Exception {
      this.space = space;
      this.binder = binder.editor();
      this.binder.editName(space.getDisplayName());
      // add also space users
      String currentUser = ConversationState.getCurrent().getIdentity().getUserId();
      Set<MoxtraUser> users = new LinkedHashSet<MoxtraUser>();
      for (String userId : space.getManagers()) {
        if (!currentUser.equals(userId)) {
          users.add(findUser(userId));
        }
      }
      for (String userId : space.getMembers()) {
        if (!currentUser.equals(userId)) {
          users.add(findUser(userId));
        }
      }
      for (MoxtraUser user : users) {
        this.binder.addUser(user);
      }
      this.isNew = isNew;
    }

    /**
     * @return the space
     */
    public Space getSpace() {
      return space;
    }

    /**
     * @return the binder
     */
    public MoxtraBinder getBinder() {
      return binder;
    }

    public boolean isNew() {
      return isNew || binder.isNew();
    }
    
    public void resetNew() {
      this.isNew = false;
    }

    /**
     * Get existing page for conversation.
     * 
     * @param nodeUUID
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public MoxtraPage getPage(String nodeUUID) throws RepositoryException,
                                              MoxtraClientException,
                                              MoxtraException {
      Node spaceNode = spaceNode();
      try {
        Node pageNode = spaceNode.getSession().getNodeByUUID(nodeUUID);
        return getPage(pageNode);
      } catch (ItemNotFoundException e) {
        throw new MoxtraSocialException("Document not found " + nodeUUID);
      }
    }

    /**
     * Get existing page for conversation.
     * 
     * @param document
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public MoxtraPage getPage(Node document) throws RepositoryException,
                                            MoxtraClientException,
                                            MoxtraException {
      if (JCR.isPageDocument(document)) {
        if (JCR.hasCreatingTime(document)) {
          throw new MoxtraSocialException("Conversation page not yet created for " + document.getName());
        } else {
          Node pageNode = JCR.getPageRef(document).getNode();
          return new MoxtraPage(JCR.getId(pageNode).getLong(),
                                JCR.getRevision(pageNode).getLong(),
                                0l,
                                0l,
                                JCR.getType(pageNode).getString(),
                                JCR.getName(pageNode).getString(),
                                JCR.getPageUrl(pageNode).getString(),
                                null,
                                null);
        }
      } else {
        throw new MoxtraSocialException("Document not a conversation page " + document.getName());
      }
    }

    /**
     * Check if given document (by its UUID) has a page for conversation. This method return
     * <code>false</code> for page currently creating.
     * 
     * @param nodeUUID
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public boolean hasPage(String nodeUUID) throws RepositoryException,
                                           MoxtraClientException,
                                           MoxtraException {
      Node spaceNode = spaceNode();
      try {
        Node pageNode = spaceNode.getSession().getNodeByUUID(nodeUUID);
        return hasPage(pageNode);
      } catch (ItemNotFoundException e) {
        throw new MoxtraSocialException("Document not found " + nodeUUID);
      }
    }

    /**
     * Check if given document has a page for conversation. This method return <code>false</code> for page
     * currently creating.
     * 
     * @param document
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public boolean hasPage(Node document) throws RepositoryException, MoxtraClientException, MoxtraException {
      return isPageNode(document, true);
    }

    /**
     * Create page for conversation. Page will not be created immediately in Moxtra and need wait and find for
     * it in the binder, then save locally.
     * 
     * @param document
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public void createPage(Node document) throws RepositoryException, MoxtraClientException, MoxtraException {
      MoxtraBinder binder = getBinder();

      // TODO create/upload page asynchronously

      // TODO other nodetypes?
      if (isPageNode(document, false)) {
        throw new MoxtraPageAlreadyException("Document already a conversation page " + document.getName());
      } else if (document.isNodeType("nt:file")) {
        // detect the doc type and choose Note or Draw (Whiteboard)
        Node data = document.getNode("jcr:content");
        if (data.isNodeType("nt:resource")) {
          MoxtraClient client = moxtra.getClient();
          String mimeType = data.getProperty("jcr:mimeType").getString();
          String docName = documentName(document);
          try {
            if (isNoteMimeType(mimeType) || isDrawMimeType(mimeType)) {
              // it is a Note or Draw should be
              client.pageUpload(binder, mimeType, data.getProperty("jcr:data").getStream(), docName);
            } else {
              // not supported document
              throw new MoxtraSocialException("Document type not supported for " + document.getName() + ": "
                  + mimeType);
            }
          } catch (OAuthProblemException e) {
            throw new MoxtraSocialException("Error creating page conversation " + docName + ". "
                + e.getMessage(), e);
          } catch (OAuthSystemException e) {
            throw new MoxtraSocialException("Error creating page conversation " + docName + ". "
                + e.getMessage(), e);
          }

          // add mixin and Moxtra specific info
          JCR.addPageDocument(document);
          document.save();
          JCR.setCreatingTime(document, new Date());
          JCR.setName(document, docName);
          document.save();
        } else {
          throw new MoxtraSocialException("Cannot determine MIME type of file " + document.getName()
              + ", content node type " + data.getPrimaryNodeType().getName());
        }
      } else {
        throw new MoxtraSocialException("Document not a file " + document.getName() + " "
            + document.getPrimaryNodeType().getName());
      }
    }

    /**
     * Find page conversation with given name in context binder space.
     * 
     * @param pageNodeUUID {@link String}
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public MoxtraPage findPage(String pageName) throws RepositoryException,
                                               MoxtraClientException,
                                               MoxtraException {
      if (!isNew()) {
        try {
          MoxtraBinder binder = moxtra.getClient().getBinder(getBinder().getBinderId());
          for (MoxtraPage page : binder.getPages()) {
            if (pageName.equals(page.getOriginalFileName())) {
              // TODO do we need check page type or other things?
              // we could ensure it was just created by created_time

              // TODO add page node in local binder node

              // TODO update page document with references

              // return document.isNodeType("moxtra:pageDocument");

              return page;
            }
          }
        } catch (OAuthSystemException e) {
          throw new MoxtraSocialException("Error searching page conversation " + pageName, e);
        } catch (OAuthProblemException e) {
          throw new MoxtraSocialException("Error searching page conversation " + pageName, e);
        }
      }
      return null;
    }

    // ******* internals ******

    protected Node spaceNode() throws MoxtraSocialException {
      try {
        return MoxtraSocialService.this.spaceNode(getSpace());
      } catch (Exception e) {
        throw new MoxtraSocialException("Error reading space node " + getSpace().getDisplayName());
      }
    }

    protected MoxtraUser findUser(String userId) throws Exception {
      User orgUser = orgService.getUserHandler().findUserByName(userId);
      return new MoxtraUser(orgUser.getEmail());
    }

    @Deprecated
    protected synchronized void creatingPage(String userId, String pageName, String documentUUID) {
      creatingPages.put(userId + ":" + pageName, documentUUID);
    }

    @Deprecated
    protected synchronized String createdPage(String userId, String pageName) {
      String key = userId + ":" + pageName;
      String documentUUID = creatingPages.get(key);
      if (documentUUID != null) {
        creatingPages.remove(key);
      }
      return documentUUID;
    }

    protected boolean isPageNode(Node document, boolean refresh) throws RepositoryException,
                                                                MoxtraClientException,
                                                                MoxtraException {
      if (JCR.isPageDocument(document)) {
        try {
          Property creatingTimeProp = JCR.getCreatingTime(document);
          Calendar creatingTime = creatingTimeProp.getDate();
          if (refresh) {
            // Node pageNode = JCR.getPageRef(document).getNode();
            Property pageNameProp = JCR.getName(document);
            String pageName = pageNameProp.getString();
            MoxtraPage page = findPage(pageName);
            if (page != null) {
              // page already created in Moxtra, add local node
              Node pageNode = writePage(page, document.getName(), pageName);
              // update document node (remove creating time props)
              creatingTimeProp.remove();
              pageNameProp.remove();
              JCR.addPageRef(document, pageNode);
              document.save();
              return true;
            }
          } else {
            // w/o checking in Moxtra we check for time how long this page is creating and if longer of some
            // allowed period - we assume it is not a page (removed/moved/renamed in Moxtra or like that)
            if (System.currentTimeMillis() - creatingTime.getTimeInMillis() < MAX_PAGE_CREATING_TIME) {
              return true;
            }
          }
        } catch (PathNotFoundException e) {
          return JCR.hasPageRef(document);
        }

        // TODO
        // Node pageNode = JCR.getPageRef(document).getNode();
        // Property pageIdProp = JCR.getId(pageNode);
        // if (PropertyType.LONG != pageIdProp.getType()) {
        // // it's "creating" page
        // if (refresh) {
        // MoxtraPage page = findPage(JCR.getName(pageNode).getString());
        // if (page != null) {
        // // page already created in Moxtra, update local node
        // writePage(pageNode, page);
        // return true;
        // }
        // }
        // } else {
        // return true;
        // }
      }
      return false;
    }

    protected Node writePage(MoxtraPage page, String nodeName, String pageName) throws MoxtraSocialException,
                                                                               RepositoryException {
      Node spaceNode = spaceNode();
      Node binderNode = JCR.getBinder(spaceNode);
      Node pageNode = JCR.addPage(binderNode, nodeName, pageName);

      JCR.setId(pageNode, page.getId());
      JCR.setRevision(pageNode, page.getRevision());
      JCR.setPageUrl(pageNode, page.getUrl());
      JCR.setType(pageNode, page.getType());

      binderNode.save();

      return pageNode;
    }
  }

  protected final SessionProviderService  sessionsProvider;

  /**
   * OrganizationService to find eXo users email.
   */
  protected final OrganizationService     orgService;

  protected final JobSchedulerServiceImpl schedulerService;

  protected final NodeHierarchyCreator    hierarchyCreator;

  protected final ManageDriveService      driveService;

  protected final Set<String>             noteMimeTypes  = new HashSet<String>();

  protected final Set<String>             drawMimeTypes  = new HashSet<String>();

  protected final Set<SpaceApplication>   addedSpaceApps = new HashSet<SpaceApplication>();

  /**
   * @throws MoxtraConfigurationException
   * 
   */
  public MoxtraSocialService(MoxtraService moxtra,
                             SessionProviderService sessionProviderService,
                             NodeHierarchyCreator hierarchyCreator,
                             OrganizationService orgService,
                             JobSchedulerServiceImpl schedulerService,
                             ManageDriveService driveService) {
    super(moxtra);
    this.sessionsProvider = sessionProviderService;
    this.hierarchyCreator = hierarchyCreator;
    this.orgService = orgService;
    this.schedulerService = schedulerService;
    this.driveService = driveService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void start() {
    // add space apps to space service on the start, to add to existing list
    SpaceService spaceService = spaceService();
    SpaceApplicationConfigPlugin appPlugin = spaceService.getSpaceApplicationConfigPlugin();
    for (SpaceApplication app : addedSpaceApps) {
      appPlugin.addToSpaceApplicationList(app);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void stop() {
    // nothing
  }

  public synchronized void configureMimeTypePlugin(MimeTypePlugin plugin) {
    updateMimetypes(noteMimeTypes, plugin.getNoteAddedTypes(), plugin.getNoteRemovedTypes());
    updateMimetypes(drawMimeTypes, plugin.getDrawAddedTypes(), plugin.getDrawRemovedTypes());
  }

  public void addSpaceApplication(SpaceApplicationConfigPlugin addAppPlugin) {
    // gather added apps in internal set, add them all together on the start only
    for (SpaceApplication app : addAppPlugin.getSpaceApplicationList()) {
      addedSpaceApps.add(app);
    }
  }

  /**
   * Create a new Moxtra binder for associated space.
   * 
   * @param binderSpace {@link MoxtraBinderSpace}
   * @throws RepositoryException
   * @throws MoxtraException
   * @throws OAuthProblemException
   * @throws OAuthSystemException
   * @throws MoxtraAuthenticationException
   * @throws MoxtraClientException
   */
  public void createSpaceBinder(MoxtraBinderSpace binderSpace) throws RepositoryException,
                                                              MoxtraClientException,
                                                              MoxtraAuthenticationException,
                                                              MoxtraException {
    MoxtraBinder newBinder = binderSpace.getBinder();
    try {
      // create in Moxtra
      MoxtraClient client = moxtra.getClient();
      client.createBinder(getUser(), newBinder);
      client.refreshBinder(newBinder);
      binderSpace.resetNew();

      // TODO handle conflict of binder name (already exists) here: client must send clear ex in such case

      // add JCR items
      saveBinder(binderSpace.getSpace(), newBinder);
    } catch (OAuthSystemException e) {
      throw new MoxtraSocialException("Error creating binder " + newBinder.getName() + ". " + e.getMessage(),
                                      e);
    } catch (OAuthProblemException e) {
      throw new MoxtraSocialException("Error creating binder " + newBinder.getName() + ". " + e.getMessage(),
                                      e);
    } catch (Exception e) {
      throw new MoxtraSocialException("Error creating binder " + newBinder.getName() + ". " + e.getMessage(),
                                      e);
    }
  }

  /**
   * Assign space to a Moxtra binder.
   * 
   * @param binderSpace {@link MoxtraBinderSpace}
   * @throws RepositoryException
   * @throws MoxtraSocialException
   */
  public void assignSpaceBinder(MoxtraBinderSpace binderSpace) throws RepositoryException,
                                                              MoxtraSocialException {
    MoxtraBinder binder = binderSpace.getBinder();
    try {
      // TODO do we have smth to update in Moxtra
      // MoxtraClient client = moxtra.getClient();
      // client.getBinder(binder.getBinderId());
      binderSpace.resetNew();

      // add JCR items
      saveBinder(binderSpace.getSpace(), binder);
    } catch (OAuthSystemException e) {
      throw new MoxtraSocialException("Error creating binder " + binder.getName() + ". " + e.getMessage(), e);
    } catch (OAuthProblemException e) {
      throw new MoxtraSocialException("Error creating binder " + binder.getName() + ". " + e.getMessage(), e);
    } catch (Exception e) {
      throw new MoxtraSocialException("Error creating binder " + binder.getName() + ". " + e.getMessage(), e);
    }
  }

  /**
   * Disable Moxtra binder in the space.
   * 
   * @param binderSpace {@link MoxtraBinderSpace}
   * @throws RepositoryException
   * @throws MoxtraSocialException
   */
  public void disableSpaceBinder(MoxtraBinderSpace binderSpace) throws RepositoryException,
                                                               MoxtraSocialException {
    MoxtraBinder binder = binderSpace.getBinder();
    try {
      binderSpace.resetNew();
      disableBinder(binderSpace.getSpace(), binder);
    } catch (OAuthSystemException e) {
      throw new MoxtraSocialException("Error disabling binder " + binder.getName() + ". " + e.getMessage(), e);
    } catch (OAuthProblemException e) {
      throw new MoxtraSocialException("Error disabling binder " + binder.getName() + ". " + e.getMessage(), e);
    } catch (Exception e) {
      throw new MoxtraSocialException("Error disabling binder " + binder.getName() + ". " + e.getMessage(), e);
    }
  }

  /**
   * Create page conversation.
   * 
   * @param document
   * @param binder
   * @throws RepositoryException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  @Deprecated
  public void createPageConversation_NOTUSED(Node document) throws RepositoryException,
                                                           MoxtraClientException,
                                                           MoxtraException {
    // TODO don't use binder parameter, but find it from the given node (it is in a space sub-tree)
    MoxtraBinderSpace binderSpace = getBinderSpace();
    if (binderSpace != null) {
      MoxtraBinder binder = binderSpace.getBinder();

      // TODO create/upload page asynchronously

      // TODO other nodetypes?
      if (document.isNodeType("moxtra:pageDocument")) {
        throw new MoxtraPageAlreadyException("Document already a conversation page " + document.getName());
      } else if (document.isNodeType("nt:file")) {
        // detect the doc type and choose Note or Draw (Whiteboard)
        Node data = document.getNode("jcr:content");
        if (data.isNodeType("nt:resource")) {
          MoxtraClient client = moxtra.getClient();
          String mimeType = data.getProperty("jcr:mimeType").getString();
          String docName = documentName(document);
          try {
            if (isNoteMimeType(mimeType) || isDrawMimeType(mimeType)) {
              // it is a Note or Draw should be
              client.pageUpload(binder, mimeType, data.getProperty("jcr:data").getStream(), docName);
            } else {
              // not supported document
              throw new MoxtraSocialException("Document type not supported for " + document.getName() + ": "
                  + mimeType);
            }
          } catch (OAuthProblemException e) {
            throw new MoxtraSocialException("Error creating page conversation " + docName + ". "
                + e.getMessage(), e);
          } catch (OAuthSystemException e) {
            throw new MoxtraSocialException("Error creating page conversation " + docName + ". "
                + e.getMessage(), e);
          }

          // TODO add mixin and Moxtra specific info
          document.addMixin("moxtra:pageDocument");
          document.save();

          Node spaceNode;
          try {
            spaceNode = spaceNode(binderSpace.getSpace());
          } catch (Exception e) {
            throw new MoxtraSocialException("Error reading space node "
                + binderSpace.getSpace().getDisplayName());
          }

          Node binderNode = JCR.getBinder(spaceNode);
          Node pageNode = JCR.addPage(binderNode, document.getName(), docName);
          spaceNode.save();

          JCR.addPageRef(document, pageNode);
          document.save();

          String docId = document.getUUID();
          // TODO save in binder space obj
        } else {
          throw new MoxtraSocialException("Cannot determine MIME type of file " + document.getName()
              + ", content node type " + data.getPrimaryNodeType().getName());
        }
      } else {
        throw new MoxtraSocialException("Document not a file " + document.getName() + " "
            + document.getPrimaryNodeType().getName());
      }
    } else {
      throw new MoxtraSocialException("Moxtra Binder space not enabled for " + document.getName() + " "
          + document.getPrimaryNodeType().getName());
    }
  }

  /**
   * Name that will be used for a page conversation on given document.
   * 
   * @param document
   * @return
   * @throws RepositoryException
   */
  public String getPageName(Node document) throws RepositoryException {
    return documentName(document);
  }

  /**
   * Save page conversation.
   * 
   * @param document
   * @param binder
   * @param pageId
   * @throws RepositoryException
   */
  public void savePageConversation(Node document, MoxtraBinder binder, String pageId) throws RepositoryException {
    Node parent = document.getParent();
    Node convoDocument = parent.addNode(convoName(document.getName()), "nt:file");
    try {
      Property exoTitle = document.getProperty("exo:title");
      convoDocument.setProperty(exoTitle.getName(), convoName(exoTitle.getString()));
    } catch (PathNotFoundException e) {
      // nothing to set
    }
    try {
      Property exoName = document.getProperty("exo:name");
      convoDocument.setProperty(exoName.getName(), convoName(exoName.getString()));
    } catch (PathNotFoundException e) {
      // nothing to set
    }
    // TODO set
    parent.save();
    // TODO add mixin and Moxtra specific info
    parent.addMixin("moxtra:page");
    parent.save();
  }

  public MoxtraBinder getBinder(String binderId) throws MoxtraException {
    try {
      return moxtra.getClient().getBinder(binderId);
    } catch (OAuthSystemException e) {
      throw new MoxtraSocialException("Error accessing binder " + binderId + ". " + e.getMessage(), e);
    } catch (OAuthProblemException e) {
      throw new MoxtraSocialException("Error accessing binder " + binderId + ". " + e.getMessage(), e);
    }
  }

  public List<MoxtraBinder> getBinders() throws MoxtraClientException, MoxtraException {
    try {
      return moxtra.getClient().getBinders(getUser());
    } catch (OAuthSystemException e) {
      throw new MoxtraSocialException("Error accessing user binders " + e.getMessage(), e);
    } catch (OAuthProblemException e) {
      throw new MoxtraSocialException("Error accessing user binders " + e.getMessage(), e);
    }
  }

  public boolean hasContextSpace() {
    return contextSpace() != null;
  }

  public MoxtraBinderSpace newBinderSpace() throws MoxtraSocialException {
    Space space = contextSpace();
    if (space != null) {
      try {
        MoxtraBinderSpace newBinder = new MoxtraBinderSpace(space, new MoxtraBinder(), true);
        return newBinder;
      } catch (Exception e) {
        throw new MoxtraSocialException("Error creating new binder space " + space.getDisplayName() + ". "
            + e.getMessage(), e);
      }
    } else {
      throw new MoxtraSocialException("No space found in the context");
    }
  }

  public MoxtraBinderSpace newBinderSpace(MoxtraBinder existingBinder) throws MoxtraSocialException {
    Space space = contextSpace();
    if (space != null) {
      try {
        MoxtraBinderSpace newBinder = new MoxtraBinderSpace(space, existingBinder, true);
        return newBinder;
      } catch (Exception e) {
        throw new MoxtraSocialException("Error creating binder space " + space.getDisplayName() + ". "
            + e.getMessage(), e);
      }
    } else {
      throw new MoxtraSocialException("No space found in the context of existing binder");
    }
  }

  /**
   * Return Moxtra binder if it is enabled for the current space (in the request) or <code>null</code> if not
   * enabled.
   * 
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  public MoxtraBinderSpace getBinderSpace() throws MoxtraSocialException {
    Space space = contextSpace();
    if (space != null) {
      return binderSpace(space);
    } else {
      throw new MoxtraSocialException("No space found in the context");
    }
  }

  /**
   * Return Moxtra binder if it is enabled for the given space (by pretty name) or <code>null</code> if not
   * enabled.
   * 
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  public MoxtraBinderSpace getBinderSpace(String spaceName) throws MoxtraSocialException {
    Space space = spaceService().getSpaceByPrettyName(spaceName);
    if (space != null) {
      return binderSpace(space);
    } else {
      throw new MoxtraSocialException("Space not found " + spaceName);
    }
  }

  // ********* internals *********

  /**
   * Current space in the request (wil work only for portal requests to space portlets).
   * 
   * @return
   */
  protected Space contextSpace() {
    return Utils.getSpaceByContext();
  }

  /**
   * Return Moxtra binder if it is enabled for given space or <code>null</code> if not
   * enabled.
   * 
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  protected MoxtraBinderSpace binderSpace(Space space) throws MoxtraSocialException {
    try {
      Node spaceNode = spaceNode(space);
      if (JCR.isServices(spaceNode)) {
        try {
          Node binderNode = JCR.getBinder(spaceNode);
          return new MoxtraBinderSpace(space, readBinder(binderNode), false);
        } catch (PathNotFoundException e) {
          try {
            JCR.removeServices(spaceNode);
            spaceNode.save();
          } catch (RepositoryException re) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Error cleaning services in " + spaceNode, re);
            }
          }
          // TODO do we need a strict exception here?
          // throw new MoxtraSocialException("Cannot find Moxtra binder enabled for " +
          // space.getDisplayName(), e);
          return null;
        }
      } else {
        return null;
      }
    } catch (Exception e) {
      throw new MoxtraSocialException("Error reading binder space " + space.getDisplayName() + ". "
          + e.getMessage(), e);
    }
  }

  protected String convoName(String name) {
    String convoSuffix = " Conversation";
    StringBuilder cname = new StringBuilder();
    int nameEnd = name.lastIndexOf(".");
    if (nameEnd > 0) {
      cname.append(name.substring(0, nameEnd));
      cname.append(convoSuffix);
      cname.append(name.substring(nameEnd));
    } else {
      cname.append(name);
      cname.append(convoSuffix);
    }
    return cname.toString();
  }

  protected void updateMimetypes(Set<String> mimeTypes, Set<String> added, Set<String> removed) {
    for (String addedMimeType : added) {
      if (addedMimeType.endsWith("/*")) {
        addedMimeType = addedMimeType.substring(0, addedMimeType.length() - 1);
      }
      mimeTypes.add(addedMimeType);
    }
    for (String removeMimeType : removed) {
      if (removeMimeType.endsWith("/*")) {
        removeMimeType = removeMimeType.substring(0, removeMimeType.length() - 1);
      }
      for (Iterator<String> nmtiter = mimeTypes.iterator(); nmtiter.hasNext();) {
        String noteMimeType = nmtiter.next();
        if (noteMimeType.startsWith(removeMimeType)) {
          nmtiter.remove();
        }
      }
    }
  }

  protected boolean isNoteMimeType(String mimeType) {
    boolean res = noteMimeTypes.contains(mimeType);
    if (!res) {
      for (String noteMimeType : noteMimeTypes) {
        if (mimeType.startsWith(noteMimeType)) {
          res = true;
          break;
        }
      }
    }
    return res;
  }

  protected boolean isDrawMimeType(String mimeType) {
    boolean res = drawMimeTypes.contains(mimeType);
    if (!res) {
      for (String drawMimeType : drawMimeTypes) {
        if (mimeType.startsWith(drawMimeType)) {
          res = true;
          break;
        }
      }
    }
    return res;
  }

  protected SpaceService spaceService() {
    return (SpaceService) ExoContainerContext.getCurrentContainer()
                                             .getComponentInstanceOfType(SpaceService.class);
  }

  protected Node spaceNode(Space space) throws Exception {
    String groupsPath = hierarchyCreator.getJcrPath(BasePath.CMS_GROUPS_PATH);
    String spaceFolder = groupsPath + "/spaces/" + space.getPrettyName();
    Session sysSession = hierarchyCreator.getPublicApplicationNode(sessionsProvider.getSystemSessionProvider(null))
                                         .getSession();
    Node spaceNode = (Node) sysSession.getItem(spaceFolder);
    return spaceNode;
  }

  protected void saveBinder(Space space, MoxtraBinder binder) throws Exception {
    Node spaceNode = spaceNode(space);
    Node binderNode;
    try {
      binderNode = JCR.getBinder(spaceNode);
    } catch (PathNotFoundException e) {
      if (!JCR.isServices(spaceNode)) {
        JCR.addServices(spaceNode);
        // save local node before adding binder (mixin should be saved)
        spaceNode.save();
      }
      binderNode = JCR.addBinder(spaceNode);
    }

    writeBinder(binderNode, binder);

    // save everything
    spaceNode.save();
  }

  protected void disableBinder(Space space, MoxtraBinder binder) throws Exception {
    Node spaceNode = spaceNode(space);
    try {
      // binder node will be removed by removal of services
      JCR.removeServices(spaceNode);
      // save the removal
      spaceNode.save();
    } catch (PathNotFoundException e) {
      // already disabled
    }
  }

  /**
   * Write binder data to the node. Node will not be saved.
   * 
   * @param binderNode {@link Node}
   * @param binder {@link MoxtraBinder} binder object to save, should be an editor instance
   * @throws RepositoryException
   */
  protected void writeBinder(Node binderNode, MoxtraBinder binder) throws RepositoryException {

    // object fields
    JCR.setId(binderNode, binder.getBinderId());
    JCR.setName(binderNode, binder.getName());
    // binder fields
    JCR.setRevision(binderNode, binder.getRevision());
    JCR.setCreatedTime(binderNode, binder.getCreatedTime());
    JCR.setUpdatedTime(binderNode, binder.getUpdatedTime());
    // JCR.setThumbnailUrl(meetNode, meet.getThumbnailUrl());

    if (binder.isNew() || !JCR.hasUsers(binderNode)) {
      // create local users
      Node usersNode = JCR.addUsers(binderNode);
      for (MoxtraUser users : binder.getUsers()) {
        Node unode = usersNode.addNode(users.getEmail());
        JCR.setId(unode, users.getId());
        JCR.setName(unode, users.getName());
        JCR.setEmail(unode, users.getEmail());
        JCR.setType(unode, users.getType());
      }
    } else if (binder.hasUsersAdded() || binder.hasUsersRemoved()) {
      // update local users
      Node usersNode = JCR.getUsers(binderNode);
      if (binder.hasUsersRemoved()) {
        for (MoxtraUser removed : binder.getRemovedUsers()) {
          try {
            usersNode.getNode(removed.getEmail()).remove();
          } catch (PathNotFoundException e) {
            // already not found
          }
        }
      }
      if (binder.hasUsersAdded()) {
        for (MoxtraUser user : binder.getAddedUsers()) {
          Node unode = usersNode.addNode(user.getEmail());
          JCR.setId(unode, user.getId());
          JCR.setName(unode, user.getName());
          JCR.setEmail(unode, user.getEmail());
          JCR.setType(unode, user.getType());
        }
      }
    } else {
      // merge local users with users from the given binder
      List<MoxtraUser> users = binder.getUsers();
      if (users.size() > 0) {
        Node usersNode = JCR.getUsers(binderNode);
        Set<String> existingEmails = new HashSet<String>();
        // add/update current binder users
        for (MoxtraUser user : users) {
          String email = user.getEmail();
          existingEmails.add(email);
          Node unode;
          try {
            unode = usersNode.getNode(email);
          } catch (PathNotFoundException e) {
            unode = usersNode.addNode(email);
          }
          JCR.setId(unode, user.getId());
          JCR.setName(unode, user.getName());
          JCR.setEmail(unode, email);
          JCR.setType(unode, user.getType());
        }
        // remove not in the meet users list
        for (NodeIterator uiter = usersNode.getNodes(); uiter.hasNext();) {
          Node unode = uiter.nextNode();
          if (!existingEmails.contains(unode.getName())) {
            unode.remove();
          }
        }
      }
    }
  }

  protected MoxtraBinder readBinder(Node binderNode) throws RepositoryException,
                                                    MoxtraClientException,
                                                    OAuthSystemException,
                                                    OAuthProblemException,
                                                    MoxtraException {
    // object fields
    String binderId = JCR.getId(binderNode).getString();
    String name = JCR.getName(binderNode).getString();
    // binder fields
    long revision = JCR.getRevision(binderNode).getLong();
    Date createdTime = JCR.getCreatedTime(binderNode).getDate().getTime();
    Date updatedTime = JCR.getUpdatedTime(binderNode).getDate().getTime();
    // JCR.setThumbnailUrl(meetNode, meet.getThumbnailUrl());

    // binder users
    List<MoxtraUser> users = new ArrayList<MoxtraUser>();
    Node usersNode = JCR.getUsers(binderNode);
    for (NodeIterator piter = usersNode.getNodes(); piter.hasNext();) {
      Node pnode = piter.nextNode();
      users.add(new MoxtraUser(JCR.getId(pnode).getString(),
                               JCR.getName(pnode).getString(),
                               JCR.getEmail(pnode).getString(),
                               JCR.getType(pnode).getString()));
    }

    MoxtraBinder localBinder = MoxtraBinder.create(binderId, name, revision, createdTime, updatedTime, users);
    return localBinder;
  }

  /**
   * Find pretty name of the document.
   * 
   * @param document
   * @return
   * @throws RepositoryException
   */
  protected String documentName(Node document) throws RepositoryException {
    try {
      return document.getProperty("exo:title").getString();
    } catch (PathNotFoundException te) {
      try {
        return document.getProperty("exo:name").getString();
      } catch (PathNotFoundException ne) {
        return document.getName();
      }
    }
  }
}