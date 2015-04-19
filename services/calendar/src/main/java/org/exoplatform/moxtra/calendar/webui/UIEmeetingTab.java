/**
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 **/
package org.exoplatform.moxtra.calendar.webui;

import static org.exoplatform.moxtra.Moxtra.getCalendar;

import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.calendar.service.CalendarSetting;
import org.exoplatform.commons.utils.LazyPageList;
import org.exoplatform.commons.utils.ListAccessImpl;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.calendar.MoxtraCalendarApplication;
import org.exoplatform.moxtra.calendar.MoxtraCalendarException;
import org.exoplatform.moxtra.calendar.webui.UIEmeetingTab.StartMeetActionListener;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.webui.MoxtraAction;
import org.exoplatform.moxtra.webui.component.UIActionCheckBoxInput;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.web.application.JavascriptManager;
import org.exoplatform.web.application.RequestContext;
import org.exoplatform.web.application.RequireJS;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.ComponentConfigs;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIGrid;
import org.exoplatform.webui.core.UIPopupContainer;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormInputWithActions;
import org.exoplatform.webui.form.UIFormSelectBoxWithGroups;
import org.exoplatform.webui.form.UIFormStringInput;
import org.exoplatform.webui.form.UIFormTextAreaInput;
import org.exoplatform.webui.form.input.UICheckBoxInput;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Event tab with Moxtra settings.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIEmeetingTab.java 00000 Mar 10, 2015 pnedonosko $
 */
@ComponentConfigs({ @ComponentConfig(
                                     template = "classpath:templates/calendar/webui/UIPopup/UIEmeetingTab.gtmpl",
                                     events = { @EventConfig(listeners = StartMeetActionListener.class,
                                                             phase = Phase.PROCESS)
                                     // @EventConfig(listeners = DisableMeetActionListener.class),
                                     // @EventConfig(listeners = EnableAutorecordingActionListener.class),
                                     // @EventConfig(listeners = DisableAutorecordingtActionListener.class),
                                     // @EventConfig(listeners = SelectMoxtraUsersActionListener.class),
                                     // @EventConfig(listeners = AddMoxtraUsersActionListener.class),
                                     // @EventConfig(listeners = DeleteActionListener.class, phase =
                                     // Phase.DECODE)
                                     }),
// @ComponentConfig(id = UIEmeetingTab.CONTAINER_MOXTRA_USER_SELECTOR, type = UIPopupContainer.class,
// events = {
// @EventConfig(listeners = AddMoxtraUsersActionListener.class),
// @EventConfig(listeners = CloseMoxtraUsersActionListener.class) })
})
public class UIEmeetingTab extends UIFormInputWithActions {

  public static final String    FIELD_ENABLE_MEET               = "enableMeet".intern();

  public static final String    FIELD_STARTED_MEET              = "startedMeet".intern();

  public static final String    FIELD_ENDED_MEET                = "endedMeet".intern();

  public static final String    FIELD_PLANED_MEET               = "planedMeet".intern();

  public static final String    FIELD_CANCELED_MEET             = "canceledMeet".intern();

  public static final String    FIELD_INVITE_MOXTRA_USER        = "inviteMoxtraUser".intern();

  public static final String    FIELD_MEET_LINK                 = "meetLink".intern();

  public static final String    FIELD_MEET_LINK_HINT            = "meetLinkHint".intern();

  public static final String    FIELD_MEET_AGENDA               = "meetAgenda".intern();

  public static final String    FIELD_MEET_SCHEDULE             = "meetSchedule".intern();

  public static final String    FIELD_MEET_SCHEDULE_START       = "meetScheduleStart".intern();

  public static final String    FIELD_MEET_SCHEDULE_END         = "meetScheduleEnd".intern();

  public static final String    FIELD_ENABLE_MEET_AUTORECORDING = "meetAutorecording".intern();

  public static final String    MESSAGE_MEET_CREATION_INFO      = "meetCreationInfo".intern();

  public static final String    MESSAGE_POWERED_BY              = "poweredBy".intern();

  public static final String    MESSAGE_START_MEET_HINT         = "startMeetHint".intern();

  public static final String    MESSAGE_JOIN_MEET_HINT          = "joinMeetHint".intern();

  public static final String    MESSAGE_STARTED_MEET_HINT       = "startedMeetHint".intern();

