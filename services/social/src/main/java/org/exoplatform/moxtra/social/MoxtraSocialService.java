package org.exoplatform.moxtra.social;

import static org.exoplatform.moxtra.Moxtra.fullName;

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.calendar.service.CalendarEvent;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.UserNotFoundException;
import org.exoplatform.moxtra.calendar.MoxtraCalendarService;
import org.exoplatform.moxtra.calendar.MoxtraCalendarStateListener;
import org.exoplatform.moxtra.client.MoxtraAuthenticationException;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraClient.Content;
import org.exoplatform.moxtra.client.MoxtraClientException;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.client.MoxtraFeed;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraOwnerUndefinedException;
import org.exoplatform.moxtra.client.MoxtraPage;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.commons.BaseMoxtraService;
import org.exoplatform.moxtra.jcr.JCR;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.scheduler.impl.JobSchedulerServiceImpl;
import org.exoplatform.social.core.space.SpaceApplicationConfigPlugin;
import org.exoplatform.social.core.space.SpaceApplicationConfigPlugin.SpaceApplication;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.webui.Utils;
import org.picocontainer.Startable;
import org.quartz.JobDataMap;
import org.quartz.Trigger;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import java.io.InputStream;
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
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;

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

  public static final String INVITATION_DETAIL      = "/invitation/detail/";

  public class CalendarListener implements MoxtraCalendarStateListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMeetRead(MoxtraMeet meet, String calendarId, CalendarEvent event) {
      // ensure current user is a member if the space binder, we don't do anything about the meet itself
      String groupId = moxtraCalendar.getGroupIdFromCalendarId(calendarId);
      if (groupId != null) {
        Space calendarSpace = spaceService().getSpaceByGroupId(groupId);
        if (calendarSpace != null) {
          try {
            MoxtraBinderSpace binderSpace = binderSpace(calendarSpace);
            if (binderSpace != null) {
              try {
                binderSpace.ensureBinderMember();
              } catch (MoxtraClientException e) {
                LOG.error("Error auto-join space user to associated Moxtra binder '"
                    + binderSpace.getBinder().getName() + "': " + e.getMessage(), e);
              } catch (MoxtraException e) {
                LOG.error("Moxtra error while auto-joining space user to associated binder '"
                    + binderSpace.getBinder().getName() + "': " + e.getMessage(), e);
              } catch (RepositoryException e) {
                LOG.error("Storage error while auto-joining space user to associated Moxtra binder in "
                    + calendarSpace.getPrettyName() + ": " + e.getMessage(), e);
              }
            } // else, binder not enabled for this space
          } catch (MoxtraSocialException e) {
            LOG.error("Error auto-join space users to associated Moxtra binder "
                + calendarSpace.getPrettyName());
          }
        } else {
          LOG.warn("Space not found for group " + groupId);
        }
      } // else, it's not space calendar
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMeetWrite(MoxtraMeet meet, String calendarId, CalendarEvent event) {
      // nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMeetDelete(MoxtraMeet meet, String calendarId, CalendarEvent event) {
      // nothing
    }
  }

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
      if (isNew) {
        if (this.binder.isNew()) {
          // set binder name to the space name only for newly creating binder
          this.binder.editName(space.getDisplayName());
        }
        // add also space users
        joinMembers();
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

    public boolean isCurrentUserManager() {
      return isSpaceManager(space);
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
        // read creating time once for atomic-style consistency (if other thread will update/remove it)
        Date creatingTime;
        try {
          creatingTime = JCR.getCreatingTime(document).getDate().getTime();
        } catch (PathNotFoundException e) {
          creatingTime = null;
        }
        if (creatingTime != null) {
          // throw new MoxtraSocialException("Conversation page not yet created for " + document.getName());
          return new MoxtraPage(documentName(document), creatingTime);
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
     * Ensure current eXo user is a member of this binder and its space. If user isn't a member of the space,
     * then it will be added via pending request (if space is open then it will be added immediately,
     * otherwise it will require a validation by the space manager). If user has an access to the space, then
     * check if a member of the binder in Moxtra and if not, invite the user to it.
     * 
     * @return <code>true</code> if current user is a member of the binder and its space, <code>false</code>
     *         otherwise (user was be added to pending list and space membership requires validation)
     * @throws MoxtraClientException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    @Deprecated
    public boolean ensureMember() throws MoxtraClientException, MoxtraException, RepositoryException {
      // TODO such check not crear where can be used: NOT USED currently!
      String userName = Moxtra.currentUserName();

      // check space membership
      Space space = getSpace();
      Set<String> spaceUsers = new HashSet<String>();
      for (String mid : space.getManagers()) {
        spaceUsers.add(mid);
      }
      for (String mid : space.getMembers()) {
        spaceUsers.add(mid);
      }
      boolean joinSpace = true;
      for (String uid : spaceUsers) {
        if (userName.equals(uid)) {
          joinSpace = false;
          break;
        }
      }
      SpaceService spaceService = spaceService();
      if (joinSpace) {
        if (!spaceService.isPendingUser(space, userName)) {
          // here we request user access to the space:
          // if space private it will require an acceptance by the managers
          // if open space an user will become a member immediately
          spaceService.addPendingUser(space, userName);
        }
        if (spaceService.hasAccessPermission(space, userName)) {
          ensureBinderMember();
        }
      }
      return false;
    }

    /**
     * Ensure current eXo user is a member of this space. If user isn't a member of the space,
     * then it will be added via pending request (if space is open then it will be added immediately,
     * otherwise it will require a validation by the space manager).
     * 
     * @return <code>true</code> if current user is a member of the space, <code>false</code> otherwise (user
     *         was be added to pending list and space membership requires validation)
     * @throws MoxtraClientException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    public boolean ensureSpaceMember() throws MoxtraClientException, MoxtraException, RepositoryException {
      // FYI this method can work for non-portal access to the binder space (e.g. REST services)
      String userName = Moxtra.currentUserName();

      // check space membership
      Space space = getSpace();
      Set<String> spaceUsers = new HashSet<String>();
      for (String mid : space.getManagers()) {
        spaceUsers.add(mid);
      }
      for (String mid : space.getMembers()) {
        spaceUsers.add(mid);
      }
      boolean joinSpace = true;
      for (String uid : spaceUsers) {
        if (userName.equals(uid)) {
          joinSpace = false;
          break;
        }
      }
      if (joinSpace) {
        SpaceService spaceService = spaceService();
        if (!spaceService.isPendingUser(space, userName)) {
          // here we request user access to the space:
          // if space private it will require an acceptance by the managers
          // if open space an user will become a member immediately
          spaceService.addPendingUser(space, userName);
          return spaceService.hasAccessPermission(space, userName);
        }
      } else {
        return true;
      }
      return false;
    }

    /**
     * Ensure current eXo user is a member of this binder.
     * 
     * @return <code>true</code> if current user is a member of the binder, <code>false</code> otherwise (user
     *         needto be invited by the manager)
     * @throws MoxtraClientException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    public boolean ensureBinderMember() throws MoxtraClientException, MoxtraException, RepositoryException {
      String userName = Moxtra.currentUserName();

      String userEmail;
      String fullName;
      String firstName;
      String lastName;
      try {
        User user = orgService.getUserHandler().findUserByName(userName);
        if (user != null) {
          userEmail = user.getEmail();
          fullName = Moxtra.fullName(user);
          firstName = user.getFirstName();
          lastName = user.getLastName();
        } else {
          userEmail = fullName = firstName = lastName = null;
        }
      } catch (Exception e) {
        LOG.error("Error reading organization user " + userName, e);
        userEmail = fullName = firstName = lastName = null;
      }
      if (userEmail != null) {
        MoxtraUser moxtraUser = null;
        for (MoxtraUser user : binder.getUsers()) {
          if (userEmail.equals(user.getEmail())) {
            moxtraUser = user;
          }
        }
        if (moxtraUser == null) {
          // add user to the binder editor
          moxtraUser = new MoxtraUser(userName,
                                      moxtra.getClient().getOrgId(),
                                      fullName,
                                      userEmail,
                                      firstName,
                                      lastName);
          MoxtraBinder editor = binder.editor();
          editor.addUser(moxtraUser);
          try {
            saveBinder(editor);
            LOG.info("User " + userName + " auto-joined binder '" + binder.getName() + "' in "
                + space.getGroupId());
          } catch (OAuthProblemException e) {
            throw new MoxtraSocialException("Error inviting space user to page conversation " + userName
                + " (" + userEmail + "). " + e.getMessage(), e);
          } catch (OAuthSystemException e) {
            throw new MoxtraSocialException("Error inviting space user to page conversation " + userName
                + " (" + userEmail + "). " + e.getMessage(), e);
          }
          return true;
        }
      }
      return false;
    }

    public void touch() throws MoxtraClientException, MoxtraException, RepositoryException {
      ensureBinderMember();

      // Moxtra.currentUserName();
      runSyncJob(this);
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
     * currently creating.<br>
     * This method also will check if current eXo user is a member of the binder and invite it if it is not.
     * 
     * @param document
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public boolean hasPage(Node document) throws RepositoryException, MoxtraClientException, MoxtraException {
      touch();
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
          throw new MoxtraSocialException("Document has not content resource " + document.getName()
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

    /**
     * Find page conversation by its ID in context binder space.
     * 
     * @param pageId {@link String}
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public MoxtraPage findPageById(String pageId) throws RepositoryException,
                                                 MoxtraClientException,
                                                 MoxtraException {
      if (!isNew()) {
        try {
          MoxtraBinder binder = moxtra.getClient().getBinder(getBinder().getBinderId());
          for (MoxtraPage page : binder.getPages()) {
            if (pageId.equals(page.getId())) {
              // TODO do we need check page type or other things?
              // we could ensure it was just created by created_time
              return page;
            }
          }
        } catch (OAuthSystemException e) {
          throw new MoxtraSocialException("Error searching page conversation " + pageId, e);
        } catch (OAuthProblemException e) {
          throw new MoxtraSocialException("Error searching page conversation " + pageId, e);
        }
      }
      return null;
    }

    /**
     * Download conversation pages content to eXo. Each content node will be placed near the original
     * document.
     * 
     * @param document
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public void syncPages() throws RepositoryException, MoxtraClientException, MoxtraException {
      MoxtraBinder binder = getBinder();

      try {
        MoxtraUser owner = binder.getOwnerUser();
        String userName = owner.getUniqueId();
        if (userName == null) {
          // TODO we can run only if it a manager created this binder
          // find in org-service by email of owner and run if current user has the same
          LOG.warn("Binder onwer name empty, cannot run sync");
          return;
        }
        MoxtraClient client = moxtra.getClient(userName);

        // TODO binderNode already exists here, thus we could access it by UUID
        Node spaceNode = spaceNode();
        Node binderNode = JCR.getBinder(spaceNode);
        Node pagesNode;
        try {
          pagesNode = JCR.getPages(binderNode);
        } catch (PathNotFoundException e) {
          pagesNode = JCR.addPages(binderNode);
          binderNode.save();
        }

        if (!JCR.isRefreshing(binderNode)) {
          JCR.markRefreshing(binderNode);
          binderNode.save();

          // first validate all currently uploading pages
          for (NodeIterator pditer = JCR.findPageDocuments(spaceNode); pditer.hasNext();) {
            Node document = pditer.nextNode();
            // this will force page node creation if it was created in Moxtra
            isPageNode(document, true);
          }

          try {
            long timestamp;
            try {
              timestamp = JCR.getRefreshedTime(binderNode).getDate().getTimeInMillis();
            } catch (PathNotFoundException e) {
              timestamp = binder.getCreatedTime().getTime();
            }

            // get binder feeds
            List<MoxtraFeed> feeds = client.getBinderConversations(binder.getBinderId(), 0); // TODO timestamp
            List<MoxtraFeed> applyList = new ArrayList<MoxtraFeed>();

            // TODO find new remote feeds for pages (not yet applied)
            next: for (MoxtraFeed feed : feeds) {
              long feedTimestamp = feed.getPublishedTime().getTime();
              // skip already applied feeds
              if (feedTimestamp > timestamp) {
                if (feed.getTargetType().equals(MoxtraFeed.OBJECT_TYPE_FILE)
                    || feed.getTargetType().equals(MoxtraFeed.OBJECT_TYPE_PAGE)) {
                  // TODO for each page find is it local page or new (remote only)
                  if (feed.getVerb().equals(MoxtraFeed.VERB_TAG)) {
                    // annotated
                    long feedTime = feed.getPublishedTime().getTime();
                    // remove feeds for the same page/change and add the new to the end
                    for (Iterator<MoxtraFeed> fiter = applyList.iterator(); fiter.hasNext();) {
                      MoxtraFeed otherFeed = fiter.next();
                      if (otherFeed.isSameObject(otherFeed)) {
                        if (feedTime >= otherFeed.getPublishedTime().getTime()) {
                          fiter.remove();
                          break;
                        } else {
                          continue next;
                        }
                      }
                    }
                    applyList.add(feed);
                  } else {
                    if (feed.getVerb().equals(MoxtraFeed.VERB_POST)) {
                      // new comment
                      // XXX what do we do with comments?
                      // pageId = feed.getTargetId();
                      if (LOG.isDebugEnabled()) {
                        LOG.debug("Feed (post) not supported: " + feed);
                      }
                    } else if (feed.getVerb().equals(MoxtraFeed.VERB_CREATE)) {
                      // TODO new file
                      LOG.warn("Feed (create) not supported: " + feed);
                    }
                    continue;
                  }
                } else {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Feed not supported: " + feed);
                  }
                }
              }
            }

            long maxTimestamp = 0;
            for (MoxtraFeed feed : applyList) {
              // for existing find page document
              String pageId = feed.getTargetId();

              // create conversation file near the original document: $DOCNAME_Conversation.docExt
              Node pageNode;
              try {
                pageNode = JCR.getPage(binderNode, pageId);
              } catch (PathNotFoundException e) {
                // TODO (PRIORITY 2) for new create a page document in space's root (or respectively to the
                // folder structure in Moxtra)
                if (LOG.isDebugEnabled()) {
                  LOG.debug(">>> skipping not existing locally page " + pageId + " " + feed.getTargetUrl());
                }
                continue;
              }

              // FYI there is should be only single reference property
              for (PropertyIterator pageRefs = pageNode.getReferences(); pageRefs.hasNext();) {
                Property pageRef = pageRefs.nextProperty();
                Node document = pageRef.getParent();
                if (!JCR.isPageContent(document)) {
                  Node folder = document.getParent();

                  String convoNodeName = convoName(document.getName(), "_conversation");
                  Node convo, content;
                  try {
                    convo = folder.getNode(convoNodeName);
                    content = convo.getNode("jcr:content");
                  } catch (PathNotFoundException e) {
                    convo = folder.addNode(convoNodeName, "nt:file");
                    content = convo.addNode("jcr:content", "nt:resource");
                  }

                  if (!JCR.isPageContent(convo)) {
                    JCR.addPageContent(convo);
                  }

                  // get page content from Moxtra
                  Content pageContent = client.downloadBinderPage(binder.getBinderId(), pageId);
                  InputStream dataStream = pageContent.getContent();
                  try {
                    content.setProperty("jcr:mimeType", "application/pdf"); // pageContent.getContentType()
                    java.util.Calendar created = java.util.Calendar.getInstance();
                    created.setTime(feed.getPublishedTime());
                    content.setProperty("jcr:lastModified", created);
                    content.setProperty("jcr:data", dataStream);
                    folder.save(); // save to let actions add mixins to new folder node
                  } finally {
                    dataStream.close();
                  }

                  // reference created conversation content to the document
                  JCR.setContentRef(document, convo);
                  document.save();// JCR.getContentRef(document)
                  // reference conversation to the page
                  JCR.setPageRef(convo, pageNode);
                  String docName = documentName(document);
                  String convoName = convoName(docName, " Conversation");
                  convo.setProperty("exo:title", convoName);
                  convo.setProperty("exo:name", convoName);
                  convo.save();
                }
              }

              // find max timestamp
              long feedTimestamp = feed.getPublishedTime().getTime();
              if (feedTimestamp > maxTimestamp) {
                maxTimestamp = feedTimestamp;
              }
            }

            if (maxTimestamp > 0) {
              JCR.setRefreshedTime(binderNode, new Date(maxTimestamp));
              // pagesNode.save(); // will save in finally below
            }
          } finally {
            JCR.markNotRefreshing(binderNode);
            binderNode.save();
          }
        } else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Binder " + binder.getBinderId() + "@" + getSpace().getGroupId() + " already synching");
          }
        }
      } catch (OAuthProblemException e) {
        throw new MoxtraSocialException("Error reading binder conversations '" + binder.getName() + "': "
            + e.getMessage(), e);
      } catch (OAuthSystemException e) {
        throw new MoxtraSocialException("Error reading binder conversations '" + binder.getName() + "': "
            + e.getMessage(), e);
      } catch (Exception e) {
        throw new MoxtraSocialException("Error reading binder node '" + binder.getName() + "'. "
            + e.getMessage(), e);
      }
    }

    public List<MoxtraUser> getMembers(boolean includeMe) throws Exception {
      List<MoxtraUser> binderUsers = binder.getUsers();
      if (!includeMe) {
        MoxtraUser me = moxtra.getClient().getCurrentUser();
        List<MoxtraUser> users = new ArrayList<MoxtraUser>();
        for (MoxtraUser user : binderUsers) {
          if (!me.equals(user)) {
            users.add(user);
          }
        }
        return users;
      } else {
        return binderUsers;
      }
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
      if (orgUser != null) {
        String name = fullName(orgUser);
        return new MoxtraUser(userId, moxtra.getClient().getOrgId(), name, orgUser.getEmail());
      } else {
        throw new MoxtraClientException("User not found in organization service '" + userId + "'");
      }
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
          // w/o checking in Moxtra we check for time how long this page is creating and if longer of some
          // allowed period - we assume it is not a page (removed/moved/renamed in Moxtra or like that)
          if (refresh) {
            // Node pageNode = JCR.getPageRef(document).getNode();
            Property pageNameProp = JCR.getName(document);
            String pageName = pageNameProp.getString();
            MoxtraPage page = findPage(pageName);
            if (page != null) {
              // page already created in Moxtra, add local node
              Node pageNode = writePage(page, document.getName());
              // update document node (remove creating time props)
              creatingTimeProp.remove();
              pageNameProp.remove();
              JCR.setPageRef(document, pageNode);
              document.save();
              return true;
            } else if (System.currentTimeMillis() - creatingTime.getTimeInMillis() < MAX_PAGE_CREATING_TIME) {
              return true;
            }
          } else {
            if (System.currentTimeMillis() - creatingTime.getTimeInMillis() < MAX_PAGE_CREATING_TIME) {
              return true;
            }
          }
        } catch (PathNotFoundException e) {
          // when creating time not found, then check if page node exists
          return JCR.hasPageRef(document);
        }
      }
      return false;
    }

    protected Node writePage(MoxtraPage page, String nodeName) throws MoxtraSocialException,
                                                              RepositoryException {
      Node spaceNode = spaceNode();
      Node binderNode = JCR.getBinder(spaceNode);
      Node pageNode = JCR.addPage(binderNode, String.valueOf(page.getId()));

      JCR.setId(pageNode, page.getId());
      JCR.setName(pageNode, page.getOriginalFileName());
      JCR.setRevision(pageNode, page.getRevision());
      JCR.setPageUrl(pageNode, page.getUrl());
      JCR.setType(pageNode, page.getType());

      binderNode.save();

      return pageNode;
    }

    /**
     * Save given binder as current using its owner user's client when available (e.g. in SSO org mode) and
     * refresh the binder space to actual state in Moxtra.
     * 
     * @param binder {@link MoxtraBinder} binder editor, this object will not be refreshed on the end!
     * @throws OAuthSystemException
     * @throws OAuthProblemException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    protected void saveBinder(MoxtraBinder binder) throws OAuthSystemException,
                                                  OAuthProblemException,
                                                  RepositoryException,
                                                  MoxtraException {
      // try find owner client (actual for SSO mode)
      MoxtraClient client;
      try {
        String ownerUniqueId = binder.getOwnerUser().getUniqueId();
        if (ownerUniqueId != null) {
          try {
            client = moxtra.getClient(ownerUniqueId);
          } catch (UserNotFoundException e) {
            throw new MoxtraClientException("Error opening Moxtra client for " + ownerUniqueId + " user: "
                + e.getMessage(), e);
          }
        } else {
          client = moxtra.getClient();
        }
      } catch (MoxtraOwnerUndefinedException e) {
        client = moxtra.getClient();
      }

      client.inviteUsers(binder);

      // refresh the instance binder object!
      client.refreshBinder(this.binder);

      Node spaceNode = spaceNode();
      Node binderNode = JCR.getBinder(spaceNode);
      writeBinder(binderNode, this.binder);
      binderNode.save();
    }

    /**
     * Add space users to binder if some of them not yet members there.
     * 
     * @return boolean <code>true</code> if at least one new member was added to the binder,
     *         <code>false</code> otherwise
     * @throws Exception
     */
    protected boolean joinMembers() throws Exception {
      Set<MoxtraUser> users = new LinkedHashSet<MoxtraUser>();
      for (String userId : space.getManagers()) {
        users.add(findUser(userId));
      }
      for (String userId : space.getMembers()) {
        users.add(findUser(userId));
      }
      boolean added = false;
      for (MoxtraUser user : users) {
        if (binder.addUser(user)) {
          added = true;
        }
      }
      return added;
    }
  }

  public class MeetEvent {
    protected final CalendarEvent event;

    protected final MoxtraMeet    meet;

    protected MeetEvent(CalendarEvent event, MoxtraMeet meet) {
      this.event = event;
      this.meet = meet;
    }

    public String getEventId() {
      return this.event.getId();
    }

    public String getBinderId() {
      return this.meet.getBinderId();
    }

    public Date getStartTime() {
      return this.meet.getStartTime();
    }

    public Date getEndTime() {
      return this.meet.getEndTime();
    }

    /**
     * Event invitation link to open inside port, this method will work only within a portal request, NPE will
     * be otherwise.
     * 
     * @return event invitation link.
     */
    public String getEventActivityLink() {
      return makeEventLink(Util.getPortalRequestContext().getRemoteUser(), event);
    }
  }

  protected final MoxtraCalendarService moxtraCalendar;

  protected final ManageDriveService    driveService;

  protected final Set<String>           noteMimeTypes  = new HashSet<String>();

  protected final Set<String>           drawMimeTypes  = new HashSet<String>();

  protected final Set<SpaceApplication> addedSpaceApps = new HashSet<SpaceApplication>();

  /**
   * @throws MoxtraConfigurationException
   * 
   */
  public MoxtraSocialService(MoxtraService moxtra,
                             MoxtraCalendarService moxtraCalendar,
                             SessionProviderService sessionProviderService,
                             NodeHierarchyCreator hierarchyCreator,
                             OrganizationService orgService,
                             JobSchedulerServiceImpl schedulerService,
                             ManageDriveService driveService) {
    super(moxtra, sessionProviderService, hierarchyCreator, orgService, schedulerService);
    this.moxtraCalendar = moxtraCalendar;
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

    // add lister to calendar service
    moxtraCalendar.addListener(new CalendarListener());
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
      MoxtraClient client = moxtra.getClient();
      client.inviteUsers(binder); // TODO merge member users from space and binder
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
   * Name that will be used for a page conversation on given document.
   * 
   * @param document
   * @return
   * @throws RepositoryException
   */
  public String getPageName(Node document) throws RepositoryException {
    return documentName(document);
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
   * Return Moxtra binder space if it is enabled for the current space (in the request) or <code>null</code>
   * if not enabled.
   * 
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  public MoxtraBinderSpace getBinderSpace() throws MoxtraSocialException {
    Space space = contextSpace();
    if (space != null) {
      MoxtraBinderSpace binderSpace = binderSpace(space);
      return binderSpace;
    } else {
      throw new MoxtraSocialException("No space found in the context");
    }
  }

  /**
   * Return Moxtra binder space if it is enabled for the given space (by pretty name) or <code>null</code> if
   * not
   * enabled.
   * 
   * @param String a space pretty name
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

  /**
   * Return Moxtra binder space if it is enabled for the given Moxtra Binder or <code>null</code> if not
   * enabled.
   * 
   * @param binder {@link MoxtraBinder}
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  public MoxtraBinderSpace getBinderSpace(MoxtraBinder binder) throws MoxtraSocialException {
    return binderSpace(binder);
  }

  /**
   * Return <code>true</code> if Social space available in the context (in portal requests to space portlets).
   * 
   * @return <code>true</code> if Social space available
   */
  public boolean hasContextSpace() {
    return contextSpace() != null;
  }

  /**
   * Social space existing in the context (will work only for portal requests to space portlets).
   * 
   * @return {@link Space}
   * @see {@link Utils#getSpaceByContext()}
   */
  public Space getContextSpace() {
    return contextSpace();
  }

  /**
   * Return <code>true</code> if current user is a manager of context space.
   * 
   * @return <code>true</code> if current user is a manager of context space, <code>false</code> otherwise
   */
  public boolean isContextSpaceManager() {
    return isSpaceManager(contextSpace());
  }

  /**
   * Return <code>true</code> if current user is a member of context space.
   * 
   * @return <code>true</code> if current user is a member of context space, <code>false</code> otherwise
   */
  public boolean isContextSpaceMember() {
    return isSpaceMember(contextSpace());
  }

  public void uploadMeetDocument(Node document, String sessionKey, String sessionId) throws MoxtraClientException,
                                                                                    MoxtraSocialException,
                                                                                    RepositoryException,
                                                                                    MoxtraException {
    if (document.isNodeType("nt:file")) {
      // detect the doc type and choose Note or Draw (Whiteboard)
      Node data = document.getNode("jcr:content");
      if (data.isNodeType("nt:resource")) {
        MoxtraClient client = moxtra.getClient();
        String docName = documentName(document);

        Property content = data.getProperty("jcr:data");
        InputStream contentStream;

        // we need content length for Moxtra
        long contentLength;
        try {
          contentLength = content.getLength();
          contentStream = content.getStream();
        } catch (ValueFormatException e) {
          // multivalued property?
          LOG.warn("Uploading multivalued document to Moxtra meet, using first value with undefined length: "
              + document.getPath());
          contentLength = -1; // XXX this will not work for Moxtra as on Jun 5 2015
          contentStream = content.getValues()[0].getStream();
        }

        if (contentLength < 0) {
          // TODO need spool the stream to temp file and then use its size and content
          LOG.warn("Content length not defined for meet upload of " + document.getPath());
        }

        try {
          client.boardUpload(sessionKey, sessionId, contentStream, contentLength, docName);
        } catch (OAuthProblemException e) {
          throw new MoxtraSocialException("Error creating meet document " + docName + ". " + e.getMessage(),
                                          e);
        } catch (OAuthSystemException e) {
          throw new MoxtraSocialException("Error creating meet document " + docName + ". " + e.getMessage(),
                                          e);
        }
      } else {
        throw new MoxtraSocialException("Meet document has not content resource " + document.getName()
            + ", content node type " + data.getPrimaryNodeType().getName());
      }
    } else {
      throw new MoxtraSocialException("Meet document not a file " + document.getName() + " "
          + document.getPrimaryNodeType().getName());
    }
  }

  public MeetEvent createMeet(MoxtraBinderSpace binderSpace, MoxtraMeet meet) throws Exception {

    String calendarId = moxtraCalendar.getCalendarIdFromGroupId(binderSpace.getSpace().getGroupId());
    CalendarEvent event;
    event = moxtraCalendar.createMeet(calendarId, meet);
    MeetEvent meetEvent = new MeetEvent(event, meet);
    return meetEvent;
  }

  public MeetEvent updateMeet(MoxtraBinderSpace binderSpace, String eventId) throws Exception {

    String calendarId = moxtraCalendar.getCalendarIdFromGroupId(binderSpace.getSpace().getGroupId());
    CalendarEvent event = moxtraCalendar.refreshMeet(calendarId, eventId);
    MoxtraMeet meet = moxtraCalendar.getMeet(event);
    MeetEvent meetEvent = new MeetEvent(event, meet);
    return meetEvent;
  }

  // ********* internals *********

  /**
   * Method copied from PLF integ with social CalendarSpaceActivityPublisher.
   * 
   * @param event
   * @return
   */
  protected String makeEventLink(String userId, CalendarEvent event) {
    StringBuffer sb = new StringBuffer("");
    PortalRequestContext requestContext = Util.getPortalRequestContext();
    sb.append(requestContext.getPortalURI())
      .append(requestContext.getNodePath().replace("/moxtra", "/calendar"))
      // TODO find better way
      .append(INVITATION_DETAIL)
      .append(userId)
      .append("/")
      .append(event.getId())
      .append("/")
      .append(event.getCalType());
    return sb.toString();
  }

  /**
   * Current space in the request (will work only for portal requests to space portlets).
   * 
   * @return
   */
  protected Space contextSpace() {
    try {
      return Utils.getSpaceByContext();
    } catch (NullPointerException e) {
      // XXX NPE has a place when running not in portal request, assume it as normal
    } catch (Throwable e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Error when getting context space: " + e.getMessage(), e);
      }
    }
    return null;
  }

  /**
   * Find space by its name (pretty name or display name).
   * 
   * @return {@link Space} instance of <code>null</code> if such space cannot be found or error happen
   */
  protected Space spaceByName(String name) {
    try {
      SpaceService service = spaceService();
      Space space = service.getSpaceByPrettyName(name);
      if (space == null) {
        space = service.getSpaceByDisplayName(name);
      }
      return space;
    } catch (NullPointerException e) {
      // XXX NPE has a place when running not in portal request, assume it as normal
      if (LOG.isDebugEnabled()) {
        LOG.debug("NPE when getting space by name: " + name + ". " + e.getMessage(), e);
      }
    } catch (Throwable e) {
      LOG.warn("Error when getting space by name: " + name + ". " + e.getMessage(), e);
    }
    return null;
  }

  protected boolean isSpaceManager(Space space) {
    if (space != null) {
      String userName = Moxtra.currentUserName();
      for (String mid : space.getManagers()) {
        if (userName.equals(mid)) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean isSpaceMember(Space space) {
    if (space != null) {
      String userName = Moxtra.currentUserName();
      for (String mid : space.getMembers()) {
        if (userName.equals(mid)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Return Moxtra binder space if it is enabled for given Social space or <code>null</code> if not
   * enabled.
   * 
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled for given space
   * @throws MoxtraSocialException if error happen
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
        }
      }
    } catch (Exception e) {
      throw new MoxtraSocialException("Error reading binder space '" + space.getDisplayName() + "'. "
          + e.getMessage(), e);
    }
    return null;
  }

  /**
   * Return Moxtra binder space associated with Moxtra binder.
   * 
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not associated with any space.
   * @throws MoxtraSocialException if error happen
   */
  protected MoxtraBinderSpace binderSpace(MoxtraBinder binder) throws MoxtraSocialException {
    try {
      Node binderNode = binderNode(binder);
      if (binderNode != null) {
        Node spaceNode = binderNode.getParent();
        if (JCR.isServices(spaceNode)) {
          Space space = spaceByName(spaceNode.getName());
          if (space != null) {
            return new MoxtraBinderSpace(space, readBinder(binderNode), false);
          } else {
            throw new MoxtraSocialException("Cannot find a space " + spaceNode.getName()
                + " associated with binder " + binder.getName());
          }
        } else {
          LOG.warn("Binder space node not of service nodetype " + spaceNode.getPath());
          throw new MoxtraSocialException("Binder space node '" + binder.getName()
              + "' not of service nodetype");
        }
      }
    } catch (Exception e) {
      throw new MoxtraSocialException("Error reading space of binder '" + binder.getName() + "'. "
          + e.getMessage(), e);
    }
    return null;
  }

  protected String convoName(String name, String convoSuffix) {
    StringBuilder cname = new StringBuilder();
    int nameEnd = name.lastIndexOf(".");
    if (nameEnd > 0) {
      cname.append(name.substring(0, nameEnd));
      cname.append(convoSuffix);
      // cname.append(name.substring(nameEnd));
    } else {
      cname.append(name);
      cname.append(convoSuffix);
    }
    cname.append(".pdf");
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
    Session sysSession = hierarchyCreator.getPublicApplicationNode(sessionProviderService.getSystemSessionProvider(null))
                                         .getSession();
    Node spaceNode = (Node) sysSession.getItem(spaceFolder);
    return spaceNode;
  }

  protected Node binderNode(MoxtraBinder binder) throws Exception {
    String groupsPath = hierarchyCreator.getJcrPath(BasePath.CMS_GROUPS_PATH);
    String spacesFolder = groupsPath + "/spaces/";
    Session sysSession = hierarchyCreator.getPublicApplicationNode(sessionProviderService.getSystemSessionProvider(null))
                                         .getSession();
    // first try by binder name cleaned in space's pretty name style
    Node binderNode;
    try {
      String spacePrettyName = SpaceUtils.cleanString(binder.getName());
      binderNode = ((Node) sysSession.getItem(spacesFolder + spacePrettyName + "/moxtra:binder"));
    } catch (PathNotFoundException e) {
      // need search using JCR query and binder NT and id
      NodeIterator binderNodes = JCR.findBinder((Node) sysSession.getItem(spacesFolder), binder);
      long nodesCount = binderNodes.getSize();
      if (nodesCount > 0) {
        binderNode = binderNodes.nextNode();
        if (nodesCount > 1) {
          LOG.warn("Found more than one binder node for '" + binder.getName() + "', using first one "
              + binderNode.getPath());
        }
      } else {
        binderNode = null;
      }
    }

    return binderNode;
  }

  protected void saveBinder(Space space, MoxtraBinder binder) throws Exception {
    Node spaceNode = spaceNode(space);
    Node binderNode;
    try {
      binderNode = JCR.getBinder(spaceNode);
    } catch (PathNotFoundException e) {
      if (!JCR.isServices(spaceNode)) {
        JCR.addServices(spaceNode, Moxtra.currentUserName());
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
      for (MoxtraUser user : binder.getUsers()) {
        Node unode = usersNode.addNode(user.getEmail());
        JCR.setId(unode, user.getId());
        JCR.setUniqueId(unode, user.getUniqueId());
        JCR.setOrgId(unode, user.getOrgId());
        JCR.setName(unode, user.getName());
        JCR.setEmail(unode, user.getEmail());
        JCR.setPictureUri(unode, user.getPictureUri());
        JCR.setType(unode, user.getType());
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
          JCR.setUniqueId(unode, user.getUniqueId());
          JCR.setOrgId(unode, user.getOrgId());
          JCR.setName(unode, user.getName());
          JCR.setEmail(unode, user.getEmail());
          JCR.setPictureUri(unode, user.getPictureUri());
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
          JCR.setUniqueId(unode, user.getUniqueId());
          JCR.setOrgId(unode, user.getOrgId());
          JCR.setName(unode, user.getName());
          JCR.setEmail(unode, email);
          JCR.setPictureUri(unode, user.getPictureUri());
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
    // internal save time
    Date savedTime = new Date();
    JCR.setSavedTime(binderNode, savedTime);
    binder.setSavedTime(savedTime);
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
      users.add(new MoxtraUser(JCR.getIdString(pnode),
                               JCR.getUniqueIdString(pnode),
                               JCR.getOrgIdString(pnode),
                               JCR.getName(pnode).getString(),
                               JCR.getEmail(pnode).getString(),
                               JCR.getPictureUriString(pnode),
                               JCR.getType(pnode).getString()));
    }

    MoxtraBinder localBinder = MoxtraBinder.create(binderId, name, revision, createdTime, updatedTime, users);

    try {
      Date savedTime = JCR.getSavedTime(binderNode).getDate().getTime();
      localBinder.setSavedTime(savedTime);
    } catch (PathNotFoundException e) {
      // ok, it will happen on previously created binders
      localBinder.setSavedTime(updatedTime);
    }

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

  protected String binderJobName(MoxtraBinderSpace binderSpace) {
    return "Binder_" + binderSpace.getBinder().getBinderId() + "@" + binderSpace.getSpace().getGroupId();
  }

  /**
   * Schedule a local job to sync a binder.
   * 
   * @throws Exception
   */
  protected String runSyncJob(MoxtraBinderSpace binderSpace) throws MoxtraSocialException {
    String jobName = binderJobName(binderSpace);

    boolean reschedule;
    // TODO
    // JobInfo jobInfo = new JobInfo(jobName, MOXTRA_JOB_GROUP_NAME, MoxtraBinderSyncJob.class);
    // JobDetail existing = schedulerService.getJob(jobInfo);
    try {
      Trigger[] existing = schedulerService.getTriggersOfJob(jobName, MOXTRA_JOB_GROUP_NAME);
      if (existing != null && existing.length > 0) {
        Date existingEndTime = existing[0].getEndTime();
        if (existingEndTime != null
            && (System.currentTimeMillis() - existingEndTime.getTime()) > MoxtraBinderSyncJob.SYNC_RESCHEDULE_THRESHOLD_MS) {
          reschedule = true;
        } else {
          return jobName; // job still actual
        }
      } else {
        reschedule = false;
      }
    } catch (Exception e) {
      throw new MoxtraSocialException("Error reading binder synchronization job " + jobName, e);
    }

    String userName = Moxtra.currentUserName();

    // job info
    JobDetailImpl job = new JobDetailImpl();

    job.setName(jobName);
    job.setGroup(MOXTRA_JOB_GROUP_NAME);
    job.setJobClass(MoxtraBinderSyncJob.class);
    job.setDescription("Synchronize space documents with Moxtra binder");

    JobDataMap jobData = job.getJobDataMap();

    jobData.put(MoxtraBinderSyncJob.DATA_USER_ID, userName);
    jobData.put(MoxtraBinderSyncJob.DATA_GROUP_ID, binderSpace.getSpace().getGroupId());
    jobData.put(MoxtraBinderSyncJob.DATA_BINDER_ID, binderSpace.getBinder().getBinderId());
    // jobData.put(MoxtraBinderSyncJob.DATA_SYNC_ATTEMPTS, Long.valueOf(0l));

    // schedule the job
    SimpleTriggerImpl trigger = new SimpleTriggerImpl();
    trigger.setName(jobName);
    trigger.setGroup(MOXTRA_JOB_GROUP_NAME);

    java.util.Calendar startTime = Moxtra.getCalendar();
    startTime.add(Calendar.MINUTE, MoxtraBinderSyncJob.SYNC_DELAY_MINUTES);
    java.util.Calendar endTime = Moxtra.getCalendar();
    endTime.add(Calendar.MINUTE, MoxtraBinderSyncJob.SYNC_PERIOD_MINUTES);

    trigger.setStartTime(startTime.getTime());
    trigger.setEndTime(endTime.getTime());
    // trigger.setRepeatCount(0);
    trigger.setRepeatInterval(MoxtraBinderSyncJob.SYNC_INTERVAL_MS);

    try {
      if (reschedule) {
        schedulerService.rescheduleJob(jobName, MOXTRA_JOB_GROUP_NAME, trigger);
      } else {
        jobEnvironment.configure(userName);
        schedulerService.addJob(job, trigger);
      }
    } catch (Exception e) {
      throw new MoxtraSocialException("Error scheduling binder synchronization job " + jobName, e);
    }

    return jobName;
  }
}
