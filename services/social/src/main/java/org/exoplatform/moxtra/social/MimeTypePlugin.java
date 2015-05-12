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
package org.exoplatform.moxtra.social;

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MimeTypePlugin.java 00000 May 8, 2015 pnedonosko $
 * 
 */
public class MimeTypePlugin extends BaseComponentPlugin {

  protected static final Log               LOG                   = ExoLogger.getLogger(MimeTypePlugin.class);

  protected final String                   ADD_NOTE_MIMETYPES    = "addNoteMimetypes";

  protected final String                   REMOVE_NOTE_MIMETYPES = "removeNoteMimetypes";

  protected final String                   ADD_DRAW_MIMETYPES    = "addDrawMimetypes";

  protected final String                   REMOVE_DRAW_MIMETYPES = "removeDrawMimetypes";

  protected final Map<String, Set<String>> mimeTypes             = new HashMap<String, Set<String>>();

  /**
   * 
   */
  public MimeTypePlugin(InitParams params) {
    Iterator<ValuesParam> vparams = params.getValuesParamIterator();
    while (vparams.hasNext()) {
      ValuesParam vp = vparams.next();
      Set<String> types = new HashSet<String>();
      for (Object mto : vp.getValues()) {
        if (mto instanceof String) {
          types.add((String) mto);
        } else {
          LOG.warn("Unsupported value type in mime types " + mto);
        }
      }
      mimeTypes.put(vp.getName(), types);
    }
  }

  public Set<String> getNoteAddedTypes() {
    return getOrEmpty(ADD_NOTE_MIMETYPES);
  }

  public Set<String> getNoteRemovedTypes() {
    return getOrEmpty(REMOVE_NOTE_MIMETYPES);
  }
  
  public Set<String> getDrawAddedTypes() {
    return getOrEmpty(ADD_DRAW_MIMETYPES);
  }

  public Set<String> getDrawRemovedTypes() {
    return getOrEmpty(REMOVE_DRAW_MIMETYPES);
  }
  
  protected Set<String> getOrEmpty(String key) {
    Set<String> types = mimeTypes.get(key);
    if (types != null) {
      return Collections.unmodifiableSet(types);
    } else {
      return Collections.emptySet();
    }
  }
}