  public static final String    LIST_MEET_PARTICIPANTS          = "UIMoxtraMeetParticipantsList".intern();

  public static final String    ACTION_AUTH                     = "loginMoxtra".intern();

  public static final String    ACTION_AUTH_HINT                = "loginMoxtraHint".intern();

  public static final String    ACTION_START_MEET               = "StartMeet".intern();

  public static final String    ACTION_JOIN_MEET                = "JoinMeet".intern();

  public static final String    ACTION_SELECT_MOXTRA_USERS      = "SelectMoxtraUsers".intern();

  public static final String    ACTION_INVITE_EMAIL_USERS       = "InviteEmailUsers".intern();

  public static final String    CONTAINER_MOXTRA_USER_SELECTOR  = "UIMoxtraUserSelectorPopupContainer";

  public static final String    DATE_FORMAT_PATTERN             = "yyyy-MM-dd HH:mm:ss";

  protected static final String TAB_EVENTDETAIL                 = "eventDetail".intern();

  protected static final String FIELD_CALENDAR                  = "calendar".intern();

  protected static final String COLON                           = ":".intern();

  protected static final String COMMA                           = ",".intern();

  private static final Log      LOG                             = ExoLogger.getExoLogger(UIEmeetingTab.class);

  /**
   * Triggers this action when user clicks on popup's close button.
   */
  @Deprecated
  public static class CloseActionListener extends EventListener<MoxtraUserSelector> {

    public void execute(Event<MoxtraUserSelector> event) throws Exception {
      MoxtraUserSelector usersSelector = event.getSource();
      UIContainer formContainer = usersSelector.getComponent().getParent().getParent().getParent();

      // deactivate users selector popup
      UIPopupContainer popupContainer = formContainer.getChild(UIPopupContainer.class);
      if (popupContainer != null) {
        popupContainer.cancelPopupAction();
      }
    }
  }

  @Deprecated
  public static class EnableAutorecordingActionListener extends EventListener<UIEmeetingTab> {
    @Override
    public void execute(Event<UIEmeetingTab> event) throws Exception {
      UIEmeetingTab uiMoxtraTab = event.getSource();

      if (uiMoxtraTab.isMeetEnabled()) {
        MoxtraMeet meet = uiMoxtraTab.meet;
        meet.editAutoRecording(true);
      }

      // meet.editAutoRecording(newAutoRecording);
      UICheckBoxInput enableCheckbox = uiMoxtraTab.getChildById(FIELD_ENABLE_MEET_AUTORECORDING);
      enableCheckbox.setValue(true);

      event.getRequestContext().addUIComponentToUpdateByAjax(enableCheckbox);
    }
  }

  @Deprecated
  public static class DisableAutorecordingtActionListener extends EventListener<UIEmeetingTab> {
    @Override
    public void execute(Event<UIEmeetingTab> event) throws Exception {
      UIEmeetingTab uiMoxtraTab = event.getSource();

      if (uiMoxtraTab.isMeetEnabled()) {
        MoxtraMeet meet = uiMoxtraTab.meet;
        meet.editAutoRecording(false);
      }

      // meet.editAutoRecording(newAutoRecording);
      UICheckBoxInput enableCheckbox = uiMoxtraTab.getChildById(FIELD_ENABLE_MEET_AUTORECORDING);
      enableCheckbox.setValue(false);

      event.getRequestContext().addUIComponentToUpdateByAjax(enableCheckbox);
    }
  }

  @Deprecated
  public static class EnableMeetActionListener extends EventListener<UIEmeetingTab> {
    @Override
    public void execute(Event<UIEmeetingTab> event) throws Exception {
      UIEmeetingTab uiMoxtraTab = event.getSource();
      uiMoxtraTab.moxtra.enableMeet();
      UICheckBoxInput enableCheckbox = uiMoxtraTab.getChildById(FIELD_ENABLE_MEET);
      enableCheckbox.setValue(true);

      JavascriptManager jsManager = ((WebuiRequestContext) WebuiRequestContext.getCurrentInstance()).getJavascriptManager();
      RequireJS requireJS = jsManager.getRequireJS();
      requireJS.require("SHARED/jquery", "$");
      requireJS.addScripts("$('#" + ACTION_SELECT_MOXTRA_USERS + "').tooltip('show');");

      event.getRequestContext().addUIComponentToUpdateByAjax(uiMoxtraTab);
    }
  }

