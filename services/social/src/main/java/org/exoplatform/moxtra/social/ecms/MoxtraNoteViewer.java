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

import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraNoteViewer.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
@Deprecated
@ComponentConfig(template = "classpath:templates/moxtra/social/ecms/MoxtraNoteViewer.gtmpl")
public class MoxtraNoteViewer extends BaseMoxtraSocialDocumentManagerComponent {

  /**
   * {@inheritDoc}
   */
  @Override
  public void processRender(WebuiRequestContext context) throws Exception {
    initContext();

    // TODO
    // Object obj = context.getAttribute(CloudDrive.class);
    // if (obj != null) {
    // CloudDrive drive = (CloudDrive) obj;
    // obj = context.getAttribute(CloudFile.class);
    // if (obj != null) {
    // initFile(drive, (CloudFile) obj);
    // }
    // }

    super.processRender(context);
  }

}
