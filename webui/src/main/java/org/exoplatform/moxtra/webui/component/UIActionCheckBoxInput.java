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
package org.exoplatform.moxtra.webui.component;

import org.exoplatform.moxtra.webui.MoxtraAction;
import org.exoplatform.moxtra.webui.component.UIActionCheckBoxInput.OnChangeActionListener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.input.UICheckBoxInput;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UIEnableMoxtraCheckBoxInput.java 00000 Mar 29, 2015 pnedonosko $
 * 
 */
@ComponentConfig(events = { @EventConfig(listeners = OnChangeActionListener.class) })
public class UIActionCheckBoxInput extends UICheckBoxInput {

  public static final String ACTION_ON_CHANGE = "OnChange".intern();

  protected static final Log LOG              = ExoLogger.getLogger(UIActionCheckBoxInput.class);

  public static class OnChangeActionListener extends EventListener<UIActionCheckBoxInput> {
    @Override
    public void execute(Event<UIActionCheckBoxInput> event) throws Exception {
      UIActionCheckBoxInput checkbox = event.getSource();
      checkbox.setChecked(Boolean.parseBoolean(event.getRequestContext().getRequestParameter(OBJECTID)));
      boolean checked = checkbox.action().execute(event);
      if (checked != checkbox.isChecked()) {
        checkbox.setChecked(checked);
        event.getRequestContext().addUIComponentToUpdateByAjax(checkbox);
      }
    }
  }

  protected MoxtraAction<Event<UIActionCheckBoxInput>, Boolean> action;

  protected boolean                                             disabled;

  /**
   * Empty constructor.
   */
  public UIActionCheckBoxInput() {
    super(UIActionCheckBoxInput.class.getSimpleName(), UIActionCheckBoxInput.class.getSimpleName(), false);
  }

  public void initMoxtra(MoxtraAction<Event<UIActionCheckBoxInput>, Boolean> action,
                         boolean disabled,
                         boolean initialValue) {
    this.action = action;
    this.setDisabled(disabled);
    this.setChecked(initialValue);

    // need set this to cause rendering of onlick in the checkbox HTML, this will invoke renderOnChangeEvent()
    this.setOnChange(ACTION_ON_CHANGE);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String renderOnChangeEvent(UIForm uiForm) throws Exception {
    // TODO return super.renderOnChangeEvent(uiForm);
    // return event(onchange_, componentEvent_, (String) null);
    // set objectid to next value should be set by the checkbox
    return event(ACTION_ON_CHANGE, String.valueOf(!isChecked()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getLabel() {
    String alabel = action.getLabel();
    return alabel != null ? alabel : super.getLabel();
  }

  protected MoxtraAction<Event<UIActionCheckBoxInput>, Boolean> action() {
    return action;
  }

}