  @Deprecated
  public static class DisableMeetActionListener extends EventListener<UIEmeetingTab> {
    @Override
    public void execute(Event<UIEmeetingTab> event) throws Exception {
      UIEmeetingTab uiMoxtraTab = event.getSource();
      uiMoxtraTab.moxtra.disableMeet();
      UICheckBoxInput enableCheckbox = uiMoxtraTab.getChildById(FIELD_ENABLE_MEET);
      enableCheckbox.setValue(false);
      event.getRequestContext().addUIComponentToUpdateByAjax(uiMoxtraTab);
    }
  }

  @Deprecated
  public static class SelectMoxtraUsersActionListener extends EventListener<UIEmeetingTab> {
    @Override
    public void execute(Event<UIEmeetingTab> event) throws Exception {
      UIEmeetingTab moxtraTab = event.getSource();

      // init popup for adding Moxtra users
      // parent container is UIPopupContainer from Calendar webapp, we will add a UIPopupContainer from PLF UI
      UIContainer formContainer = moxtraTab.getParentForm().getParent();
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
      uiUserSelector.init(moxtraTab.moxtra);
      event.getRequestContext().addUIComponentToUpdateByAjax(formContainer);
    }
  }

  @Deprecated
  public static class AddMeetUsersActionListener extends EventListener<MoxtraUserSelector> {
    @Override
    public void execute(Event<MoxtraUserSelector> event) throws Exception {
      MoxtraUserSelector usersSelector = event.getSource();
      UIContainer formContainer = usersSelector.getComponent().getParent().getParent().getParent();
      UIEmeetingTab moxtraTab = formContainer.findFirstComponentOfType(UIEmeetingTab.class);

      MoxtraMeet meet = moxtraTab.meet;
      moxtraTab.setMeetUsers(meet.getUsers());

      event.getRequestContext().addUIComponentToUpdateByAjax(moxtraTab);

      // deactivate users selector popup
      UIPopupContainer popupContainer = formContainer.getChild(UIPopupContainer.class);
      if (popupContainer != null) {
        popupContainer.cancelPopupAction();
      }
    }
  }

  @Deprecated
  public static class DeleteActionListener extends EventListener<UIEmeetingTab> {
    @Override
    public void execute(Event<UIEmeetingTab> event) throws Exception {
      UIEmeetingTab moxtraTab = event.getSource();
      MoxtraMeet meet = moxtraTab.meet;

      String userName = event.getRequestContext().getRequestParameter(OBJECTID);
      for (MoxtraUser user : meet.getUsers()) {
        if (user.getName().equals(userName)) {
          if (meet.getHostUser().equals(user)) {
            event.getRequestContext()
                 .getUIApplication()
                 .addMessage(new ApplicationMessage("UIEmeetingTab.message.CannotRemoveMeetCreator", null));
          } else {
            meet.removeUser(user);
          }
          break;
        }
      }

      moxtraTab.setMeetUsers(meet.getUsers());
      event.getRequestContext().addUIComponentToUpdateByAjax(moxtraTab);
    }
  }

  public static class StartMeetActionListener extends EventListener<UIEmeetingTab> {
    @Override
    public void execute(Event<UIEmeetingTab> event) throws Exception {
      UIEmeetingTab moxtraTab = event.getSource();

      // TODO if not yet started meet, update meet in some period (let user to check the page and click start)
      // MoxtraMeet meet = moxtraTab.moxtra.getMeet();

      event.getRequestContext().addUIComponentToUpdateByAjax(moxtraTab);
    }
  }

