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
package org.exoplatform.moxtra.social.space;

import org.exoplatform.moxtra.social.MoxtraSocialService;
import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceListenerPlugin;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraSpaceListener.java 00000 May 8, 2015 pnedonosko $
 * 
 */
public class MoxtraSpaceListener extends SpaceListenerPlugin {

  protected static final Log          LOG = ExoLogger.getLogger(MoxtraSpaceListener.class);

  protected final MoxtraSocialService moxtra;

  /**
   * 
   */
  public MoxtraSpaceListener(MoxtraSocialService moxtra) {
    this.moxtra = moxtra;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    // TODO what we could do here?
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void spaceRemoved(SpaceLifeCycleEvent event) {
    // TODO remove space binder if created an one
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void joined(SpaceLifeCycleEvent event) {
    // Auto-join space user(s) to its binder if enabled
    joinUser(event);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void left(SpaceLifeCycleEvent event) {
    removeUser(event);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void spaceRenamed(SpaceLifeCycleEvent event) {
    // Rename binder if enabled
    renameBinder(event);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void spaceAvatarEdited(SpaceLifeCycleEvent event) {
    // TODO Update space's finder avatar if enabled
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addInvitedUser(SpaceLifeCycleEvent event) {
    // Auto-join user to binder if enabled
    joinUser(event);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addPendingUser(SpaceLifeCycleEvent event) {
    // Auto-join user to binder if enabled
    joinUser(event);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void spaceDescriptionEdited(SpaceLifeCycleEvent event) {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void revokedLead(SpaceLifeCycleEvent event) {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void applicationActivated(SpaceLifeCycleEvent event) {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void applicationAdded(SpaceLifeCycleEvent event) {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void applicationDeactivated(SpaceLifeCycleEvent event) {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void applicationRemoved(SpaceLifeCycleEvent event) {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void grantedLead(SpaceLifeCycleEvent event) {
    // nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void spaceAccessEdited(SpaceLifeCycleEvent event) {
    // nothing
  }

  protected void joinUser(SpaceLifeCycleEvent event) {
    String userName = event.getTarget();
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(event.getSpace());
      if (binderSpace != null) {
        binderSpace.ensureBinderMember(userName);
      }
    } catch (Exception e) {
      LOG.error("Error joining user " + userName + " to binder of " + event.getSpace().getPrettyName(), e);
    }
  }

  protected void removeUser(SpaceLifeCycleEvent event) {
    String userName = event.getTarget();
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(event.getSpace());
      if (binderSpace != null) {
        binderSpace.removeBinderMember(userName);
      }
    } catch (Exception e) {
      LOG.error("Error removing user " + userName + " from binder of " + event.getSpace().getPrettyName(), e);
    }
  }

  protected void renameBinder(SpaceLifeCycleEvent event) {
    String newName = event.getSpace().getDisplayName();
    try {
      MoxtraBinderSpace binderSpace = moxtra.getBinderSpace(event.getSpace());
      if (binderSpace != null) {
        binderSpace.renameBinder(newName);
      }
    } catch (Exception e) {
      LOG.error("Error renaming binder of " + event.getSpace().getPrettyName() + " to '" + newName + "'", e);
    }
  }
}
