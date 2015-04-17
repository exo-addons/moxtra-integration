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
package org.exoplatform.moxtra.webui.config;

import org.exoplatform.commons.serialization.api.annotations.Converted;
import org.exoplatform.webui.config.Component;
import org.exoplatform.webui.config.ComponentConfigConverter;
import org.exoplatform.webui.config.Event;
import org.exoplatform.webui.config.EventInterceptor;
import org.exoplatform.webui.config.InitParams;
import org.exoplatform.webui.config.Validator;
import org.exoplatform.webui.event.EventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: ExtendedComponent.java 00000 Mar 31, 2015 pnedonosko $
 * 
 */
@Deprecated
@Converted(ComponentConfigConverter.class)
public class ExtendedComponent extends Component {

  protected final Component    original;

  protected final Component    extension;

  protected List<Event>        events;

  protected Map<String, Event> eventsMap = new LinkedHashMap<String, Event>();

  protected final Set<String>  merged    = new LinkedHashSet<String>();

  private ExtendedComponent(Class<?> owner,
                            String id,
                            String type,
                            String lifecycle,
                            String template,
                            String decorator,
                            InitParams initParams,
                            List<Validator> validators,
                            List<Event> events,
                            List<EventInterceptor> eventInterceptors) {
    super(owner, id, type, lifecycle, template, decorator, initParams, validators, events, eventInterceptors);
    this.original = extension = null;
    this.events = null;
  }

  public ExtendedComponent(Class<?> owner, Component orig, Component extension) throws Exception {
    super(owner,
          orig.getId(),
          orig.getType(),
          orig.getLifecycle(),
          orig.getTemplate(),
          orig.getDecorator(),
          orig.getInitParams(),
          orig.getValidators(),
          orig.getEvents(),
          orig.getEventInterceptors());
    this.original = orig;
    this.extension = extension;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Event> getEvents() {
    if (eventsMap.size() > 0) {
      // need merge events from original and extension

    }
    return events;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getUIComponentEventConfig(String eventName) throws Exception {
    Event origEvent = original.getUIComponentEventConfig(eventName);
    Event extEvent = extension.getUIComponentEventConfig(eventName);
    if (extEvent != null && false) {
      // merge event listeners from extension to original object

      Set<String> names = new LinkedHashSet<String>();
      Set<EventListener> listeners = new LinkedHashSet<EventListener>();
      names.addAll(origEvent.getListeners()); // first original
      names.addAll(extEvent.getListeners()); // extended after
      origEvent.setListeners(new ArrayList<String>(names));

      listeners.addAll(origEvent.getCachedEventListeners());
      listeners.addAll(extEvent.getCachedEventListeners());
      origEvent.setCachedEventListeners(new ArrayList<EventListener>(listeners));
    }
    return origEvent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EventListener> getUIComponentEventListeners(String eventName) throws Exception {
    // return original.getUIComponentEventListeners(eventName);
    return getUIComponentEventConfig(eventName).getCachedEventListeners();
  }

  /**
   * Merge events.
   * 
   * @return
   * @throws Exception
   */
  protected List<Event> mergeEvents() throws Exception {
    List<Event> events = new ArrayList<Event>();
    Set<String> mergedNames = new HashSet<String>();
    for (Event oe : original.getEvents()) {
      String eventName = oe.getName();
      for (Event ee : extension.getEvents()) {
        if (eventName.equals(ee.getName())) {
          mergedNames.add(eventName);
          // for same name event we merge original and extension listeners into a new event
          Event ne = copyEvent(oe);

          // listener names (set to avoid duplicates)
          Set<String> names = new LinkedHashSet<String>();
          names.addAll(oe.getListeners()); // first original
          names.addAll(ee.getListeners()); // extended after
          ne.setListeners(new ArrayList<String>(names));

          // listeners instances (set to avoid duplicates)
          // ensure event configs cached listeners internally
          original.getUIComponentEventConfig(eventName);
          extension.getUIComponentEventConfig(eventName);
          Set<EventListener> listeners = new LinkedHashSet<EventListener>();
          listeners.addAll(oe.getCachedEventListeners());
          listeners.addAll(ee.getCachedEventListeners());
          ne.setCachedEventListeners(new ArrayList<EventListener>(listeners));

          oe = ne; // use new as original
        }
      }
      events.add(oe);
    }
    // add not yet merged from extension
    for (Event ee : extension.getEvents()) {
      if (!mergedNames.contains(ee.getName())) {
        events.add(ee);
      }
    }
    return events;
  }

  /**
   * Copy event without listeners.
   * 
   * @param src
   * @return
   */
  private Event copyEvent(Event src) {
    Event event = new Event();
    event.setExecutionPhase(src.getExecutionPhase());
    event.setConfirm(src.getConfirm());
    event.setInitParams(src.getInitParams());
    event.setName(src.getName());
    event.setCsrfCheck(src.isCsrfCheck());
    return event;
  }

}
