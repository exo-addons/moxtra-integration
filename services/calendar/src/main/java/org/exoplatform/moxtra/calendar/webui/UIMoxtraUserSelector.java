/**
 * Copyright (C) 2009 eXo Platform SAS.
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
import org.exoplatform.commons.utils.LazyPageList;
import org.exoplatform.commons.utils.ListAccessImpl;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.calendar.MoxtraCalendarApplication;
import org.exoplatform.moxtra.calendar.MoxtraCalendarException;
import org.exoplatform.moxtra.calendar.webui.UIMoxtraUserSelector.AddMoxtraUsersActionListener;
import org.exoplatform.moxtra.calendar.webui.UIMoxtraUserSelector.CloseMoxtraUsersActionListener;
import org.exoplatform.moxtra.calendar.webui.UIMoxtraUserSelector.SearchActionListener;
import org.exoplatform.moxtra.calendar.webui.UIMoxtraUserSelector.ShowPageActionListener;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIPageIterator;
import org.exoplatform.webui.core.UIPopupComponent;
import org.exoplatform.webui.core.UIPopupContainer;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.core.model.SelectItemOption;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormSelectBox;
import org.exoplatform.webui.form.UIFormStringInput;
import org.exoplatform.webui.form.input.UICheckBoxInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by The eXo Platform SAS
 */
@ComponentConfig(lifecycle = UIFormLifecycle.class,
                 template = "classpath:templates/calendar/webui/UIPopup/UIMoxtraUserSelector.gtmpl",
                 events = { @EventConfig(listeners = AddMoxtraUsersActionListener.class),
                     @EventConfig(listeners = CloseMoxtraUsersActionListener.class),
                     @EventConfig(listeners = SearchActionListener.class, phase = Phase.DECODE),
                     @EventConfig(listeners = ShowPageActionListener.class, phase = Phase.DECODE) })
@Serialized
public class UIMoxtraUserSelector extends UIForm implements MoxtraUserSelector, UIPopupComponent {

  public static class AddMoxtraUsersActionListener extends EventListener<MoxtraUserSelector> {
    public void execute(Event<MoxtraUserSelector> event) throws Exception {
      MoxtraUserSelector selector = event.getSource();
      UIMoxtraUserSelector selectorForm = selector.getComponent();

      selectorForm.addUsers();

      // get item from selected item map
      Set<String> selectedNames = selectorForm.usersIterator.getSelectedItems();
      if (selectedNames.size() == 0) {
        UIApplication uiApp = selectorForm.getAncestorOfType(UIApplication.class);
        uiApp.addMessage(new ApplicationMessage("UIUserSelector.msg.user-required", null));
        return;
      }

      selectorForm.getParent().getParent().broadcast(event, event.getExecutionPhase());
    }
  }

  public static class CloseMoxtraUsersActionListener extends EventListener<MoxtraUserSelector> {
    public void execute(Event<MoxtraUserSelector> event) throws Exception {
      MoxtraUserSelector selector = event.getSource();
      UIMoxtraUserSelector selectorForm = selector.getComponent();
      UIPopupContainer popupContainer = selectorForm.getParent().getParent();
      popupContainer.cancelPopupAction();
      // selectorForm.getParent().broadcast(event, event.getExecutionPhase());
    }
  }

  public static class ShowPageActionListener extends EventListener<MoxtraUserSelector> {
    public void execute(Event<MoxtraUserSelector> event) throws Exception {
      MoxtraUserSelector selector = event.getSource();
      UIMoxtraUserSelector selectorForm = selector.getComponent();
      selectorForm.addUsers();
      int page = Integer.parseInt(event.getRequestContext().getRequestParameter(OBJECTID));
      selectorForm.updateCurrentPage(page);
      event.getRequestContext().addUIComponentToUpdateByAjax(selectorForm);
    }
  }

  @SuppressWarnings("unchecked")
  public static class SearchActionListener extends EventListener<UIMoxtraUserSelector> {
    public void execute(Event<UIMoxtraUserSelector> event) throws Exception {
      UIMoxtraUserSelector uiForm = event.getSource();
      String keyword = uiForm.getUIStringInput(FIELD_KEYWORD).getValue();
      String field = uiForm.getUIFormSelectBox(FIELD_FILTER).getValue();
      uiForm.filter(keyword, field);
      if (field == null || field.trim().length() == 0)
        return;

      event.getRequestContext().addUIComponentToUpdateByAjax(uiForm);
    }
  }

  private static final Log LOG           = ExoLogger.getExoLogger(UIMoxtraUserSelector.class);

  private List<MoxtraUser> selectedUsers = new ArrayList<MoxtraUser>();

  private UIPageIterator   usersIterator;

  private MoxtraMeet       meet;

  /**
   * Cached user contacts as users available for selection.
   */
  private List<MoxtraUser> selectorUsers;

