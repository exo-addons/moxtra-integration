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
package org.exoplatform.moxtra.commons;

import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.ecm.utils.text.Text;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraAuthenticationException;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.cms.impl.Utils;
import org.exoplatform.services.idgenerator.IDGeneratorService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.scheduler.impl.JobSchedulerServiceImpl;
import org.exoplatform.services.security.ConversationState;
import org.quartz.JobDetail;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.Node;
import javax.jcr.Session;

/**
 * Base class for building container components and services used Moxtra client.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: BaseMoxtraService.java 00000 May 18, 2015 pnedonosko $
 * 
 */
public abstract class BaseMoxtraService {

  public static final String MOXTRA_JOB_GROUP_NAME = "moxtra_sync";

  public static final String MOXTRA_CURRENT_USER   = "moxtra.currentUser";

  protected class UserSettings {
    final ConversationState conversation;

    final ExoContainer      container;

    ConversationState       prevConversation;

    ExoContainer            prevContainer;

    SessionProvider         prevSessions;

    UserSettings(ConversationState conversation, ExoContainer container) {
      this.conversation = conversation;
      this.container = container;
    }
  }

  /**
   * Setup environment for jobs execution in eXo Container.
   */
  protected class Environment {

    protected final Map<String, UserSettings> config = new ConcurrentHashMap<String, UserSettings>();

    public void configure(String userName) throws MoxtraServiceException {
      ConversationState conversation = ConversationState.getCurrent();
      if (conversation == null) {
        throw new MoxtraServiceException("Error configuring user environment for " + userName
            + ". User identity not set.");
      }
      config.put(userName, new UserSettings(conversation, ExoContainerContext.getCurrentContainer()));
    }

    public void prepare(String userName) throws MoxtraServiceException {
      UserSettings settings = config.get(userName);
      if (settings != null) {
        settings.prevConversation = ConversationState.getCurrent();
        ConversationState.setCurrent(settings.conversation);

        // set correct container
        settings.prevContainer = ExoContainerContext.getCurrentContainerIfPresent();
        ExoContainerContext.setCurrentContainer(settings.container);

        // set correct SessionProvider
        settings.prevSessions = sessionProviderService.getSessionProvider(null);
        SessionProvider sp = new SessionProvider(settings.conversation);
        sessionProviderService.setSessionProvider(null, sp);
      } else {
        throw new MoxtraServiceException("User setting not configured to prepare " + userName
            + " environment.");
      }
    }

    protected void cleanup(String userName) throws MoxtraServiceException {
      UserSettings settings = config.get(userName);
      if (settings != null) {
        ConversationState.setCurrent(settings.prevConversation);
        ExoContainerContext.setCurrentContainer(settings.prevContainer);
        SessionProvider sp = sessionProviderService.getSessionProvider(null);
        sessionProviderService.setSessionProvider(null, settings.prevSessions);
        sp.close();
      } else {
        throw new MoxtraServiceException("User setting not configured to clean " + userName + " environment.");
      }
    }
  }

  protected final MimeTypeResolver        mimetypeResolver = new MimeTypeResolver();

  protected final Environment             jobEnvironment   = new Environment();

  protected final MoxtraService           moxtra;

  /**
   * OrganizationService to find eXo users email.
   */
  protected final OrganizationService     orgService;

  protected final IDGeneratorService      idGenerator;

  protected final JobSchedulerServiceImpl schedulerService;

  protected final NodeHierarchyCreator    hierarchyCreator;

  protected final SessionProviderService  sessionProviderService;

  protected final ManageDriveService      driveService;

  /**
   * 
   */
  public BaseMoxtraService(MoxtraService moxtraService,
                           SessionProviderService sessionProviderService,
                           NodeHierarchyCreator hierarchyCreator,
                           IDGeneratorService idGenerator,
                           OrganizationService orgService,
                           JobSchedulerServiceImpl schedulerService,
                           ManageDriveService driveService) {
    this.moxtra = moxtraService;
    this.sessionProviderService = sessionProviderService;
    this.hierarchyCreator = hierarchyCreator;
    this.idGenerator = idGenerator;
    this.orgService = orgService;
    this.schedulerService = schedulerService;
    this.driveService = driveService;
  }

