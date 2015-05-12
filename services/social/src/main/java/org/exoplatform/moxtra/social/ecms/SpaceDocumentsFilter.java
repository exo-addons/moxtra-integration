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

import org.exoplatform.ecm.webui.component.explorer.UIJCRExplorer;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.webui.ext.filter.UIExtensionFilter;
import org.exoplatform.webui.ext.filter.UIExtensionFilterType;

import java.util.Map;

import javax.jcr.Node;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: SpaceDocumentsFilter.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
public class SpaceDocumentsFilter implements UIExtensionFilter {

  protected static final Log LOG = ExoLogger.getLogger(SpaceDocumentsFilter.class);

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean accept(Map<String, Object> context) throws Exception {
    // only show in space docs' subfolders
    UIJCRExplorer uiExplorer = (UIJCRExplorer) context.get(UIJCRExplorer.class.getName());
    NodeHierarchyCreator nodeHierarchyCreator = WCMCoreUtils.getService(NodeHierarchyCreator.class);

    // TODO
    // String userId = Util.getPortalRequestContext().getRemoteUser();
    // SessionProvider sessionProvider = WCMCoreUtils.getUserSessionProvider();
    // Node userNode = nodeHierarchyCreator.getUserNode(sessionProvider, userId);
    // String driveRootPath =
    // org.exoplatform.services.cms.impl.Utils.getPersonalDrivePath(uiExplorer.getDriveData()
    // .getHomePath(),
    // userId);

    String driveRootPath = uiExplorer.getDriveData().getHomePath();

    String groupPath = nodeHierarchyCreator.getJcrPath(BasePath.CMS_GROUPS_PATH);
    String spacesFolder = groupPath + "/spaces/";
    if (driveRootPath.startsWith(spacesFolder)) {
      Node currentNode = (Node) context.get(Node.class.getName());
      String nodePath = currentNode.getPath();
      if (nodePath.startsWith(driveRootPath)) {
        // TODO check selected doc mimetype and allow only for selected types
        String mimeType = getMimetype(currentNode);
        
        
        // CloudDriveContext.initConnected(WebuiRequestContext.getCurrentInstance(), (Node) personalDocs);
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onDeny(Map<String, Object> context) throws Exception {
    // TODO do we need smth here? kind of cleanup or disable some UI things?
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UIExtensionFilterType getType() {
    return UIExtensionFilterType.MANDATORY;
  }
  
  // ******* internals *******
  
  protected String getMimetype(Node document) {
    
    // TODO
    return null;
  }
  

}
