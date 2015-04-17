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

import org.exoplatform.calendar.service.Utils;
import org.exoplatform.moxtra.calendar.MoxtraCalendarException;
import org.exoplatform.moxtra.calendar.MoxtraNotActivatedException;
import org.exoplatform.moxtra.calendar.webui.UIEventForm.AddParticipantActionListener;
import org.exoplatform.moxtra.calendar.webui.UIEventForm.CancelActionListener;
import org.exoplatform.moxtra.calendar.webui.UIEventForm.SaveActionListener;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.ComponentConfigs;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;

/**
 * This component used as a config with action listeners that should be added to original UIEventForm from
 * calendar webapp .<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIEventForm.java 00000 Apr 6, 2015 pnedonosko $
 * 
 */
@ComponentConfigs({ @ComponentConfig(events = { @EventConfig(listeners = SaveActionListener.class),
    @EventConfig(listeners = AddParticipantActionListener.class, phase = Phase.DECODE),
    @EventConfig(listeners = CancelActionListener.class, phase = Phase.DECODE), }) })
public class UIEventForm extends UIForm {

  private static final Log LOG = ExoLogger.getExoLogger(UIEventForm.class);

  public static class AddParticipantActionListener extends EventListener<UIForm> {
    @Override
    public void execute(Event<UIForm> event) throws Exception {
      UIForm uiForm = event.getSource();
      // TODO cleanup
      // UIFormInputWithActions tabAttender = uiForm.getChildById(TAB_EVENTATTENDER);
      // String values = uiForm.getParticipantValues();
      // tabAttender.updateParticipants(values);
      // event.getRequestContext().addUIComponentToUpdateByAjax(tabAttender);

      // UIPopupContainer
      UIContainer formContainer = uiForm.getParent();
      // UIPopupAction
      UIContainer uiPopupAction = formContainer.getChildById("UICalendarChildPopup");
      // UICalendarChildPopupWindow
      UIPopupWindow popupWindow = uiPopupAction.getChildById("UICalendarChildPopupWindow");

      // UIInvitationContainer as component in UICalendarChildPopupWindow (UIpopupWindow)
      UIContainer uiInvitationContainer = (UIContainer) popupWindow.getUIComponent();
      // UIInvitationForm in UIInvitationContainer
      UIForm uiInvitationForm = uiInvitationContainer.getChildById("UIInvitationForm");
      // UIInvitationForm added each time new in UIEventForm$AddParticipantActionListener
      // add an action AddMoxtraParticipant to it to open UIMoxtraUserSelector

      // get Moxtra app from UIEmeetingTab
      // TODO make this work more transparent to action code
      UIEmeetingTab moxtraTab = formContainer.findComponentById(UIEmeetingTab.class.getSimpleName());
      moxtraTab.moxtra.mergeConfigs(uiInvitationForm.getComponentConfig(), UIInvitationForm.class);
    }
  }

  public static class SaveActionListener extends EventListener<UIForm> {
    @Override
    public void execute(Event<UIForm> event) throws Exception {
      UIForm uiForm = event.getSource();
      UIContainer formContainer = uiForm.getParent();
      UIEmeetingTab moxtraTab = formContainer.findComponentById(UIEmeetingTab.class.getSimpleName());
      String calType = moxtraTab.getCalendarType();
      try {
        if (moxtraTab.moxtra.hasMeet()) {
          if (String.valueOf(Utils.PRIVATE_TYPE).equals(calType)) {
            // XXX user (private) event: need invoke saveMeet from here
            moxtraTab.moxtra.saveMeet();
          }
        }
      } catch (MoxtraNotActivatedException e) {
        // TODO action can be invoked twice and second time the moxtra app will not be activated, why
        // event happens twice?
        if (LOG.isDebugEnabled()) {
          LOG.debug("Error saving private meet: " + e.getMessage());
        }
      } catch (MoxtraCalendarException e) {
        event.getRequestContext()
             .getUIApplication()
             .addMessage(new ApplicationMessage("UIEmeetingTab.error.ErrorSavingMeet",
                                                new Object[] { e.getMessage() }));
      }
    }
  }

  /**
   * Resources cleanup in Moxtra app on form cancellation - reset the app (clean a meet if exists).
   */
  public static class CancelActionListener extends EventListener<UIForm> {
    @Override
    public void execute(Event<UIForm> event) throws Exception {
      UIForm uiForm = event.getSource();
      UIContainer formContainer = uiForm.getParent();
      UIEmeetingTab moxtraTab = formContainer.findComponentById(UIEmeetingTab.class.getSimpleName());
      moxtraTab.moxtra.reset();
    }
  }

  /**
   * 
   */
  public UIEventForm() {
  }
}