  /**
   * Checks if current user is already authorized to access Moxtra services.
   * 
   * @return <code>true</code> if user is authorized to access Moxtra services, <code>false</code> otherwise
   */
  public boolean isAuthorized() {
    return moxtra.getClient().isAuthorized();
  }

  public String getOAuth2Link() throws OAuthSystemException {
    return moxtra.getClient().authorizer().authorizationLink();
  }

  /**
   * Current user in Moxtra.<br>
   * This user will be cached in current {@link ConversationState} attribute.
   * 
   * @return {@link MoxtraUser}.
   * @throws MoxtraException
   * 
   * @see {@link MoxtraClient#getCurrentUser(boolean)}
   */
  public MoxtraUser getUser() throws MoxtraException {
    return moxtra.getClient().getCurrentUser();
  }

  /**
   * Current user contacts in Moxtra.<br>
   * 
   * @return {@link Collection} of {@link MoxtraUser}.
   * @throws MoxtraException
   * @throws MoxtraAuthenticationException
   */
  public List<MoxtraUser> getContacts() throws MoxtraAuthenticationException, MoxtraException {
    return moxtra.getClient().getContacts(getUser());
  }

  public void prepareJobEnvironment(JobDetail job) throws MoxtraServiceException {
    String exoUserId = job.getJobDataMap().getString(BaseMoxtraJob.DATA_USER_ID);
    jobEnvironment.prepare(exoUserId);
  }

  public void cleanupJobEnvironment(JobDetail job) throws MoxtraServiceException {
    String exoUserId = job.getJobDataMap().getString(BaseMoxtraJob.DATA_USER_ID);
    jobEnvironment.cleanup(exoUserId);
  }

  public String cleanNodeName(String name) {
    return Text.escapeIllegalJcrChars(Utils.cleanString(name));
  }

  /**
   * Find given user Personal Documents folder using system session.
   * 
   * @param userName {@link String}
   * @return {@link Node} Personal Documents folder node or <code>null</code>
   * @throws Exception
   */
  protected Node getUserDocumentsNode(String userName) throws Exception {
    // code idea based on ECMS's UIJCRExplorerPortlet.getUserDrive()
    for (DriveData userDrive : driveService.getPersonalDrives(userName)) {
      String homePath = userDrive.getHomePath();
      if (homePath.endsWith("/Private")) {
        // using system session!
        SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
        Node userNode = hierarchyCreator.getUserNode(sessionProvider, userName);
        String driveRootPath = org.exoplatform.services.cms.impl.Utils.getPersonalDrivePath(homePath,
                                                                                            userName);
        int uhlen = userNode.getPath().length();
        if (homePath.length() > uhlen) {
          // it should be w/o leading slash, e.g. "Private"
          String driveSubPath = driveRootPath.substring(uhlen + 1);
          return userNode.getNode(driveSubPath);
        }
      }
    }
    return null;
  }

  /**
   * Find given group Documents folder using system session.
   *
   * @param userName {@link String}
   * @param groupName {@link String}
   * @return {@link Node} space's Documents folder node or <code>null</code>
   * @throws Exception
   */
  protected Node getSpaceDocumentsNode(String userName, String groupName) throws Exception {
    // DriveData groupDrive = driveService.getDriveByName("Groups");
    String groupDriveName = groupName.replace("/", ".");
    DriveData groupDrive = driveService.getDriveByName(groupDriveName);
    if (groupDrive != null) {
      // using system session!
      SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      // we actually don't need user home node, just a JCR session
      Session session = hierarchyCreator.getUserNode(sessionProvider, userName).getSession();
      return (Node) session.getItem(groupDrive.getHomePath());
    } else {
      return null;
    }
  }

}
