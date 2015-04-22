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
package org.exoplatform.moxtra.calendar;

import org.apache.commons.lang.reflect.FieldUtils;
import org.apache.commons.lang.reflect.MethodUtils;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.calendar.service.CalendarEvent;
import org.exoplatform.calendar.service.CalendarSetting;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.calendar.webui.UICalendarView;
import org.exoplatform.moxtra.calendar.webui.UIEmeetingTab;
import org.exoplatform.moxtra.calendar.webui.UIEventForm;
import org.exoplatform.moxtra.client.MoxtraAuthenticationException;
import org.exoplatform.moxtra.client.MoxtraConfigurationException;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.moxtra.webui.MoxtraApplication;
import org.exoplatform.moxtra.webui.MoxtraNotActivatedException;
import org.exoplatform.moxtra.webui.MoxtraNotInitializedException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.application.WebuiApplication;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.Component;
import org.exoplatform.webui.config.Event;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormInputWithActions;
import org.exoplatform.webui.form.UIFormStringInput;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application a bridge between WebUI {@link UIApplication} associated with calendar event creation or update
 * and Moxtra service. This app instance exists together with the UI app (portlet) and has the same life span.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraCalendarApplication.java 00000 Mar 11, 2015 pnedonosko $
 * 
 */
public class MoxtraCalendarApplication implements MoxtraApplication {

  public static final String                             QUICK_ADD_EVENT_POPUP_ID = "UIQuickAddEventPopupWindow".intern();

  public static final String                             QUICK_ADD_TASK_POPUP_ID  = "UIQuickAddTaskPopupWindow".intern();

  public static final String                             QUICK_ADD_EVENT_FORM_ID  = "UIQuickAddEvent".intern();

  public static final String                             QUICK_ADD_TASK_FORM_ID   = "UIQuickAddTask".intern();

  public static final Log                                LOG                      = ExoLogger.getLogger(MoxtraCalendarApplication.class);

  protected static final String                          COLON                    = ":".intern();

  /**
   * Associated WebUI app.
   */
  protected final ThreadLocal<UIApplication>             uiApp                    = new ThreadLocal<UIApplication>();

  /**
   * Associated form.
   */
  protected final ThreadLocal<UIForm>                    requestForm              = new ThreadLocal<UIForm>();

  /**
   * Associated UICalendarViewContainer. Main purpose is caching to reduce UI children traversing in
   * activation
   * and completion (save or delete).
   */
  protected final ThreadLocal<UIContainer>               requestViewContainer     = new ThreadLocal<UIContainer>();

  /**
   * Meets associated with components in the app.<br>
   * Components (forms) are stateful in general (like event form or calendar views). Additionally the same
   * instance can be used for different events. As result this map should be intialized in each request and
   * reset on modification operation completion (meet save or delete). Meets map cannot be used itself to
   * activate the app by getting a meet by current form - a meet should be found in the form or any other part
   * of the app and saved in this map to let later read it by other components in the request. <br>
   * This map will not be required if it will be possible to save meets in forms directly (that isn't possible
   * in Java if form class haven't such support, but in Groovy or via byte-code tools it could be doable).
   */
  // TODO Need weak ref? FYI This app itself lies in WeakHashMap in its activator (see
  // MoxtraApplicationService)
  // TODO Keep meet in the form
  protected final Map<UIComponent, MoxtraMeet>           meets                    = new ConcurrentHashMap<UIComponent, MoxtraMeet>();

  /**
   * EventId-to-meet cache to reduce number of calls to Moxtra API during activation.
   */
  @Deprecated
  protected final Map<String, SoftReference<MoxtraMeet>> eventMeets               = new ConcurrentHashMap<String, SoftReference<MoxtraMeet>>();

  protected final Set<Component>                         mergedConfigs            = new HashSet<Component>();

  /**
   * Associated {@link MoxtraCalendarService}.
   */
  protected MoxtraCalendarService                        moxtra;

  /**
   * Empty constructor mandatory!
   * 
   */
  public MoxtraCalendarApplication() {
  }

  public void init() {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    this.moxtra = (MoxtraCalendarService) container.getComponentInstanceOfType(MoxtraCalendarService.class);
  }

  public boolean isAuthorized() throws MoxtraNotInitializedException {
    return moxtra().isAuthorized();
  }

  public String getAuthorizationLink() throws MoxtraNotInitializedException,
                                      OAuthSystemException,
                                      MoxtraConfigurationException {
    return moxtra().getOAuth2Link();
  }

  /**
   * Associate with WebUI application and patch the UI app if required.
   * 
   * @param uiApp {@link UIApplication}
   */
  @Deprecated
  public void activateForm(UIForm form) {
    if (isQuickAddForm(form)) {
      initQuickAddForm(form);
    } else {
      initEventForm(form); // not used
    }
  }

