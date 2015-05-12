/*
 * Copyright (C) 2003-2014 eXo Platform SAS.
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

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.ext.filter.UIExtensionFilter;
import org.exoplatform.webui.ext.filter.UIExtensionFilterType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;

/**
 * Filter files by MIME type including wildcard types. This filter will try get a MIME type from the context
 * (will work for file view in JCR explorer) and if not found then will read it from the context node.<br>
 * 
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: FileTypeFilter.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
public class FileTypeFilter implements UIExtensionFilter {

  protected static final Log LOG = ExoLogger.getLogger(FileTypeFilter.class);

  protected Set<String>      mimeTypes;

  public boolean accept(Map<String, Object> context) throws Exception {
    if (mimeTypes == null || mimeTypes.isEmpty()) {
      return true;
    }

    String mimeType;
    Object obj = context.get("mimeType");
    if (obj != null && obj instanceof String) {
      // we're in file view or like that
      mimeType = (String) obj;
    } else {
      // find mimetype from the context node
      Node currentNode = (Node) context.get(Node.class.getName());
      if (currentNode != null && currentNode.isNodeType("nt:file")) {
        try {
          mimeType = currentNode.getNode("jcr:content").getProperty("jcr:mimeType").getString();
        } catch (PathNotFoundException e) {
          return false;
        }
      } else {
        return false;
      }
    }

    // try quick check first
    if (mimeTypes.contains(mimeType)) {
      return true;
    }

    for (String accepted : mimeTypes) {
      if (mimeType.startsWith(accepted)) {
        return true;
      }
    }

    return false;
  }

  public UIExtensionFilterType getType() {
    return UIExtensionFilterType.MANDATORY;
  }

  public void onDeny(Map<String, Object> context) throws Exception {
  }
}