  public UIMoxtraUserSelector() throws Exception {
    addUIFormInput(new UIFormStringInput(FIELD_KEYWORD, FIELD_KEYWORD, null));
    addUIFormInput(new UIFormSelectBox(FIELD_FILTER, FIELD_FILTER, getFilters()));
    usersIterator = new UIPageIterator();
    usersIterator.setId("UISelectMoxtraUserPage");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activate() {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deActivate() {
    // nothing
  }

  /**
   * Selected users.
   * 
   * @return
   */
  public List<MoxtraUser> getSelectedUsers() {
    return selectedUsers;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T extends UIComponent> T getComponent() {
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public List<MoxtraUser> getPageUsers() {
    List<MoxtraUser> pageUsers;
    try {
      pageUsers = (List<MoxtraUser>) usersIterator.getCurrentPageData();
    } catch (Exception e) {
      LOG.error("Error getting current page data in users iterator", e);
      return Collections.emptyList();
    }

    // init current page checkboxes
    for (MoxtraUser user : pageUsers) {
      UICheckBoxInput uiUserCheckBoxInput = getUICheckBoxInput(user.getName());
      if (uiUserCheckBoxInput == null) {
        uiUserCheckBoxInput = new UICheckBoxInput(user.getName(), user.getName(), false);
        addUIFormInput(uiUserCheckBoxInput);
      }
      uiUserCheckBoxInput.setChecked(usersIterator.isSelectedItem(user.getName()));
    }

    return pageUsers;
  }

  public String[] getActions() {
    return new String[] { "AddMoxtraUsers", "CloseMoxtraUsers" };
  }

  public String getLabel(String id) {
    try {
      return super.getLabel(id);
    } catch (Exception e) {
      return id;
    }
  }

  public long getAvailablePage() {
    return usersIterator.getAvailablePage();
  }

  public long getCurrentPage() {
    return usersIterator.getCurrentPage();
  }

  protected void updateCurrentPage(int page) throws Exception {
    usersIterator.setCurrentPage(page);
  }

  protected void filter(String keyword, String field) {
    List<MoxtraUser> res = new ArrayList<MoxtraUser>();
    // TODO filter all users list
    keyword = keyword.toUpperCase().toLowerCase();
    if (field.equals("name")) {
      for (MoxtraUser user : selectorUsers) {
        if (user.getName().toUpperCase().toLowerCase().indexOf(keyword) >= 0) {
          res.add(user);
        }
      }
    }
    setUsers(res);
  }

  public void init(MoxtraCalendarApplication moxtra) throws MoxtraCalendarException,
                                                    MoxtraException,
                                                    MoxtraConfigurationException {
    this.meet = moxtra.getMeet();
    // List<MoxtraUser> availableUsers = moxtra.getUserContacts();
    // currently we show all available users
    // TODO filter users (as below), but if empty result then show an info to user that all his contacts
    // already invited
    // List<MoxtraUser> meetUsers = meet.getUsers();
    // List<MoxtraUser> selectorUsers = new ArrayList<MoxtraUser>();
    // for (MoxtraUser user : availableUsers) {
    // if (meetUsers.contains(user)) {
    // continue;
    // }
    // selectorUsers.add(user);
    // }
    // this.selectorUsers = selectorUsers;
    this.selectorUsers = moxtra.getUserContacts();
    setUsers(selectorUsers);
    this.selectedUsers.clear();
  }

  /**
   * Set users list in UI grid iterator.
   */
  protected void setUsers(List<MoxtraUser> users) {
    LazyPageList<MoxtraUser> pageList = new LazyPageList<MoxtraUser>(new ListAccessImpl<MoxtraUser>(MoxtraUser.class,
                                                                                                    users),
                                                                     15);
    this.usersIterator.setPageList(pageList);
  }

  protected List<SelectItemOption<String>> getFilters() throws Exception {
    List<SelectItemOption<String>> options = new ArrayList<SelectItemOption<String>>();
    options.add(new SelectItemOption<String>(USER_NAME, USER_NAME));
    // options.add(new SelectItemOption<String>(LAST_NAME, LAST_NAME));
    // options.add(new SelectItemOption<String>(FIRST_NAME, FIRST_NAME));
    options.add(new SelectItemOption<String>(EMAIL, EMAIL));
    return options;
  }

  /**
   * Add meet users from the iterator according their checkbox checked statuses in the form.
   * 
   */
  protected void addUsers() {
    for (MoxtraUser user : selectorUsers) {
      UICheckBoxInput input = this.getUICheckBoxInput(user.getName());
      if (input != null) {
        boolean checked = input.isChecked();
        this.usersIterator.setSelectedItem(user.getName(), checked);
        if (checked) {
          meet.addUser(user);
          selectedUsers.add(user);
        }
      }
    }
  }
}