  /**
   * Associate with WebUI application and patch the UI app if required.
   * 
   * @param uiApp {@link UIApplication}
   */
  public void activate(UIApplication uiApp) {
    this.uiApp.set(uiApp); // for information mainly

    UIContainer calContainer = findComponent(uiApp, "UICalendarWorkingContainer");
    if (calContainer != null) {
      // *********** UIQuickAddEvent ***********
      // XXX We need activate quick-add form here also (together with activateForm() in template),
      // because we need a proper state on decode phase of a request for event form (and activateForm() will
      // do only in rendering phase, via UICalendarWorkingContainer.active() method)
      // List<UIPopupWindow> uiQuickAddPopups = new ArrayList<UIPopupWindow>();
      // TODO UIQuickAddEvent doesn't have Moxtra checkbox temporarily
      // UIPopupWindow uiQuickAddEventPopup = calContainer.getChildById(QUICK_ADD_EVENT_POPUP_ID);
      // if (uiQuickAddEventPopup != null) {
      // uiQuickAddPopups.add(uiQuickAddEventPopup);
      // }
      // UIPopupWindow uiQuickAddTaskPopup = calContainer.getChildById(QUICK_ADD_TASK_POPUP_ID);
      // if (uiQuickAddTaskPopup != null) {
      // uiQuickAddPopups.add(uiQuickAddTaskPopup);
      // }
      // for (UIPopupWindow uiQuickAddPopup : uiQuickAddPopups) {
      // UIComponent popupComponent = uiQuickAddPopup.getUIComponent();
      // if (popupComponent instanceof UIForm) {
      // initQuickAddForm((UIForm) popupComponent);
      // }

      // TODO this stuff DOESN'T WORK via MoxtraLifecycle - as the popup will be added in rendering phase of
      // the calendar portlet in UICalendarWorkingContainer.active() method.
      // if (uiQuickAddPopup instanceof org.exoplatform.moxtra.calendar.webui.UIPopupWindow) {
      // if (popupComponent != null && popupComponent instanceof UIForm) {
      // try {
      // initQuickAddForm((UIForm) popupComponent);
      // } catch (Exception e) {
      // LOG.error("Error reactivating existing popup window for quick-add form in Calendar app", e);
      // }
      // }
      // } else {
      // try {
      // org.exoplatform.moxtra.calendar.webui.UIPopupWindow newPopupWindow =
      // calContainer.addChild(org.exoplatform.moxtra.calendar.webui.UIPopupWindow.class,
      // null,
      // QUICK_ADD_EVENT_POPUP_ID);
      // if (LOG.isDebugEnabled()) {
      // LOG.debug(">> activate quick-add: " + newPopupWindow);
      // }
      // newPopupWindow.initMoxtra(this, true);
      // newPopupWindow.setId(uiQuickAddPopup.getId());
      // newPopupWindow.setRendered(uiQuickAddPopup.isRendered());
      // calContainer.removeChildById(QUICK_ADD_EVENT_POPUP_ID);
      // calContainer.addChild(newPopupWindow);
      // } catch (Exception e) {
      // LOG.error("Error creating new popup window for quick-add form in Calendar app", e);
      // }
      // }
      // }

      // ********** UICalendarViewContainer with calendar views ***********
      UIContainer viewContainer = findComponent(uiApp, "UICalendarViewContainer");
      if (viewContainer != null) {
        this.requestViewContainer.set(viewContainer);
        UIComponent renderedComp;
        try {
          renderedComp = (UIComponent) MethodUtils.invokeMethod(viewContainer, "getRenderedChild", null);
        } catch (Exception e) {
          // ignore error, try get from the container directly
          // search for classes implementing CalendarView
          renderedComp = findRenderedChildByInterface(viewContainer, "CalendarView");
        }
        if (renderedComp != null) {
          if (renderedComp instanceof UIForm) {
            initViewForm((UIForm) renderedComp);
          } else {
            // but for UIListView it lies in a UIListContainer
            if (renderedComp instanceof UIContainer) {
              // there are two comps in list container: UIListView and UIPreview, we use list view in the app
              UIForm listForm = findComponent((UIContainer) renderedComp, "UIListView");
              if (listForm != null && listForm.isRendered()) {
                initViewForm(listForm);
              }
            }
          }
        }
      }
    }

    // ********* UIEventForm ***********
    UIContainer uiPopupAction = findComponent(uiApp, "UIPopupAction");
    if (uiPopupAction != null) {
      UIPopupWindow uiPopupWindow = uiPopupAction.getChild(UIPopupWindow.class);
      if (uiPopupWindow != null) {
        UIContainer uiPopupContainer = (UIContainer) uiPopupWindow.getUIComponent();
        // ours UIPopupWindow it is a first marker that our app activated: this type should be added only
        // here!
        if (uiPopupWindow instanceof org.exoplatform.moxtra.calendar.webui.UIPopupWindow) {
          if (uiPopupContainer != null) {
            initEventFormContainer(uiPopupContainer);
          }
        } else {
          try {
            if (uiPopupContainer != null) {
              initEventFormContainer(uiPopupContainer);
            } else {
              org.exoplatform.moxtra.calendar.webui.UIPopupWindow newPopupWindow = uiPopupAction.createUIComponent(org.exoplatform.moxtra.calendar.webui.UIPopupWindow.class,
                                                                                                                   null,
                                                                                                                   null);
              if (LOG.isDebugEnabled()) {
                LOG.debug(">> activate: " + newPopupWindow);
              }
              newPopupWindow.initMoxtra(this);
              newPopupWindow.setId(uiPopupWindow.getId());
              newPopupWindow.setRendered(uiPopupWindow.isRendered());
              uiPopupAction.removeChild(UIPopupWindow.class);
              uiPopupAction.addChild(newPopupWindow);
            }
          } catch (Exception e) {
            LOG.error("Error creating new popup window in Calendar app", e);
          }
        }
      }
    }
  }

