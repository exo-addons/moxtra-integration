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
import org.exoplatform.services.idgenerator.IDGeneratorService;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.ItemExistsException;
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

  /**
   * Maximum time to wait while page will be created in Moxtra. It's 3min.
   */
  public static final long   MAX_PAGE_CREATING_TIME = 1000 * 60 * 3;

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
            MoxtraBinderSpace binderSpace = getBinderSpace(calendarSpace);
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

    protected final Space        space;

    protected final MoxtraBinder binder;

    protected boolean            isNew;

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
                                JCR.getIndex(pageNode).getLong(),
                                JCR.getNumberString(pageNode),
                                JCR.getType(pageNode).getString(),
                                JCR.getName(pageNode).getString(),
                                JCR.getPageUrl(pageNode).getString(),
                                JCR.getThumbnailUrlString(pageNode),
                                JCR.getBackgroundUrlString(pageNode));
        }
      } else if (JCR.isPageContent(document)) {
        Node pageNode = JCR.getPageRef(document).getNode();
        return new MoxtraPage(JCR.getId(pageNode).getLong(),
                              JCR.getRevision(pageNode).getLong(),
                              JCR.getIndex(pageNode).getLong(),
                              JCR.getNumberString(pageNode),
                              JCR.getType(pageNode).getString(),
                              JCR.getName(pageNode).getString(),
                              JCR.getPageUrl(pageNode).getString(),
                              JCR.getThumbnailUrlString(pageNode),
                              JCR.getBackgroundUrlString(pageNode));
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
     *         need to be invited by the manager)
     * @throws MoxtraClientException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    public boolean ensureBinderMember() throws MoxtraClientException, MoxtraException, RepositoryException {
      String userName = Moxtra.currentUserName();
      return ensureBinderMember(userName);
    }

    /**
     * Ensure given eXo user is a member of this binder. This method doesn't check if the user is a space
     * member.
     * 
     * @param userName {@link String} eXo user name
     * @return <code>true</code> if user is a member of the binder, <code>false</code> otherwise (user
     *         need to be invited by the manager)
     * @throws MoxtraClientException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    public boolean ensureBinderMember(String userName) throws MoxtraClientException,
                                                      MoxtraException,
                                                      RepositoryException {
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
        SpaceService spaces = spaceService();
        // invited and pending users will join when itself do in own sessions (it will be "joined" event in
        // the space)
        if (moxtraUser == null && !spaces.isPendingUser(space, userName)
            && !spaces.isInvitedUser(space, userName)) {
          // add user to the binder editor
          moxtraUser = new MoxtraUser(userName,
                                      moxtra.getClient().getOrgId(),
                                      fullName,
                                      userEmail,
                                      firstName,
                                      lastName);
          MoxtraBinder editor = binder.editor();
          editor.addUser(moxtraUser);
          saveBinder(editor);
          LOG.info("User " + userName + " auto-joined binder '" + binder.getName() + "' in "
              + space.getGroupId());
          return true;
        }
      }
      return false;
    }

    /**
     * Remove given eXo user from this binder users.
     * 
     * @param userName {@link String} eXo user name
     * @return <code>true</code> if member removed from the binder, <code>false</code> otherwise (user not a
     *         member)
     * @throws MoxtraClientException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    public boolean removeBinderMember(String userName) throws MoxtraClientException,
                                                      MoxtraException,
                                                      RepositoryException {
      String userEmail;
      try {
        User user = orgService.getUserHandler().findUserByName(userName);
        if (user != null) {
          userEmail = user.getEmail();
        } else {
          userEmail = null;
        }
      } catch (Exception e) {
        LOG.error("Error reading organization user " + userName, e);
        userEmail = null;
      }
      if (userEmail != null) {
        MoxtraUser moxtraUser = null;
        for (MoxtraUser user : binder.getUsers()) {
          if (userEmail.equals(user.getEmail())) {
            moxtraUser = user;
          }
        }
        if (moxtraUser != null) {
          // remove user from the binder editor

          MoxtraBinder editor = binder.editor();
          editor.removeUser(moxtraUser);
          saveBinder(editor);
          LOG.info("User " + userName + " left binder '" + binder.getName() + "' in " + space.getGroupId());
          return true;
        } // else, not binder user already
      }
      return false;
    }

    /**
     * Rename this binder.
     * 
     * @param newName {@link String} new name of the binder
     * @throws MoxtraClientException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    public void renameBinder(String newName) throws MoxtraClientException,
                                            MoxtraException,
                                            RepositoryException {
      if (newName != null) {
        // remove user from the binder editor

        MoxtraBinder editor = binder.editor();
        String oldName = editor.getName();
        editor.editName(newName);
        saveBinder(editor);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Binder space " + binder.getBinderId() + " renamed from '" + oldName + "' to '" + newName
              + "'");
        }
      } // else, null name skipped
    }

    public void touch() throws MoxtraClientException, MoxtraException, RepositoryException {
      ensureBinderMember();

      // Moxtra.currentUserName();
      runSyncJob(this);
    }

    public String newPage(String name, String type, Node parent) throws RepositoryException,
                                                                MoxtraClientException,
                                                                MoxtraException {
      try {
        String nodeName = cleanNodeName(name);
        // create local stub-node
        Node convo = writeNewConversation(nodeName, type, parent);
        convo.setProperty("exo:title", name);
        convo.setProperty("exo:name", name);
        convo.save();

        // upload it to Moxtra
        Node content = convo.getNode("jcr:content");
        String mimeType = content.getProperty("jcr:mimeType").getString();
        InputStream dataStream = content.getProperty("jcr:data").getStream();
        MoxtraClient client = moxtra.getClient();
        try {
          client.pageUpload(binder, mimeType, dataStream, name);
        } catch (OAuthProblemException e) {
          throw new MoxtraSocialException("Error creating new page '" + name + "'. " + e.getMessage(), e);
        } catch (OAuthSystemException e) {
          throw new MoxtraSocialException("Error creating new page '" + name + "'. " + e.getMessage(), e);
        } finally {
          dataStream.close();
        }

        // remember on new map
        String nodeUUID = convo.getUUID();
        newPages.put(nodeUUID, new ConversationRef(name));
        return nodeUUID;
      } catch (IOException e) {
        throw new MoxtraSocialException("Error creating new page " + name + ". " + e.getMessage(), e);
      }
    }

    /**
     * Check if given document (by its UUID) has a page for conversation or it is a such conversation. This
     * method return <code>false</code> for page/conversation currently creating.
     * 
     * @param nodeUUID {@link String}
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
     * Check if given document has a page for conversation or it is a such page. This method return
     * <code>false</code> for page/conversation currently creating.<br>
     * This method also will check if current eXo user is a member of the binder and invite it if it is not.
     * 
     * @param document
     * @throws RepositoryException
     * @throws MoxtraException
     * @throws MoxtraClientException
     */
    public boolean hasPage(Node document) throws RepositoryException, MoxtraClientException, MoxtraException {
      touch();
      boolean isDocument = isPageNode(document, true);
      if (isDocument) {
        return true;
      } else {
        boolean isConversation = isPageContent(document, false); // don't force refresh here again
        return isConversation;
      }
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
          InputStream dataStream = data.getProperty("jcr:data").getStream();
          try {
            if (isNoteMimeType(mimeType) || isDrawMimeType(mimeType)) {
              // it is a Note or Draw should be
              client.pageUpload(binder, mimeType, dataStream, docName);
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
          } finally {
            try {
              dataStream.close();
            } catch (IOException e) {
              LOG.warn("Error closing new page node data stream " + docName + ". " + e.getMessage(), e);
            }
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
        for (MoxtraPage page : binder.getPages()) {
          if (pageName.equals(page.getOriginalFileName())) {
            // TODO do we need check page type or other things?
            // we could ensure it was just created by created_time
            return page;
          }
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
        for (MoxtraPage page : binder.getPages()) {
          if (pageId.equals(page.getId())) {
            // TODO do we need check page type or other things?
            // we could ensure it was just created by created_time
            return page;
          }
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
      try {
        MoxtraClient client = ownerClient();
        refreshBinder(client);

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

          // first validate all currently uploading pages (local and new)
          for (NodeIterator pditer = JCR.findPageDocuments(spaceNode); pditer.hasNext();) {
            Node document = pditer.nextNode();
            // this will force page node creation if it was created in Moxtra
            hasPageNode(document, false);
          }
          for (NodeIterator pditer = JCR.findPageContents(spaceNode); pditer.hasNext();) {
            Node pageContent = pditer.nextNode();
            // this will force page node creation if it was created in Moxtra
            hasPageContent(pageContent, false);
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
            for (MoxtraFeed feed : feeds) {
              long feedTimestamp = feed.getPublishedTime().getTime();

              if (LOG.isDebugEnabled()) {
                LOG.debug(">> feed: " + feed + " << " + feed.getPublishedTime());
              }

              // skip already applied feeds
              if (feedTimestamp > timestamp) {
                String object = feed.getObjectType();
                String target = feed.getTargetType();
                String verb = feed.getVerb();
                if (target.equals(MoxtraFeed.OBJECT_TYPE_BINDER)) {
                  // updates in binder objects
                  if (object.equals(MoxtraFeed.OBJECT_TYPE_PAGE)) {
                    if (verb.equals(MoxtraFeed.VERB_CREATE)) {
                      // created new page
                      addFeed(feed, applyList);
                    } else if (verb.equals(MoxtraFeed.VERB_UPDATE)) {
                      // file updated
                      addFeed(feed, applyList);
                    } else if (verb.equals(MoxtraFeed.VERB_DELETE)) {
                      // ensure this page not in feeds to apply
                      removeFeed(feed, applyList);
                    } else {
                      if (LOG.isDebugEnabled()) {
                        LOG.debug("Feed verb (" + verb + ") not supported: " + feed);
                      }
                    }
                  } else if (object.equals(MoxtraFeed.OBJECT_TYPE_FILE)) {
                    // FYI ignore file uploads - they'll become pages when format supported
                    // } else if (verb.equals(MoxtraFeed.VERB_UPLOAD)) {
                    // // file uploaded (what a difference to created page?)
                    // addFeed(feed, applyList);
                  } else if (object.equals(MoxtraFeed.OBJECT_TYPE_PERSON)) {
                    // skip users ops silently
                  } else {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Feed object (" + object + ") not supported: " + feed);
                    }
                  }
                } else if (target.equals(MoxtraFeed.OBJECT_TYPE_PAGE)) {
                  // updates in page (annotations, comments)
                  if (object.equals(MoxtraFeed.OBJECT_TYPE_ANNOTATION)) {
                    if (verb.equals(MoxtraFeed.VERB_TAG)) {
                      // annotated
                      addFeed(feed, applyList);
                    } else {
                      if (LOG.isDebugEnabled()) {
                        LOG.debug("Feed verb (" + verb + ") not supported: " + feed);
                      }
                    }
                  } else {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Feed object (" + object + ") not supported: " + feed);
                    }
                  }
                } else if (target.equals(MoxtraFeed.OBJECT_TYPE_PERSON)) {
                  // skip users ops silently
                } else if (target.equals(MoxtraFeed.OBJECT_TYPE_BINDER)) {
                  // skip binder ops silently
                } else {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Feed target (" + target + ") not supported: " + feed);
                  }
                }
              }
            }

            long maxTimestamp = 0;
            for (MoxtraFeed feed : applyList) {
              if (LOG.isDebugEnabled()) {
                LOG.debug(">>> apply : " + feed + " << " + feed.getPublishedTime());
              }

              // for existing find page document
              String pageId;
              String pageUrl; // for logs & errors
              if (feed.getTargetType().equals(MoxtraFeed.OBJECT_TYPE_BINDER)) {
                // it's page/file created
                pageId = feed.getObjectId();
                pageUrl = feed.getObjectUrl();
              } else {
                // page update (like annotation assumed here)
                pageId = feed.getTargetId();
                pageUrl = feed.getTargetUrl();
              }

              Node pageNode;
              MoxtraFeed applied = null;
              try {
                pageNode = JCR.getPage(binderNode, pageId);

                // FYI it is expected a single reference property (but if node will be copied by an user...)
                PropertyIterator pageRefs = pageNode.getReferences();
                while (pageRefs.hasNext()) {
                  Property pageRef = pageRefs.nextProperty();
                  Node document = pageRef.getParent();
                  Node parent = document.getParent();
                  if (JCR.isPageContent(document)) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Saving conversation for remote page " + pageId + " (" + pageUrl + ")");
                    }
                    Node convo = writeConversation(document.getName(),
                                                   pageId,
                                                   feed.getPublishedTime(),
                                                   parent,
                                                   client);
                    applied = feed;
                  } else {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Saving conversation of local document " + document.getPath() + " page "
                          + pageId + " (" + pageUrl + ")");
                    }

                    // create conversation file near the original document: $DOCNAME_Conversation.docExt
                    String nodeName = convoName(document.getName(), "_conversation");
                    boolean isNew = !parent.hasNode(nodeName);
                    Node convo = writeConversation(nodeName, pageId, feed.getPublishedTime(), parent, client);
                    if (isNew) {
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
                    applied = feed;
                  }
                }
              } catch (PathNotFoundException e) {
                // for new create a page document in space's root
                // (or respectively to the folder structure in Moxtra)
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Creating conversation for remote page " + pageId + " (" + pageUrl + ")");
                }

                // page node not found, we'll create it and a conversation node for it,
                // in this case page exists w/o a document
                MoxtraPage page = findPageById(pageId);
                if (page != null) {
                  // create page in root of space Documents
                  Node documents = getSpaceDocumentsNode(Moxtra.currentUserName(), space.getGroupId());

                  String pageName = page.getOriginalFileName();
                  if (pageName == null || pageName.trim().length() == 0) {
                    pageName = binder.getName() + " page #" + pageId;
                  }

                  Node convo = writeConversation(cleanNodeName(pageName),
                                                 pageId,
                                                 feed.getPublishedTime(),
                                                 documents,
                                                 client);

                  // create page and reference conversation to it
                  pageNode = writePage(page);
                  JCR.setPageRef(convo, pageNode);
                  String convoName = convoName(pageName, " Conversation");
                  convo.setProperty("exo:title", convoName);
                  convo.setProperty("exo:name", convoName);
                  convo.save();

                  applied = feed;
                } else {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Moxtra page " + pageId + " (" + pageUrl + ") not found in the binder "
                        + binder);
                  }
                }
              }
              if (applied != null && applied.getPublishedTime().getTime() > maxTimestamp) {
                maxTimestamp = applied.getPublishedTime().getTime();
              }
            }
            if (maxTimestamp > 0) {
              Date newRefreshedTime = new Date(maxTimestamp);
              JCR.setRefreshedTime(binderNode, newRefreshedTime); // will save in finally below
              if (LOG.isDebugEnabled()) {
                LOG.debug(">>> new refreshed time: " + newRefreshedTime);
              }
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

    protected void addFeed(MoxtraFeed feed, List<MoxtraFeed> feeds) {
      for (Iterator<MoxtraFeed> fiter = feeds.iterator(); fiter.hasNext();) {
        MoxtraFeed otherFeed = fiter.next();
        if (feed.isSameObject(otherFeed)) {
          if (feed.getPublishedTime().getTime() >= otherFeed.getPublishedTime().getTime()) {
            fiter.remove();
            break;
          } else {
            return; // a feed for this object already exists
          }
        }
      }
      feeds.add(feed);
    }

    protected void removeFeed(MoxtraFeed feed, List<MoxtraFeed> feeds) {
      for (Iterator<MoxtraFeed> fiter = feeds.iterator(); fiter.hasNext();) {
        MoxtraFeed otherFeed = fiter.next();
        if (feed.isSameObject(otherFeed)) {
          fiter.remove();
          break; // we assume feed of same object happens once in the list
        }
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
        throw new MoxtraSocialException("Error reading space node " + getSpace().getDisplayName() + ". "
            + e.getMessage(), e);
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

    protected boolean hasPageNode(Node document, boolean refresh) throws RepositoryException,
                                                                 MoxtraClientException,
                                                                 MoxtraException {
      try {
        Property creatingTimeProp = JCR.getCreatingTime(document);
        Calendar creatingTime = creatingTimeProp.getDate();
        // w/o checking in Moxtra we check for time how long this page is creating and if longer of some
        // allowed period - we assume it is not a page (removed/moved/renamed in Moxtra or like that)
        if (refresh) {
          refreshBinder();
        }
        Property pageNameProp = JCR.getName(document);
        MoxtraPage page = findPage(pageNameProp.getString());
        if (page != null) {
          // page already created in Moxtra, add local node
          Node pageNode = writePage(page);
          // update document node (remove creating time props)
          creatingTimeProp.remove();
          pageNameProp.remove();
          JCR.setPageRef(document, pageNode);
          document.save();
          return true;
        } else if (System.currentTimeMillis() - creatingTime.getTimeInMillis() < MAX_PAGE_CREATING_TIME) {
          return true;
        }
      } catch (PathNotFoundException e) {
        // when creating time not found, then check if page node exists
        return JCR.hasPageRef(document);
      }
      return false;
    }

    protected boolean isPageNode(Node document, boolean refresh) throws RepositoryException,
                                                                MoxtraClientException,
                                                                MoxtraException {
      if (JCR.isPageDocument(document)) {
        return hasPageNode(document, refresh);
      } else {
        return false;
      }
    }

    protected boolean hasPageContent(Node node, boolean refresh) throws RepositoryException,
                                                                MoxtraClientException,
                                                                MoxtraException {
      if (JCR.hasPageRef(node)) {
        return true;
      } else {
        if (refresh) {
          refreshBinder();
        }
        ConversationRef nodeRef = newPages.get(node.getUUID());
        if (nodeRef != null) {
          MoxtraPage page = findPage(nodeRef.name);
          int i = 3;
          do {
            if (page != null) {
              // page already created in Moxtra, add local node
              Node pageNode = writePage(page);
              // update document node (remove creating time props)
              newPages.remove(node.getUUID());
              JCR.setPageRef(node, pageNode);
              node.save();
              return true;
            } else {
              if (nodeRef.isCreatingExpired()) {
                return false;
              }
              // XXX wait a bit, refresh the binder and try again
              try {
                Thread.sleep(2000);
                refreshBinder();
                page = findPage(nodeRef.name);
              } catch (InterruptedException e) {
                LOG.warn("Page content thread sleep interrupted: " + e);
                break;
              }
            }
          } while ((--i) > 0);
        } else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Page content node " + node.getPath() + " not found in new pages");
          }
        }
      }
      return false;
    }

    protected boolean isPageContent(Node node, boolean refresh) throws RepositoryException,
                                                               MoxtraClientException,
                                                               MoxtraException {
      if (JCR.isPageContent(node)) {
        return hasPageContent(node, refresh);
      } else {
        return false;
      }
    }

    protected Node writePage(MoxtraPage page) throws MoxtraSocialException, RepositoryException {
      Node spaceNode = spaceNode();
      Node binderNode = JCR.getBinder(spaceNode);
      Node pageNode = JCR.addPage(binderNode, String.valueOf(page.getId()));

      JCR.setId(pageNode, page.getId());
      JCR.setName(pageNode, page.getOriginalFileName());
      JCR.setType(pageNode, page.getType());
      JCR.setRevision(pageNode, page.getRevision());
      JCR.setIndex(pageNode, page.getIndex());
      JCR.setNumber(pageNode, page.getNumber());
      JCR.setPageUrl(pageNode, page.getUrl());
      JCR.setThumbnailUrl(pageNode, page.getThumbnailUrl());
      JCR.setBackgroundUrl(pageNode, page.getBackgroundUrl());

      binderNode.save();

      return pageNode;
    }

    protected Node writeConversation(String nodeName,
                                     String pageId,
                                     Date publishedTime,
                                     Node parent,
                                     MoxtraClient client) throws RepositoryException,
                                                         MoxtraClientException,
                                                         OAuthSystemException,
                                                         OAuthProblemException,
                                                         MoxtraException,
                                                         IOException {
      boolean isNew;
      Node convo, content;
      try {
        convo = parent.getNode(nodeName);
        if (JCR.isPageContent(convo)) {
          // it is conversation content update
          content = convo.getNode("jcr:content");
          isNew = false;
        } else {
          // FIXME node with such name exists and it is not a Moxtra page conversation
          throw new ItemExistsException("Cannot create page conversation: document already exists with such name '"
              + nodeName + "'");
        }
      } catch (PathNotFoundException e) {
        convo = parent.addNode(nodeName, "nt:file");
        content = convo.addNode("jcr:content", "nt:resource");
        // TODO do need add mixins for exo:name/exo:title explicitly?
        // if (!convo.hasProperty(org.exoplatform.ecm.webui.utils.Utils.EXO_TITLE)) {
        // convo.addMixin(org.exoplatform.ecm.webui.utils.Utils.EXO_RSS_ENABLE);
        // }
        isNew = true;
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
        created.setTime(publishedTime);
        content.setProperty("jcr:lastModified", created);
        content.setProperty("jcr:data", dataStream);
        if (isNew) {
          parent.save();
        } else {
          convo.save();
        }
      } finally {
        dataStream.close();
      }
      return convo;
    }

    protected Node writeNewConversation(String nodeName, String type, Node parent) throws RepositoryException,
                                                                                  IOException {
      Node convo = parent.addNode(nodeName, "nt:file");
      Node content = convo.addNode("jcr:content", "nt:resource");
      // TODO do need add mixins for exo:name/exo:title explicitly?
      // if (!convo.hasProperty(org.exoplatform.ecm.webui.utils.Utils.EXO_TITLE)) {
      // convo.addMixin(org.exoplatform.ecm.webui.utils.Utils.EXO_RSS_ENABLE);
      // }

      JCR.addPageContent(convo);

      // use blank content predefined in client
      InputStream dataStream;
      String mimeType;
      if (MoxtraPage.PAGE_TYPE_NOTE.equals(type)) {
        dataStream = moxtra.getBlankNoteContent();
        mimeType = "text/html";
      } else {
        dataStream = moxtra.getBlankWhiteboardContent();
        mimeType = "image/png";
      }
      try {
        content.setProperty("jcr:mimeType", mimeType);
        java.util.Calendar created = java.util.Calendar.getInstance();
        content.setProperty("jcr:lastModified", created);
        content.setProperty("jcr:data", dataStream);
        parent.save(); // save and let actions add ECMS mixins
      } finally {
        dataStream.close();
      }
      return convo;
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
    protected void saveBinder(MoxtraBinder binder) throws RepositoryException, MoxtraException {
      // try find owner client (actual for SSO mode)
      MoxtraClient client = ownerClient();

      try {
        if (binder.hasNameChanged()) {
          client.renameBinder(binder);
        }
        if (binder.hasUsersAdded()) {
          client.inviteUsers(binder);
        }
        if (binder.hasUsersRemoved()) {
          client.removeUsers(binder);
        }

        // refresh the instance binder object!
        client.refreshBinder(this.binder);
      } catch (OAuthProblemException e) {
        throw new MoxtraSocialException("Error saving binder " + binder.getName() + " ("
            + binder.getBinderId() + "). " + e.getMessage(), e);
      } catch (OAuthSystemException e) {
        throw new MoxtraSocialException("Error saving binder " + binder.getName() + " ("
            + binder.getBinderId() + "). " + e.getMessage(), e);
      }

      Node spaceNode = spaceNode();
      Node binderNode = JCR.getBinder(spaceNode);
      writeBinder(binderNode, this.binder);
      binderNode.save();
    }

    /**
     * Refresh current binder using its owner user's client when available (e.g. in SSO org mode) to actual
     * state in Moxtra.
     * 
     * @throws OAuthSystemException
     * @throws OAuthProblemException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    protected void refreshBinder() throws RepositoryException, MoxtraException {
      // TODO should we use current user for refresh?
      // try find owner client (actual for SSO mode)
      MoxtraClient client = ownerClient();
      refreshBinder(client);
    }

    /**
     * Refresh current binder using given Moxtra client.
     * 
     * @throws OAuthSystemException
     * @throws OAuthProblemException
     * @throws MoxtraException
     * @throws RepositoryException
     */
    protected void refreshBinder(MoxtraClient client) throws RepositoryException, MoxtraException {
      // refresh the instance binder object!
      try {
        client.refreshBinder(this.binder);
      } catch (OAuthProblemException e) {
        throw new MoxtraSocialException("Error refreshing binder " + binder.getName() + " ("
            + binder.getBinderId() + "). " + e.getMessage(), e);
      } catch (OAuthSystemException e) {
        throw new MoxtraSocialException("Error refreshing binder " + binder.getName() + " ("
            + binder.getBinderId() + "). " + e.getMessage(), e);
      }

      Node spaceNode = spaceNode();
      Node binderNode = JCR.getBinder(spaceNode);
      writeBinder(binderNode, this.binder);
      binderNode.save();
    }

    protected MoxtraClient ownerClient() throws MoxtraClientException {
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
      return client;
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

  protected class ConversationRef {
    protected final long   createdTime;

    protected final String name;

    protected ConversationRef(String name) {
      this.createdTime = System.currentTimeMillis();
      this.name = name;
    }

    protected boolean isCreatingExpired() {
      return System.currentTimeMillis() - createdTime > MAX_PAGE_CREATING_TIME;
    }
  }

  protected final MoxtraCalendarService        moxtraCalendar;

  protected final Set<String>                  noteMimeTypes  = new HashSet<String>();

  protected final Set<String>                  drawMimeTypes  = new HashSet<String>();

  protected final Set<SpaceApplication>        addedSpaceApps = new HashSet<SpaceApplication>();

  protected final Map<String, ConversationRef> newPages       = new ConcurrentHashMap<String, ConversationRef>();

  /**
   * @throws MoxtraConfigurationException
   * 
   */
  public MoxtraSocialService(MoxtraService moxtra,
                             MoxtraCalendarService moxtraCalendar,
                             SessionProviderService sessionProviderService,
                             NodeHierarchyCreator hierarchyCreator,
                             IDGeneratorService idGenerator,
                             OrganizationService orgService,
                             JobSchedulerServiceImpl schedulerService,
                             ManageDriveService driveService) {
    super(moxtra,
          sessionProviderService,
          hierarchyCreator,
          idGenerator,
          orgService,
          schedulerService,
          driveService);
    this.moxtraCalendar = moxtraCalendar;
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

  public MoxtraBinder getBinder(String binderId) throws MoxtraClientException, MoxtraException {
    try {
      return moxtra.getClient().getBinder(binderId);
    } catch (OAuthSystemException e) {
      throw new MoxtraSocialException("Error accessing binder " + binderId + ". " + e.getMessage(), e);
    } catch (OAuthProblemException e) {
      throw new MoxtraSocialException("Error accessing binder " + binderId + ". " + e.getMessage(), e);
    }
  }

  public MoxtraBinder getLocalBinder(String binderId) throws Exception {
    // try find binder locally first (in spaces by JCR query)
    Node binderNode = binderNode(binderId, null);
    if (binderNode != null) {
      return readBinder(binderNode);
    }
    return null;
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
      MoxtraBinderSpace binderSpace = getBinderSpace(space);
      return binderSpace;
    } else {
      throw new MoxtraSocialException("No space found in the context");
    }
  }

  /**
   * Return Moxtra binder space if it is enabled for the given space (by ID) or <code>null</code> if
   * not enabled.
   * 
   * @param spaceId String a space ID
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  public MoxtraBinderSpace getBinderSpace(String spaceId) throws MoxtraSocialException {
    Space space = spaceById(spaceId);
    if (space != null) {
      return getBinderSpace(space);
    } else {
      throw new MoxtraSocialException("Space not found " + spaceId);
    }
  }

  /**
   * Return Moxtra binder space if it is enabled for the given space or <code>null</code> if
   * not enabled.
   * 
   * @param space {@link Space}
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  public MoxtraBinderSpace getBinderSpace(Space space) throws MoxtraSocialException {
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
      throw new MoxtraSocialException("Error getting binder space '" + space.getDisplayName() + "'. "
          + e.getMessage(), e);
    }
    return null;
  }

  /**
   * Return Moxtra binder space if it is enabled for the given Moxtra Binder or <code>null</code> if not
   * enabled.<br>
   * TODO THIS METHOD DEPRECATED, USE GETTER BY SPACE INSTEAD.
   * 
   * @param binder {@link MoxtraBinder}
   * @return {@link MoxtraBinderSpace} instance or <code>null</code> if binder not enabled
   * @throws MoxtraSocialException
   */
  @Deprecated
  public MoxtraBinderSpace getBinderSpace(MoxtraBinder binder) throws MoxtraSocialException {
    try {
      Node binderNode = binderNode(binder);
      if (binderNode != null) {
        Node spaceNode = binderNode.getParent();
        if (JCR.isServices(spaceNode)) {
          Space space;
          try {
            String spaceId = JCR.getSpaceId(binderNode).getString();
            space = spaceById(spaceId);
          } catch (PathNotFoundException e) {
            // find space node by its groups' node name (it is a pretty name), this will work while space not
            // renamed after creation
            space = spaceByName(spaceNode.getName());
          }
          if (space != null) {
            return new MoxtraBinderSpace(space, binder, false);
          } else {
            throw new MoxtraSocialException("Cannot find a space '" + spaceNode.getName()
                + "' associated with binder " + binder.getName());
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
   * Find space by its name (pretty name or display name). Use this method with caution as space can be
   * renamed during its life.
   * 
   * @return {@link Space} instance of <code>null</code> if such space cannot be found or error happen
   */
  @Deprecated
  protected Space spaceByName(String name) {
    try {
      SpaceService service = spaceService();
      Space space = service.getSpaceByPrettyName(name);// service.getSpaceById("2065ac2ec0a8016548878fc4570e0c4f")
      if (space == null) {
        space = service.getSpaceByDisplayName(name);
      }// service.getS
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

  /**
   * Find space by its id.
   * 
   * @return {@link Space} instance of <code>null</code> if such space cannot be found or error happen
   */
  protected Space spaceById(String id) {
    try {
      SpaceService service = spaceService();
      Space space = service.getSpaceById(id);
      return space;
    } catch (NullPointerException e) {
      // XXX NPE has a place when running not in portal request, assume it as normal
      if (LOG.isDebugEnabled()) {
        LOG.debug("NPE when getting space by id: " + id + ". " + e.getMessage(), e);
      }
    } catch (Throwable e) {
      LOG.warn("Error when getting space by id: " + id + ". " + e.getMessage(), e);
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

  protected String convoName(String name, String convoSuffix) {
    StringBuilder cname = new StringBuilder();
    int nameEnd = name.lastIndexOf(".");
    if (nameEnd > 0) {
      cname.append(name.substring(0, nameEnd));
      // cname.append(name.substring(nameEnd)); // ignore extension
    } else {
      cname.append(name);
    }
    if (convoSuffix != null) {
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

  /**
   * Find the space group node (as /Groups/spaces/$SPACE_GROUP_ID).<br>
   * 
   * @param space {@link Space}
   * @return
   * @throws Exception
   */
  protected Node spaceNode(Space space) throws Exception {
    Session sysSession = hierarchyCreator.getPublicApplicationNode(sessionProviderService.getSystemSessionProvider(null))
                                         .getSession();
    String groupsPath = hierarchyCreator.getJcrPath(BasePath.CMS_GROUPS_PATH);

    Node spaceNode;
    try {
      String spaceFolder = groupsPath + space.getGroupId();
      spaceNode = (Node) sysSession.getItem(spaceFolder);
    } catch (PathNotFoundException e) {
      // try by pretty name
      String spaceFolder = groupsPath + "/spaces/" + space.getPrettyName();
      try {
        spaceNode = (Node) sysSession.getItem(spaceFolder);
      } catch (PathNotFoundException pne) {
        // also not found
      }
      throw new MoxtraSocialException("Cannot find space node " + space, e);
    }

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
      NodeIterator binderNodes = JCR.findBinder((Node) sysSession.getItem(spacesFolder), binder.getBinderId());
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

  protected Node binderNode(String binderId, Space space) throws Exception {
    Node node;
    if (space != null) {
      node = spaceNode(space);
    } else {
      String groupsPath = hierarchyCreator.getJcrPath(BasePath.CMS_GROUPS_PATH);
      String spacesFolder = groupsPath + "/spaces/";
      Session sysSession = hierarchyCreator.getPublicApplicationNode(sessionProviderService.getSystemSessionProvider(null))
                                           .getSession();
      node = (Node) sysSession.getItem(spacesFolder);
    }
    Node binderNode;
    // search using JCR query and binder NT and id
    NodeIterator binderNodes = JCR.findBinder(node, binderId);
    long nodesCount = binderNodes.getSize();
    if (nodesCount > 0) {
      binderNode = binderNodes.nextNode();
      if (nodesCount > 1) {
        LOG.warn("Found more than one binder node for " + binderId + " in " + node.getPath()
            + ", using first one " + binderNode.getPath());
      }
    } else {
      binderNode = null;
    }
    return binderNode;
  }

  /**
   * Save binder node in the space group node (in /Groups/spaces/$SPACE_GROUP_ID).
   * 
   * @param space {@link Space}
   * @param binder {@link MoxtraBinder}
   * @throws Exception
   */
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

    // save the space id for finding it later in SpaceService
    JCR.setSpaceId(binderNode, space.getId());

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
    jobData.put(MoxtraBinderSyncJob.DATA_SPACE_ID, binderSpace.getSpace().getId());
    jobData.put(MoxtraBinderSyncJob.DATA_GROUP_ID, binderSpace.getSpace().getGroupId());
    // jobData.put(MoxtraBinderSyncJob.DATA_BINDER_ID, binderSpace.getBinder().getBinderId());

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
