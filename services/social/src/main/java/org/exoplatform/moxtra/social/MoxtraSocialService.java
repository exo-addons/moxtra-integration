package org.exoplatform.moxtra.social;

import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraBinder;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.scheduler.impl.JobSchedulerServiceImpl;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraSocialService.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
public class MoxtraSocialService {

  protected static final Log              LOG           = ExoLogger.getLogger(MoxtraSocialService.class);

  protected final MoxtraService           moxtra;

  /**
   * OrganizationService to find eXo users email.
   */
  protected final OrganizationService     orgService;

  protected final JobSchedulerServiceImpl schedulerService;

  protected final NodeHierarchyCreator    hierarchyCreator;

  protected final SessionProviderService  sessionProviderService;

  protected final ManageDriveService      driveService;

  protected final SpaceService            spaceService;

  protected final Set<String>             noteMimeTypes = new HashSet<String>();

  protected final Set<String>             drawMimeTypes = new HashSet<String>();

  /**
   * @throws MoxtraConfigurationException
   * 
   */
  public MoxtraSocialService(MoxtraService moxtra,
                             SessionProviderService sessionProviderService,
                             NodeHierarchyCreator hierarchyCreator,
                             OrganizationService orgService,
                             JobSchedulerServiceImpl schedulerService,
                             ManageDriveService driveService,
                             SpaceService spaceService) {
    this.moxtra = moxtra;
    this.sessionProviderService = sessionProviderService;
    this.hierarchyCreator = hierarchyCreator;
    this.orgService = orgService;
    this.schedulerService = schedulerService;
    this.driveService = driveService;
    this.spaceService = spaceService;
  }

  public synchronized void configureMimeTypePlugin(MimeTypePlugin plugin) {
    updateMimetypes(noteMimeTypes, plugin.getNoteAddedTypes(), plugin.getNoteRemovedTypes());
    updateMimetypes(drawMimeTypes, plugin.getDrawAddedTypes(), plugin.getDrawRemovedTypes());
  }

  /**
   * Create a new Moxtra binder for given space.
   * 
   * @param space
   * @throws RepositoryException
   * @throws MoxtraSocialException
   */
  public void createSpaceBinder(Space space) throws RepositoryException, MoxtraSocialException {
    // TODO
  }

  /**
   * Assign space to existing Moxtra binder.
   * 
   * @param space
   * @param binder
   * @throws RepositoryException
   * @throws MoxtraSocialException
   */
  public void assignSpaceBinder(Space space, MoxtraBinder binder) throws RepositoryException, MoxtraSocialException {
    // TODO
  }

  /**
   * Create page conversation.
   * 
   * @param document
   * @param binder
   * @throws RepositoryException
   * @throws MoxtraSocialException
   */
  public void createPageConversation(Node document, MoxtraBinder binder) throws RepositoryException,
                                                                    MoxtraSocialException {
    // TODO don't use binder parameter, but find it from the given node (it is in a space sub-tree)
    
    // TODO other nodetypes?
    if (document.isNodeType("nt:file")) {
      // detect the doc type and choose Note or Draw (Whiteboard)
      Node data = document.getNode("nt:resource");
      String mimeType = data.getProperty("jcr:mimeType").getString();
      if (isNoteMimeType(mimeType)) {
        // it is a Note should be

      } else if (isDrawMimeType(mimeType)) {
        // it is a Draw should be

      } else {
        // not supported document
        throw new MoxtraSocialException("Document type not supported for " + document.getName() + ": "
            + mimeType);
      }

      // TODO add mixin and Moxtra specific info
      document.addMixin("moxtra:page");
      document.save();
    } else {
      throw new MoxtraSocialException("Document not a file " + document.getName() + " "
          + document.getPrimaryNodeType().getName());
    }
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
}