  protected class MeetEnabler implements MoxtraAction<Event<UIActionCheckBoxInput>, Boolean> {

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean execute(Event<UIActionCheckBoxInput> event) {
      UIActionCheckBoxInput checkbox = event.getSource();
      JavascriptManager jsManager = ((WebuiRequestContext) WebuiRequestContext.getCurrentInstance()).getJavascriptManager();
      RequireJS requireJS = jsManager.getRequireJS();
      try {
        boolean res;
        if (checkbox.isChecked()) {
          if (moxtra.isAuthorized()) {
            user = moxtra.getUser();
            meet = moxtra.enableMeet();
            res = true;
          } else {
            // need authorize user
            requireJS.require("SHARED/jquery", "$");
            requireJS.addScripts("$('a.moxtraAuthLink').tooltip('show');");
            res = false; // unchecked
          }
        } else {
          meet = moxtra.disableMeet();
          res = false;
        }
        return res;
      } catch (MoxtraCalendarException e) {
        event.getRequestContext()
             .getUIApplication()
             .addMessage(new ApplicationMessage("UIEmeetingTab.message.ErrorEnablingMeet", null));
        return false;
      } catch (MoxtraException e) {
        event.getRequestContext()
             .getUIApplication()
             .addMessage(new ApplicationMessage("UIEmeetingTab.message.ErrorReadingUser", null));
        return false;
      } finally {
        event.getRequestContext().addUIComponentToUpdateByAjax(UIEmeetingTab.this);
      }
    }
  }

  protected class AutorecordingEnabled implements MoxtraAction<Event<UIActionCheckBoxInput>, Boolean> {

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean execute(Event<UIActionCheckBoxInput> event) {
      UIActionCheckBoxInput checkbox = event.getSource();
      try {
        boolean res;
        if (checkbox.isChecked()) {
          meet.editAutoRecording(true);
          res = true;
        } else {
          meet.editAutoRecording(false);
          res = false;
        }
        return res;
      } finally {
        event.getRequestContext().addUIComponentToUpdateByAjax(UIEmeetingTab.this);
      }
    }
  }

  protected final Map<String, List<ActionData>> moxtraActions = new HashMap<String, List<ActionData>>();

  protected MoxtraCalendarApplication           moxtra;

  /**
   * Current user cache.
   */
  protected MoxtraUser                          user;

  /**
   * Meet associated with the form.
   */
  protected MoxtraMeet                          meet;

  /**
   * Current user state regarding the existing meet. If <code>true</code> then this user a host user (creator
   * of the meet)
   */
  protected boolean                             isHostUser;

  public UIEmeetingTab() throws Exception {
    super();
  }

  public MoxtraCalendarApplication getMoxtra() {
    return moxtra;
  }

  public String getMeetLabel() throws Exception {
    UIForm eventForm = getParentForm();
    String label;
    if (isMeetSaved()) {
      if (meet.isStarted()) {
        label = eventForm.getLabel(FIELD_STARTED_MEET);
      } else if (meet.isEnded()) {
        label = eventForm.getLabel(FIELD_ENDED_MEET);
      } else if (meet.isScheduled()) {
        if (isHostUser()) {
          label = eventForm.getLabel(FIELD_ENABLE_MEET);
        } else {
          label = eventForm.getLabel(FIELD_PLANED_MEET);
        }
      } else {
        // else it can be only deleted (canceled)
        label = eventForm.getLabel(FIELD_CANCELED_MEET);
      }
    } else {
      // this only available to future owner
      label = eventForm.getLabel(FIELD_ENABLE_MEET);
    }
    return label;
  }

  public String getAuthLink() throws MoxtraCalendarException,
                             OAuthSystemException,
                             MoxtraConfigurationException {
    return moxtra.getAuthorizationLink();
  }

  public boolean isAuthorized() throws MoxtraCalendarException {
    return moxtra.isAuthorized();
  }

  public boolean isMeetEnabled() throws MoxtraCalendarException {
    return meet != null ? !meet.isDeleted() : false;
  }

  /**
   * Tells is it a newly created and not yet saved meet. Used in template to show new meet info.
   * 
   * @return boolean, <code>true</code> if it is a newly created meet
   * @throws MoxtraCalendarException
   */
  public boolean isMeetNew() throws MoxtraCalendarException {
    return isMeetEnabled() && meet.isNew();
  }

  public boolean isMeetSaved() throws MoxtraCalendarException {
    return isMeetEnabled() && !meet.isNew();
  }

  public boolean isMeetStarted() throws MoxtraCalendarException {
    return isMeetEnabled() && meet.isStarted();
  }

  public boolean isCanStartMeet() throws MoxtraCalendarException {
    if (isMeetEnabled()) {
      if (isHostUser() && !(meet.isNew() || meet.isEnded() || meet.wasDeleted() || meet.isExpired())) {
        return true; // host user can do anytime if not ended
      }
      return meet.canStart();
    }
    return false;
  }

