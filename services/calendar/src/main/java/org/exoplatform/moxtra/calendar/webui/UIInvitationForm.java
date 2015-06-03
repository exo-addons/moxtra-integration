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

import org.exoplatform.moxtra.calendar.webui.UIInvitationForm.AddMoxtraParticipantActionListener;
import org.exoplatform.moxtra.calendar.webui.UIInvitationForm.AddMoxtraUsersActionListener;
import org.exoplatform.moxtra.calendar.webui.UIInvitationForm.CloseMoxtraUsersActionListener;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.ComponentConfigs;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPopupContainer;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormTextAreaInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This component used as a config with action listeners that should be added to original UIInvitationForm
 * from calendar webapp .<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIInvitationForm.java 00000 Apr 6, 2015 pnedonosko $
 * 
 */
@ComponentConfigs({
    @ComponentConfig(template = "classpath:templates/calendar/webui/UIPopup/UIInvitationForm.gtmpl",
                     events = { @EventConfig(listeners = AddMoxtraParticipantActionListener.class,
                                             phase = Phase.DECODE) }),
    @ComponentConfig(id = UIInvitationForm.CONTAINER_MOXTRA_USER_SELECTOR, type = UIPopupContainer.class,
                     events = {
                         @EventConfig(listeners = UIPopupWindow.CloseActionListener.class,
                                      name = "ClosePopup"),
                         @EventConfig(listeners = AddMoxtraUsersActionListener.class)/*,
                         @EventConfig(listeners = CloseMoxtraUsersActionListener.class)*/ }) })
public class UIInvitationForm extends UIForm {

  public static final String CONTAINER_MOXTRA_USER_SELECTOR = "UIMoxtraUserSelectorPopupContainer";

  public static final String FIELD_PARTICIPANT              = "participant".intern();
  
  public static final String NEW_LINE = "\r\n";

  /**
   * Save selected users in UIInvitationForm's text area with id "participant".
   */
  public static class AddMoxtraUsersActionListener extends EventListener<UIMoxtraUserSelector> {
    @Override
    public void execute(Event<UIMoxtraUserSelector> event) throws Exception {
      UIMoxtraUserSelector uiUserSelector = event.getSource();

      // UI parent
      UIPopupContainer popupContainer = uiUserSelector.getParent().getParent();
      popupContainer.cancelPopupAction();

      // UIEventForm container
      UIContainer formContainer = popupContainer.getParent();

      // UIEmeetingTab moxtraTab = formContainer.findFirstComponentOfType(UIEmeetingTab.class);
      // MoxtraMeet meet = moxtraTab.moxtra.getMeet();

      // update invitation form
      UIForm invitationForm = formContainer.findComponentById("UIInvitationForm");
      event.getRequestContext().addUIComponentToUpdateByAjax(invitationForm);

      // add Moxtra users email to participants in UIInvitationForm
      // first take existing to a set
      UIFormTextAreaInput participantsText = invitationForm.getUIFormTextAreaInput(FIELD_PARTICIPANT);
      Set<String> parts = new LinkedHashSet<String>();
      String partsString = participantsText.getValue();
      if (partsString != null) {
        for (String user : participantsText.getValue().split(NEW_LINE)) {
          parts.add(user);
        }
      }
      // add Moxtra users (if not already in the set)
      for (MoxtraUser user : uiUserSelector.getSelectedUsers()) {
        parts.add(user.getEmail().trim());
      }
      // sort and create participants text
      List<String> partsList = new ArrayList<String>(parts);
      Collections.sort(partsList);
      StringBuilder participants = new StringBuilder();
      for (String part : partsList) {
        if (part.length() > 0) {
          if (participants.length() > 0) {
            participants.append(NEW_LINE);
          }
          participants.append(part);
        }
      }
      participantsText.setValue(participants.toString());
    }
  }

  /**
   * Hide {@link UIMoxtraUserSelector} form.
   */
  public static class CloseMoxtraUsersActionListener extends EventListener<UIMoxtraUserSelector> {
    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Event<UIMoxtraUserSelector> event) throws Exception {
      UIMoxtraUserSelector uiMoxtraSelector = event.getSource();
      UIPopupContainer popupContainer = uiMoxtraSelector.getParent();
      popupContainer.cancelPopupAction();
      //popupContainer.deActivate();
      //event.getRequestContext().addUIComponentToUpdateByAjax(popupContainer);
    }
  }

  /**
   * Show {@link UIMoxtraUserSelector} form.
   */
  public static class AddMoxtraParticipantActionListener extends EventListener<UIForm> {
    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Event<UIForm> event) throws Exception {
      UIForm invitationForm = event.getSource();
      // init popup for adding Moxtra users
      // UiInvitationForm lies in a container placed in popup window, it itself in the popup action container
      // and it finally in the UIPopupContainer from Calendar webapp where UIEventForm lies,
      // we will add a UIPopupContainer from PLF UI to that parent
      UIContainer formContainer = invitationForm.getParent().getParent().getParent().getParent();
      UIPopupContainer popupContainer = formContainer.getChild(UIPopupContainer.class);
      if (popupContainer == null) {
        popupContainer = formContainer.addChild(UIPopupContainer.class,
                                                CONTAINER_MOXTRA_USER_SELECTOR,
                                                CONTAINER_MOXTRA_USER_SELECTOR);
      } else {
        popupContainer.deActivate();
      }

      UIMoxtraUserSelector uiUserSelector = popupContainer.activate(UIMoxtraUserSelector.class,
                                                                    null,
                                                                    740,
                                                                    400);

      // get Moxtra app from UIEmeetingTab
      UIEmeetingTab moxtraTab = formContainer.findComponentById(UIEmeetingTab.class.getSimpleName());
      uiUserSelector.init(moxtraTab.moxtra);
      event.getRequestContext().addUIComponentToUpdateByAjax(formContainer);
    }
  }

  /**
   * 
   */
  public UIInvitationForm() {
  }
}
