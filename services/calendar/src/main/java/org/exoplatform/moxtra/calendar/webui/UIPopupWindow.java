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
package org.exoplatform.moxtra.calendar.webui;

import org.exoplatform.commons.serialization.api.annotations.Serialized;
import org.exoplatform.moxtra.calendar.MoxtraCalendarApplication;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIContainer;

/**
 * The same as {@link UIPopupWindow} functionality but call {@link MoxtraCalendarApplication} action on
 * {@link UIComponent} setter.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIPopupWindow.java 00000 Mar 12, 2015 pnedonosko $
 * 
 */
@ComponentConfig(
                 template = "system:/groovy/webui/core/UIPopupWindow.gtmpl",
                 events = @EventConfig(
                                       listeners = org.exoplatform.webui.core.UIPopupWindow.CloseActionListener.class,
                                       name = "ClosePopup"))
@Serialized
public class UIPopupWindow extends org.exoplatform.webui.core.UIPopupWindow {

  protected MoxtraCalendarApplication moxtra;

  /**
   * Empty constructor.
   */
  public UIPopupWindow() {
    setId("UICalendarPopupWindow");
  }

  /**
   * Associate Moxtra support app with this popup.
   * 
   * @param moxtra {@link MoxtraCalendarApplication}
   */
  public void initMoxtra(MoxtraCalendarApplication moxtra) {
    this.moxtra = moxtra;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UIComponent setUIComponent(UIComponent uicomponent) {
    activate(uicomponent);
    return super.setUIComponent(uicomponent);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UIComponent getUIComponent() {
    UIComponent uicomponent = super.getUIComponent();
    activate(uicomponent);
    return uicomponent;
  }

  protected void activate(UIComponent uicomponent) {
    if (moxtra != null && uicomponent instanceof UIContainer) {
      moxtra.initEventFormContainer((UIContainer) uicomponent);
    }
  }
}