  public boolean isHostUser() {
    return isHostUser;
  }

  public String getStartMeetLink() throws MoxtraCalendarException {
    if (isMeetEnabled()) {
      return meet.getStartMeetUrl();
    }
    return "javascript:void(0);";
  }

  // TODO cleanup
  // /**
  // * {@inheritDoc}
  // */
  // @Override
  // @Deprecated
  // public void setActionField(String fieldName, List<ActionData> actions) {
  // moxtraActions.put(fieldName, actions);
  // }

  @Deprecated
  // TODO NOT USED, but mentioned in the template
  public List<ActionData> getActionField(String fieldName) {
    return moxtraActions.get(fieldName);
  }

  public void initMoxtra(MoxtraCalendarApplication moxtra) throws Exception {
    this.moxtra = moxtra;

    // TODO show complete meet participants (in non editable grid)
    // UIGrid participantsList = addChild(UIGrid.class, null, LIST_MEET_PARTICIPANTS);
    // participantsList.getUIPageIterator().setId("MeetUsersIterator");
    // String[] participantAction;

    if (moxtra.hasMeet()) {
      user = moxtra.getUser();
      meet = moxtra.getMeet();
    } else {
      user = null;
      meet = null;
    }

    boolean meetEnabled, meetEnded, meetStarted, autoRecord;
    // List<MoxtraUser> participants;
    if (isMeetEnabled()) {
      meetEnabled = true;

      // we treat expired as ended here
      meetEnded = meet.isEnded() || meet.wasDeleted() || meet.isExpired();
      meetStarted = meet.isStarted();

      if (meet.isNew()) {
        isHostUser = true; // current user owner of a new meet
      } else if (meetStarted) {
        isHostUser = false; // when started even host cannot disable/modify it
      } else {
        try {
          isHostUser = user.equals(meet.getHostUser());
        } catch (MoxtraException e) {
          // if error reading it may be not authorized user, it can be started meet also.
          // TODO use of unique_id from Moxtra with eXo user id could help here: no need to get current user
          isHostUser = false;
        }
      }

      // participants = meet.getUsers();

      autoRecord = meet.isAutoRecording();

      if (!meet.isNew()) {
        // init UI for existing meet
        UIFormStringInput meetLink = new UIFormStringInput(FIELD_MEET_LINK,
                                                           FIELD_MEET_LINK,
                                                           meet.getStartMeetUrl());
        meetLink.setDisabled(true);
        addUIFormInput(meetLink);

        UIFormTextAreaInput meetAgenda = new UIFormTextAreaInput(FIELD_MEET_AGENDA,
                                                                 FIELD_MEET_AGENDA,
                                                                 meet.getAgenda());
        meetAgenda.setDisabled(true);
        addUIFormInput(meetAgenda);

        // user specific date format
        DateFormat dateFormat = userDateFormat();

        UIFormStringInput meetScheduleStart = new UIFormStringInput(FIELD_MEET_SCHEDULE_START,
                                                                    FIELD_MEET_SCHEDULE_START,
                                                                    dateFormat.format(meet.getStartTime()));
        meetScheduleStart.setDisabled(true);
        addUIFormInput(meetScheduleStart);

        UIFormStringInput meetScheduleEnd = new UIFormStringInput(FIELD_MEET_SCHEDULE_END,
                                                                  FIELD_MEET_SCHEDULE_END,
                                                                  dateFormat.format(meet.getEndTime()));
        meetScheduleEnd.setDisabled(true);
        addUIFormInput(meetScheduleEnd);
      }
    } else {
      meetEnabled = meetEnded = meetStarted = false;
      isHostUser = true; // we assume current user it's future owner
      // participants = new ArrayList<MoxtraUser>();
      autoRecord = false;
    }

    if (isHostUser) {
      // actions on participants:
      // removal from grid
      // participantAction = new String[] { "Delete" };
      // add action
      // List<ActionData> actions = new ArrayList<ActionData>();
      // ActionData addUser = new ActionData();
      // addUser.setActionListener(ACTION_SELECT_MOXTRA_USERS);
      // addUser.setActionName(ACTION_SELECT_MOXTRA_USERS);
      // addUser.setActionParameter(this.getId());
      // addUser.setActionType(ActionData.TYPE_ICON);
      // addUser.setCssIconClass("uiIconPlus uiIconLightGray");
      // actions.add(addUser);
      // ActionData emailUser = new ActionData();
      // emailUser.setActionListener(ACTION_INVITE_EMAIL_USERS);
      // emailUser.setActionName(ACTION_INVITE_EMAIL_USERS);
      // emailUser.setActionParameter(this.getId());
      // emailUser.setActionType(ActionData.TYPE_ICON);
      // emailUser.setCssIconClass("uiIconMail uiIconLightGray");
      // actions.add(emailUser);
      // this.setActionField(FIELD_INVITE_MOXTRA_USER, actions);
    } else {
      // participantAction = new String[] {};
    }

    // participantsList.configure("name", new String[] { "name", "email" }, participantAction);
    // setMeetUsers(participants);

    // init UI
    UIActionCheckBoxInput enableCheckbox = getParentForm().createUIComponent(UIActionCheckBoxInput.class,
                                                                             null,
                                                                             FIELD_ENABLE_MEET);
    enableCheckbox.initMoxtra(new MeetEnabler(), !isHostUser || meetEnded, meetEnabled);
    addUIFormInput(enableCheckbox);

    UIActionCheckBoxInput autorecCheckbox = getParentForm().createUIComponent(UIActionCheckBoxInput.class,
                                                                              null,
                                                                              FIELD_ENABLE_MEET_AUTORECORDING);
    autorecCheckbox.initMoxtra(new AutorecordingEnabled(),
                               !isHostUser || meetStarted || meetEnded,
                               autoRecord);
    addUIFormInput(autorecCheckbox);

    // show hints for better UX
    if (meetStarted) {
      // TODO in Javascript remember a window of open meet (if did it) and when clicked Start/Join again
      // switch the browser to that window instead of opening a new one
      JavascriptManager jsManager = ((WebuiRequestContext) WebuiRequestContext.getCurrentInstance()).getJavascriptManager();
      RequireJS requireJS = jsManager.getRequireJS();
      requireJS.require("SHARED/jquery", "$");
      requireJS.addScripts("$('span>a.meetStartAction').tooltip('show');");
    }
  }

