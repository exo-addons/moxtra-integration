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

import org.exoplatform.ecm.webui.component.explorer.control.filter.CanAddNodeFilter;
import org.exoplatform.ecm.webui.component.explorer.control.filter.IsCheckedOutFilter;
import org.exoplatform.ecm.webui.component.explorer.control.filter.IsNotCategoryFilter;
import org.exoplatform.ecm.webui.component.explorer.control.filter.IsNotEditingDocumentFilter;
import org.exoplatform.ecm.webui.component.explorer.control.filter.IsNotInTrashFilter;
import org.exoplatform.ecm.webui.component.explorer.control.filter.IsNotLockedFilter;
import org.exoplatform.ecm.webui.component.explorer.control.filter.IsNotTrashHomeNodeFilter;
import org.exoplatform.ecm.webui.component.explorer.control.listener.UIWorkingAreaActionListener;
import org.exoplatform.moxtra.client.MoxtraPage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.Parameter;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.ext.filter.UIExtensionFilter;
import org.exoplatform.webui.ext.filter.UIExtensionFilters;

import java.util.Arrays;
import java.util.List;

/**
 * Action stub for ECMS menu. See logic in Javascript.
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: AddMoxtraNoteManagerComponent.java 00000 Apr 29, 2015 pnedonosko $
 * 
 */
@ComponentConfig(
                 events = { @EventConfig(
                                         listeners = AddMoxtraWhiteboardManagerComponent.AddMoxtraWhiteboardActionListener.class) })
public class AddMoxtraWhiteboardManagerComponent extends BaseMoxtraSocialDocumentManagerComponent {

  protected static final Log LOG = ExoLogger.getLogger(AddMoxtraWhiteboardManagerComponent.class);

  public static class AddMoxtraWhiteboardActionListener
                                                       extends
                                                       UIWorkingAreaActionListener<AddMoxtraWhiteboardManagerComponent> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void processEvent(Event<AddMoxtraWhiteboardManagerComponent> event) throws Exception {
      AddMoxtraWhiteboardManagerComponent action = event.getSource();
      action.initNewPage(MoxtraPage.PAGE_TYPE_WHITEBOARD, event.getRequestContext());
    }
  }

  private final List<UIExtensionFilter> FILTERS = Arrays.asList(new UIExtensionFilter[] {// new
      // IsNotNtFileFilter(),
      new MoxtraBinderSpaceFilter(), new CanAddNodeFilter(), new IsNotCategoryFilter(),
      new IsNotLockedFilter(), new IsCheckedOutFilter(), new IsNotTrashHomeNodeFilter(),
      new IsNotInTrashFilter(), new IsNotEditingDocumentFilter() });

  @UIExtensionFilters
  public List<UIExtensionFilter> getFilters() {
    return FILTERS;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String renderEventURL(boolean ajax, String name, String beanId, Parameter[] params) throws Exception {
    initContext();
    return super.renderEventURL(ajax, name, beanId, params);
  }

}