  /**
   * Reset associated WebUI app in the context request (current thread). Moxtra app cannot be used after the
   * resetting without reactivation.
   */
  public void reset() {
    UIComponent form = this.requestForm.get();
    if (form != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("<< reset: " + form);
      }
      this.requestForm.remove();
      this.meets.remove(form);
    }
    this.requestViewContainer.remove();
  }

  public void initEventFormContainer(UIContainer formContainer) {
    UIForm form = findComponent(formContainer, "UIEventForm");
    if (form != null) {
      initEventForm(form);
    }
  }

  protected void initEventForm(UIForm form) {
    if (form.getId().equals("UIEventForm")) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(">>> initEventForm: " + form);
      }
      try {
        // UIEmeetingTab it is a second marker that our app activated
        if (form.getChildById(UIEmeetingTab.class.getSimpleName()) == null) {
          // add E-Meeting tab
          UIEmeetingTab moxtraTab = form.createUIComponent(UIEmeetingTab.class, null, null);

          // init once a tab created, not each request as this form (and the tab) isn't reusable as quick-add
          // forms - this form should keep the meet (it is steteful) until it will be saved/deleted,
          // init context here - just after the creation, do all other after this!
          initContext(form);
          initEventMeet(form);

          form.addChild(moxtraTab);
          moxtraTab.initMoxtra(this); // init after adding to the event form!

          // add an action to UIEventForm to init UIInvitationForm subform: add an icon "Pick Moxtra User"
          mergeConfigs(form.getComponentConfig(), UIEventForm.class);
        } else {
          // if form already initialized, just init the service context (for later save, delete ops)
          initContext(form);
        }
      } catch (Exception e) {
        LOG.error("Error creating Moxtra tab in Calendar event form " + form, e);
      }
    }
  }

  protected void initViewForm(UIForm form) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(">> initViewForm: " + form);
    }

    try {
      // XXX obtain CalendarEvent in nasty way... we have no other way
      String eventId;
      CalendarEvent event;
      String objId = WebuiRequestContext.getCurrentInstance().getRequestParameter(UIComponent.OBJECTID);
      if (objId != null && objId.startsWith("Event")) {
        eventId = objId;
      } else {
        // it will be set for UIMonthView request
        eventId = WebuiRequestContext.getCurrentInstance().getRequestParameter(UICalendarView.EVENTID);
        if (eventId == null) {
          // this method field will be set by UIListView, UIDayView and UIWeekView
          eventId = (String) MethodUtils.invokeMethod(form, "getLastUpdatedEventId", null);
          if (eventId == null) {
            // XXX this will be correct only in case of Deletion Confirmation popup submit, otherwise this
            // field persists in the form even in next requests
            eventId = (String) FieldUtils.readField(form, "singleDeletedEventId", true);
          }
        }
      }
      if (eventId != null) {
        event = moxtra.getEvent(eventId);
      } else {
        // TODO this seems never works
        event = (CalendarEvent) MethodUtils.invokeMethod(form, "getcurrentOccurrence", null);
      }
      if (event != null) {
        // form of existing event
        MoxtraMeet meet = moxtra().getMeet(event);
        if (meet != null) {
          // we have a meet enabled in this event: use editor of it
          meet = meet.editor();
          // this.requestMeet.set(meet);
          this.requestForm.set(form);
          this.meets.put(form, meet);
          // this.userName = userName;
          moxtra().initContext(this);
          // add ext events
          mergeConfigs(form.getComponentConfig(), UICalendarView.class);
        } // meet wasn't enabled for the event
      } // view without events selected
    } catch (Exception e) {
      LOG.error("Error initializing Moxtra meet in Calendar view " + form, e);
    }
  }

  /**
   * Init meet as for UIEventForm.
   * 
   * @throws MoxtraCalendarException
   */
  protected void initEventMeet(UIForm form) throws MoxtraCalendarException {
    MoxtraMeet meet = findFormMeet(form);
    if (meet != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(">>>> initEventMeet for " + form + " " + meet + " (" + meet.getSessionKey() + ") "
            + meet.getName());
      }
      this.meets.put(form, meet);
    }
  }

  /**
   * Init meet for quick-add form requests. This also will set request meet (thread-local) for later use in
   * UIEventForm.
   */
  protected void initQuickAddMeet() {
    // FYI quick-add form for new meet only, no meet
    // MoxtraMeet prevMeet = this.meets.remove(form);
    // if (prevMeet != null) {
    // save for later use in UIEmeetingTab in this request
    // this.requestMeet.set(prevMeet);
    // }
  }

  /**
   * Initialize this app if given form is quick-add form UIQuickAddEvent.
   * 
   * @param form {@link UIForm}
   */
  @Deprecated
  // NOT USED
  protected void initQuickAddForm(UIForm form) {
    if (isQuickAddForm(form)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(">> initQuickAddForm: " + form);
      }

      try {
        // TODO
        // init each request as quick-add forms reusable in calendar portlet, they are always on the page and
        // can be used to create new events, thus should be clean for new event but know its meet for save
        // from it.
        // initContext(form);
        // initQuickAddMeet();

        // add Moxtra Meet checkbox if not found (also requires support in template)
        // if (form.getChild(UIEnableMoxtraCheckBoxInput.class) == null) {
        // UIEnableMoxtraCheckBoxInput checkbox = form.createUIComponent(UIEnableMoxtraCheckBoxInput.class,
        // null,
        // null);
        // checkbox.initMoxtra(this, false); // not disabled
        // form.addUIFormInput(checkbox);

        // replace form config with extended set of action listeners
        // TODO cleanup
        // WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
        // WebuiApplication webuiApp = (WebuiApplication) context.getApplication();
        // Component origConfig = form.getComponentConfig();
        // Component extConfig =
        // webuiApp.getConfigurationManager().getComponentConfig(UIQuickAddEvent.class,
        // null);
        // mergeConfigs(form.getComponentConfig(), UIQuickAddEvent.class);
        // TODO
        // form.setComponentConfig(form.getId(), new ExtendedComponent(form.getClass(), origConfig,
        // extConfig));
        // }
      } catch (Exception e) {
        LOG.error("Error initializing quick-add form in Calendar " + form, e);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void deactivate(UIApplication uiApp) {
    this.uiApp.remove();
    this.requestForm.remove();

    // XXX we need be stateful while a form is open :(
    // app (request meet) will be reset in following cases:
    // * on meet save (from MoxtraCalendarService)
    // * on UIEventForm cancellation (by ext action)
    // this.reset();

    // TODO cleanup
    // UIContainer calPopup = findComponent(uiApp, "UIPopupAction");
    // UIPopupWindow calPopupWindow = calPopup.getChild(UIPopupWindow.class);
    // if (calPopupWindow != null) {
    // if (calPopupWindow instanceof org.exoplatform.moxtra.calendar.webui.UIPopupWindow) {
    // if (LOG.isDebugEnabled()) {
    // LOG.debug(">> deactivate: " + calPopupWindow);
    // }
    // UIContainer calPopupContainer = (UIContainer) calPopupWindow.getUIComponent();
    // if (calPopupContainer != null) {
    // UIForm eventForm = findComponent(calPopupContainer, "UIEventForm");
    // if (eventForm != null) {
    // if (eventForm.removeChild(UIEmeetingTab.class) != null) {
    // if (LOG.isDebugEnabled()) {
    // LOG.debug(">>> dactivateForm: " + eventForm);
    // }
    // try {
    // disableMeet();
    // } catch (MoxtraCalendarException e) {
    // LOG.error("Error deactivating form " + eventForm + ". " + e.getMessage());
    // }
    // }
    // }
    // }
    // }
    // }
  }

  public MoxtraMeet enableMeet() throws MoxtraNotActivatedException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(">> enableMeet: " + uiApp.get());
    }
    MoxtraCalendarService moxtra = moxtra();
    MoxtraMeet meet = meet();
    if (meet == null) {
      meet = moxtra.newMeet();
      // this.requestMeet.set(meet);
      this.meets.put(form(), meet);
      if (LOG.isDebugEnabled()) {
        LOG.debug(">>> enableMeet: " + uiApp.get() + " new meet " + meet);
      }
    } else if (meet.isDeleted()) {
      // was marked as deleted, unmark it
      meet.undelete();
      if (LOG.isDebugEnabled()) {
        LOG.debug(">>> enableMeet: " + uiApp.get() + " undeleted " + meet + " (" + meet.getSessionKey()
            + ") " + meet.getName());
      }
    } else {
      // else, already enabled
      if (LOG.isDebugEnabled()) {
        LOG.debug(">>> enableMeet: " + uiApp.get() + " already enabled " + meet + " (" + meet.getSessionKey()
            + ") " + meet.getName());
      }
    }
    return meet;
  }

  public MoxtraMeet disableMeet() throws MoxtraNotActivatedException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(">> disableMeet: " + uiApp.get());
    }
    MoxtraMeet meet = meet();
    if (meet != null && !meet.isNew()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(">>> disableMeet: " + form() + " delete " + meet);
      }
      meet.delete();
      return meet;
    } else {
      if (LOG.isDebugEnabled()) {
        LOG.debug(">>> disableMeet: " + form() + " clean " + meet);
      }
      // this.requestMeet.remove();
      this.meets.remove(form());
      return null;
    }
  }

  /**
   * Return Moxtra meet associated with this app. Returned meet may be already marked as deleted in this app,
   * refer to {@link MoxtraMeet#isDeleted()} for its state. If meet wasn't enabled previously this method
   * return {@link MoxtraMeetNotFoundException}.
   * 
   * @return {@link MoxtraMeet} instance
   * @throws MoxtraNotActivatedException
   * @throws MoxtraMeetNotFoundException
   */
  public MoxtraMeet getMeet() throws MoxtraNotActivatedException, MoxtraMeetNotFoundException {
    MoxtraMeet meet = meet();
    if (meet != null) {
      return meet;
    }
    throw new MoxtraMeetNotFoundException("Meet not found for " + uiApp.get());
  }

  /**
   * Check if Moxtra meet has been associated with this app. Note that meet can be already marked for
   * deleting.<br>
   * 
   * @return boolean <code>true</code> if meet available to get it, <code>false</code> otherwise
   * @throws MoxtraNotActivatedException
   * @see {@link #getMeet()}
   */
  public boolean hasMeet() throws MoxtraNotActivatedException {
    MoxtraMeet meet = meet();
    return meet != null;
  }

  /**
   * Check if Moxtra meet enabled for this app.<br>
   * 
   * @return boolean <code>true</code> if meet enabled, <code>false</code> otherwise
   * @throws MoxtraNotActivatedException
   * @see {@link #getMeet()}
   */
  public boolean isMeetEnabled() throws MoxtraNotActivatedException {
    MoxtraMeet meet = meet();
    return meet != null ? !meet.isDeleted() : false;
  }

  public boolean isActivated() {
    return this.requestForm.get() != null;
  }

  public boolean isInitialized() {
    return this.moxtra != null;
  }

  // TODO cleanup
  // @Deprecated
  // public String getCalendarId(UIForm form) throws MoxtraCalendarException {
  // if (form != null) {
  // return getCalendarId(form);
  // // } TODO
  // // else if (quickAddForm != null) {
  // // return getQuickAddCalendarId(quickAddForm);
  // } else {
  // throw new MoxtraCalendarException("Moxtra application form not activated");
  // }
  // }

  /**
   * Current Moxtra user.
   * 
   * @return {@link MoxtraUser}
   * @throws MoxtraCalendarException
   * @throws MoxtraException
   */
  public MoxtraUser getUser() throws MoxtraCalendarException, MoxtraException {
    // TODO avoid use this method often, it requests Moxtra API each time
    return moxtra().getUser();
  }

  /**
   * Current user calendar settings.
   * 
   * @return {@link CalendarSetting}
   * @throws Exception
   * @throws MoxtraCalendarException
   */
  public CalendarSetting getCalendarSetting() throws MoxtraCalendarException, Exception {
    return moxtra().getCalendarSetting();
  }

  /**
   * Current user contacts in Moxtra.<br>
   * 
   * @return {@link List} of {@link MoxtraUser}.
   * @throws MoxtraCalendarException
   * @throws MoxtraConfigurationException
   * @throws MoxtraException
   * @throws MoxtraAuthenticationException
   */
  public List<MoxtraUser> getUserContacts() throws MoxtraCalendarException,
                                           MoxtraAuthenticationException,
                                           MoxtraException,
                                           MoxtraConfigurationException {
    return moxtra().getContacts();
  }

  public void saveMeet() throws Exception {
    Set<String> eventIds = getEventIds();
    if (eventIds.size() > 0) {
      // this way we handle several events
      // TODO ensure multiple selection supported in other places
      for (String eventId : eventIds) {
        String calendarId = getEventCalendarId(); // can be null, but saveMeet() can handle it
        CalendarEvent event = moxtra().getEvent(eventId);
        if (LOG.isDebugEnabled()) {
          LOG.debug(">> saveMeet: event " + eventId + " \"" + event.getSummary() + "\""
              + (calendarId != null ? " in " + calendarId : ""));
        }
        moxtra().saveMeet(calendarId, event);
      }
    } else {
      LOG.error("Error saving meet: cannot find event id");
      throw new MoxtraCalendarException("Error saving meet: cannot find event id");
    }
  }

  // // ********* internals ***********

  /**
   * Initialize this app context.
   * 
   * @param form {@link UIForm}
   * @throws MoxtraCalendarException
   * @throws MoxtraConfigurationException
   * @throws MoxtraException
   */
  protected void initContext(UIForm form) throws MoxtraCalendarException,
                                         MoxtraException,
                                         MoxtraConfigurationException {

    // TODO cleanup
    // if (isQuickAddForm(form)) {
    // // quick-add form for new meet only
    // MoxtraMeet prevMeet = this.meets.remove(form);
    // if (prevMeet != null) {
    // requestMeet.set(prevMeet);
    // }
    // } else {
    // MoxtraMeet meet = this.meets.get(form);
    // if (meet == null) {
    // // find by form event
    // meet = findFormMeet(form, userName, moxtra);
    // if (meet == null) {
    // // find in meets in quick-add forms
    // meet = requestMeet.get();
    // // for (Map.Entry<UIComponent, MoxtraMeet> me : meets.entrySet()) {
    // // if (me.getKey().getId().equals(QUICK_ADD_EVENT_FORM_ID)) {
    // // meet = me.getValue();
    // // if (meet != null) {
    // // break;
    // // }
    // // }
    // // }
    // }

    this.requestForm.set(form);
    moxtra().initContext(this);
  }

  /**
   * Find UI component in given container by its type name or ID if it equals to the given name.<br>
   * 
   * @param container {@link UIContainer}
   * @param typeName {@link String}
   * @return {@link UIComponent} or <code>null</code>
   */
  @SuppressWarnings("unchecked")
  protected <T extends UIComponent> T findComponent(UIContainer container, String typeName) {
    // TODO kind of caching could do a good job here as traversing isn't best approach from perf POV
    // (thread-local caching)
    T child = container.findComponentById(typeName);
    if (child == null) {
      for (UIComponent cc : container.getChildren()) {
        if (cc.getClass().getSimpleName().equals(typeName)) {
          child = (T) cc;
          break;
        }
        if (cc instanceof UIContainer) {
          child = findComponent((UIContainer) cc, typeName);
          if (child != null) {
            break;
          }
        }
      }
    }
    return child;
  }

  /**
   * Find UI component child in given container by its super type name. If several such components found
   * preference will be given to a first rendered.<br>
   * 
   * @param container {@link UIContainer}
   * @param superTypeName {@link String}
   * @return {@link UIComponent} or <code>null</code>
   */
  @SuppressWarnings("unchecked")
  protected <T extends UIComponent> T findChildBySuperType(UIContainer container, String superTypeName) {
    List<T> childs = findChildsBySuperType(container, superTypeName, false);
    T child = null;
    for (UIComponent cc : childs) {
      if (child != null) {
        if (cc.isRendered()) {
          child = (T) cc;
          break;
        }
      } else {
        child = (T) cc;
        if (cc.isRendered()) {
          break;
        }
      }
    }
    return child;
  }

  /**
   * Find first rendered UI component in children of given container by a super type name. <br>
   * 
   * @param container {@link UIContainer}
   * @param superTypeName {@link String}
   * @return {@link UIComponent} or <code>null</code>
   */
  @SuppressWarnings("unchecked")
  protected <T extends UIComponent> T findRenderedChildBySuperType(UIContainer container, String superTypeName) {
    // TODO kind of caching could do a good job here as traversing isn't best approach from perf POV
    // (thread-local caching)
    List<T> childs = findChildsBySuperType(container, superTypeName, false);
    for (UIComponent cc : childs) {
      if (cc.isRendered()) {
        return (T) cc;
      }
    }
    return null;
  }

  /**
   * Find UI component children in given container by a super type name. <br>
   * 
   * @param container {@link UIContainer}
   * @param superTypeName {@link String}
   * @return {@link UIComponent} or <code>null</code>
   */
  @SuppressWarnings("unchecked")
  protected <T extends UIComponent> List<T> findChildsBySuperType(UIContainer container,
                                                                  String superTypeName,
                                                                  boolean deep) {
    // TODO kind of caching could do a good job here as traversing isn't best approach from perf POV
    // (thread-local caching)
    List<T> childs = new ArrayList<T>();
    for (UIComponent cc : container.getChildren()) {
      Class<?> superType = cc.getClass().getSuperclass();
      if (superType != null && superType.getSimpleName().equals(superTypeName)) {
        childs.add((T) cc);
        if (deep && cc instanceof UIContainer) {
          List<T> ccChilds = findChildsBySuperType((UIContainer) cc, superTypeName, deep);
          childs.addAll(ccChilds);
        }
      }
    }
    return childs;
  }

  /**
   * Find first rendered UI component in children of given container by an interface name. <br>
   * 
   * @param container {@link UIContainer}
   * @param interfaceName {@link String}
   * @return {@link UIComponent} or <code>null</code>
   */
  @SuppressWarnings("unchecked")
  protected <T extends UIComponent> T findRenderedChildByInterface(UIContainer container, String interfaceName) {
    // TODO kind of caching could do a good job here as traversing isn't best approach from perf POV
    // (thread-local caching)
    List<T> childs = findChildsByInterface(container, interfaceName, false);
    for (UIComponent cc : childs) {
      if (cc.isRendered()) {
        return (T) cc;
      }
    }
    return null;
  }

  /**
   * Find UI component children in given container by an interface name. <br>
   * 
   * @param container {@link UIContainer}
   * @param interfaceName {@link String}
   * @return {@link UIComponent} or <code>null</code>
   */
  @SuppressWarnings("unchecked")
  protected <T extends UIComponent> List<T> findChildsByInterface(UIContainer container,
                                                                  String interfaceName,
                                                                  boolean deep) {
    // TODO kind of caching could do a good job here as traversing isn't best approach from perf POV
    // (thread-local caching)
    List<T> childs = new ArrayList<T>();
    for (UIComponent cc : container.getChildren()) {
      for (Class<?> interfaceType : cc.getClass().getInterfaces()) {
        if (interfaceType.getSimpleName().equals(interfaceName)) {
          childs.add((T) cc);
          if (deep && cc instanceof UIContainer) {
            List<T> ccChilds = findChildsByInterface((UIContainer) cc, interfaceName, deep);
            childs.addAll(ccChilds);
          }
        }
      }
    }
    return childs;
  }

  /**
   * Hardcoded version of <code>UIEventForm.getCalendarId()</code>. <br>
   * 
   * @param eventForm {@link UIForm}
   * @return calendar id selected in given form or <code>null</code>
   * @throws MoxtraCalendarException
   */
  protected String getEventCalendarId() throws MoxtraCalendarException {
    UIForm form = this.requestForm.get();
    if (form != null) {
      UIFormInputWithActions eventDetailTab = form.getChildById("eventDetail");
      if (eventDetailTab != null) {
        try {
          String value = ((UIFormStringInput) eventDetailTab.findComponentById("calendar")).getValue();
          if (value != null && value.length() > 0) {
            value = value.trim();
            String[] values = value.split(COLON);
            if (values.length > 0) {
              return values[1];
            }
            return value;
          }
        } catch (NullPointerException e) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Error searching for calendar conponent in " + form, e);
          }
        }
      }
    }
    return null;
  }

  /**
   * Get current calendar type in UIEventForm. Based on idea of {@link #getCalendarId(UIForm)}.
   * 
   * @return
   * @throws MoxtraCalendarException
   */
  protected String getCalendarType() throws MoxtraCalendarException {
    UIForm form = this.requestForm.get();
    if (form != null) {
      UIFormInputWithActions eventDetailTab = form.getChildById("eventDetail");
      if (eventDetailTab != null) {
        try {
          String value = ((UIFormStringInput) eventDetailTab.findComponentById("calendar")).getValue();
          if (value != null && value.length() > 0) {
            value = value.trim();
            String[] values = value.split(COLON);
            if (values.length > 0) {
              return values[0];
            }
          }
        } catch (NullPointerException e) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Error searching for calendar conponent in " + form, e);
          }
        }
      }
    }
    return null;
  }

  /**
   * Event ids associated with current context (updated or deleted in the request).
   * 
   * @return {@link Set} of {@link String} with event ids
   */
  protected Set<String> getEventIds() {
    // TODO may be search for event id from an action code of concrete form? it will be more accurate
    Set<String> eventIds = new LinkedHashSet<String>();
    UIContainer viewContainer = this.requestViewContainer.get();
    if (viewContainer != null) {
      // FYI first source could be a request form, but as for UIEventForm it itself saves last saved eventId
      // in its calendarView, thus we read that value and do similarly for others usecases
      // UIApplication uiApp = this.uiApp.get();
      // UIContainer viewContainer = findComponent(uiApp, "UICalendarViewContainer");
      UIComponent viewForm;
      try {
        viewForm = (UIComponent) MethodUtils.invokeMethod(viewContainer, "getRenderedChild", null);
      } catch (Exception e) {
        // ignore error, try get from the container directly
        // search for classes implementing CalendarView
        viewForm = findRenderedChildByInterface(viewContainer, "CalendarView");
      }
      String eventId = null;
      if (viewForm != null) {
        try {
          // method of CalendarView
          eventId = (String) MethodUtils.invokeMethod(viewForm, "getLastUpdatedEventId", null);
        } catch (Exception e) {
          // ignore
        }
      }
      // TODO cleanup
      // if (eventId == null) {
      // // try as for UIListView and single event
      // UIContainer listContainer = findComponent(viewContainer, "UIListContainer");
      // if (listContainer != null && listContainer.isRendered()) {
      // viewForm = findComponent(viewContainer, "UIListView");
      // if (viewForm != null && viewForm.isRendered()) {
      // try {
      // // TODO MethodUtils.invokeMethod(listContainer, "getLastUpdatedEventId", null)
      // eventId = (String) MethodUtils.invokeMethod(viewForm, "getSelectedEvent", null);
      // } catch (Exception e) {
      // // ignore
      // }
      // }
      // }
      // }
      if (eventId != null) {
        eventIds.add(eventId);
      } else if (viewForm != null && viewForm.isRendered()) {
        // final attempt to find in multiple selection
        // try as for UIMonthView or UIListView and multiple events move
        // XXX but this approach may return wrong events: selected doesn't mean updated
        try {
          @SuppressWarnings("unchecked")
          List<CalendarEvent> events = (List<CalendarEvent>) MethodUtils.invokeMethod(viewForm,
                                                                                      "getSelectedEvents",
                                                                                      null);
          if (events != null) {
            for (CalendarEvent ce : events) {
              eventIds.add(ce.getId());
            }
          }
        } catch (Exception e) {
          // ignore
        }
      }
    } else if (LOG.isDebugEnabled()) {
      LOG.debug("<<< requestViewContainer not set for " + this.requestForm.get());
    }
    return eventIds;
  }

  /**
   * Reflection based access to <code>UIQuickAddEvent.getEventCalendar()</code>. <br>
   * 
   * @param quickAddForm {@link UIForm}
   * @return calendar id selected in given form or <code>null</code>
   * @throws MoxtraCalendarException
   */
  @Deprecated
  protected String getQuickAddCalendarId(UIForm quickAddForm) throws MoxtraCalendarException {
    // TODO cleanup
    // try {
    // Java reflection possible... but
    // java.lang.reflect.Method m = quickAddForm.getClass().getDeclaredMethod("getEventCalendar", new
    // Class[0]);m.setAccessible(true);Object r = m.invoke(quickAddForm, new Object[0]);LOG.debug(r);

    // it is anyway null here, probably to this moment quick-add form will be reset/cleaned for next
    // invocations
    @SuppressWarnings("unchecked")
    Object res = ((org.exoplatform.webui.form.UIFormInputBase<String>) quickAddForm.findComponentById("calendar")).getValue();

    // TODO Apache reflection does work
    // Object res = MethodUtils.invokeMethod(quickAddForm, "getEventCalendar", null);
    if (res != null) {
      if (res instanceof String) {
        return (String) res;
      }
      throw new MoxtraCalendarException("Unexpected object type returned from getEventCalendar() on "
          + quickAddForm.getClass().getName());
    } else {
      return null;
    }
    // } catch (NoSuchMethodException e) {
    // throw new MoxtraCalendarException("Cannot find getEventCalendar() method in "
    // + quickAddForm.getClass().getName(), e);
    // } catch (IllegalAccessException e) {
    // throw new MoxtraCalendarException("Cannot access getEventCalendar() method in "
    // + quickAddForm.getClass().getName(), e);
    // } catch (InvocationTargetException e) {
    // throw new MoxtraCalendarException("Error invoking getEventCalendar() method on " + quickAddForm
    // + (e.getCause() != null ? ": " + e.getCause().getMessage() : ""), e);
    // }
  }

  protected MoxtraCalendarService moxtra() throws MoxtraNotInitializedException {
    MoxtraCalendarService moxtra = this.moxtra;
    if (moxtra != null) {
      return moxtra;
    } else {
      throw new MoxtraNotInitializedException("Moxtra application not initialized");
    }
  }

  protected UIForm form() throws MoxtraNotActivatedException {
    UIForm form = this.requestForm.get();
    if (form != null) {
      return form;
    } else {
      throw new MoxtraNotActivatedException("Moxtra application not activated");
    }
  }

  /**
   * Find existing meet enabled for even shown in given form. Return <code>null</code> if no meet enabled.
   * 
   * @param form
   * @param userName
   * @return {@link MoxtraMeet} instance for already enabled meet or <code>null</code>
   * @throws MoxtraCalendarException
   * @throws MoxtraNotInitializedException
   */
  protected MoxtraMeet findFormMeet(UIForm form) throws MoxtraNotInitializedException,
                                                MoxtraCalendarException {
    // XXX obtain CalendarEvent in nasty way... we have no other way
    try {
      CalendarEvent event = (CalendarEvent) FieldUtils.readField(form, "calendarEvent_", true);
      if (event != null) {
        // form of existing event
        MoxtraMeet meet = moxtra().getMeet(event);
        if (meet != null) {
          // TODO update meet in event node
          // we have a meet enabled in this event: use editor of it
          return meet.editor();
        } else {
          // meet wasn't enabled for the event
          return null;
        }
      } else {
        // form of new event (meet can be enabled by enableMeet())
        return null;
      }
    } catch (IllegalAccessException e) {
      throw new MoxtraCalendarException("Cannot find calendar event in " + form, e);
    }
  }

  protected MoxtraMeet meet() throws MoxtraNotActivatedException {
    MoxtraMeet meet = this.meets.get(form());
    return meet;
  }

  protected boolean isQuickAddForm(UIComponent form) {
    return QUICK_ADD_EVENT_FORM_ID.equals(form.getId()) || QUICK_ADD_TASK_FORM_ID.equals(form.getId());
  }

  /**
   * Merge component configurations: event with listeners and if template defined, from extension to original
   * component config. When template defined in extension it will replace the original.
   * 
   * @throws Exception
   */
  @SuppressWarnings("rawtypes")
  public void mergeConfigs(Component original, Class<? extends UIComponent> clazz) throws Exception {
    if (!mergedConfigs.contains(original)) {
      WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
      WebuiApplication webuiApp = (WebuiApplication) context.getApplication();
      Component extension = webuiApp.getConfigurationManager().getComponentConfig(clazz, null);
      Set<Event> extEvents = new LinkedHashSet<Event>(extension.getEvents());
      List<Event> events = original.getEvents();
      for (Event oe : events) {
        String eventName = oe.getName();
        for (Iterator<Event> eeiter = extEvents.iterator(); eeiter.hasNext();) {
          Event ee = eeiter.next();
          if (eventName.equals(ee.getName())) {
            // same name event: we merge extension listeners to the original instance

            // listener names (set to avoid duplicates)
            Set<String> names = new LinkedHashSet<String>();
            names.addAll(oe.getListeners()); // first original
            names.addAll(ee.getListeners()); // extended after
            oe.setListeners(new ArrayList<String>(names));

            // listeners instances (set to avoid duplicates)
            // ensure event configs cached listeners internally
            original.getUIComponentEventConfig(eventName);
            extension.getUIComponentEventConfig(eventName);
            Set<EventListener> listeners = new LinkedHashSet<EventListener>();
            listeners.addAll(oe.getCachedEventListeners());
            listeners.addAll(ee.getCachedEventListeners());
            oe.setCachedEventListeners(new ArrayList<EventListener>(listeners));

            // remove merged from ext events, to later add not merged to the original as new events
            eeiter.remove();
          }
        }
      }
      if (extEvents.size() > 0) {
        events.addAll(extEvents);
        // XXX we need reset internal map to let it be re-populated with added new events
        FieldUtils.writeDeclaredField(original, "eventMap", null, true);
      }
      if (extension.getTemplate() != null && extension.getTemplate().length() > 0) {
        // TODO implement template replacement: use new instance will be better?
        FieldUtils.writeDeclaredField(original, "template", extension.getTemplate(), true);
      }
      mergedConfigs.add(original);
    }
  }

  @Deprecated
  protected MoxtraMeet getEventMeet(String eventId) {
    SoftReference<MoxtraMeet> mref = this.eventMeets.get(eventId);
    if (mref != null) {
      MoxtraMeet meet = mref.get();
      if (meet != null) {
        return meet;
      }
    }
    return null;
  }

}