  protected UIForm getParentForm() {
    return (UIForm) getParent();
  }

  @Deprecated
  protected void setMeetUsers(List<MoxtraUser> users) {
    LazyPageList<MoxtraUser> pageList = new LazyPageList<MoxtraUser>(new ListAccessImpl<MoxtraUser>(MoxtraUser.class,
                                                                                                    users),
                                                                     15);
    getChild(UIGrid.class).getUIPageIterator().setPageList(pageList);
  }

  protected DateFormat userDateFormat() throws MoxtraCalendarException, Exception {
    WebuiRequestContext context = RequestContext.getCurrentInstance();
    Locale locale = context.getParentAppRequestContext().getLocale();
    CalendarSetting setting = moxtra.getCalendarSetting();
    String dateFormatPattern = setting.getDateFormat() + " " + setting.getTimeFormat();
    DateFormat dateFormat = new SimpleDateFormat(dateFormatPattern, locale);
    dateFormat.setTimeZone(TimeZone.getTimeZone(setting.getTimeZone()));
    return dateFormat;
  }

  /**
   * Get current calendar id in UIEventForm.
   * 
   */
  protected String getCalendarId() {
    UIFormInputWithActions eventDetailTab = getParentForm().getChildById(TAB_EVENTDETAIL);
    String value = ((UIFormSelectBoxWithGroups) eventDetailTab.findComponentById(FIELD_CALENDAR)).getValue();
    if (value != null && value.length() > 0) {
      String[] values = value.split(COLON);
      if (values.length > 0) {
        return value.split(COLON)[1];
      }
    }
    return value;
  }

  /**
   * Get current calendar type in UIEventForm.
   * 
   * @return
   */
  protected String getCalendarType() {
    UIFormInputWithActions eventDetailTab = getParentForm().getChildById(TAB_EVENTDETAIL);
    String value = ((UIFormSelectBoxWithGroups) eventDetailTab.findComponentById(FIELD_CALENDAR)).getValue();
    if (value != null && value.length() > 0) {
      String[] values = value.split(COLON);
      if (values.length > 0) {
        return value.split(COLON)[0];
      }
    }
    return null;
  }
}
