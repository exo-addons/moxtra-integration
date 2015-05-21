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
package org.exoplatform.moxtra.social.portlet;

import juzu.SessionScoped;

import org.exoplatform.moxtra.social.MoxtraSocialService.MoxtraBinderSpace;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Current space context for Moxtra Binder app. Used as binder for new integrations space-binder. Context is
 * thread safe.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraBinderSpaceContext.java 00000 May 16, 2015 pnedonosko $
 * 
 */
@SessionScoped
public class MoxtraBinderSpaceContext {

  protected MoxtraBinderSpace   binderSpace;

  protected final ReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * 
   */
  public MoxtraBinderSpaceContext() {
  }

  public void set(MoxtraBinderSpace binderSpace) {
    lock.writeLock().lock();
    try {
      this.binderSpace = binderSpace;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public MoxtraBinderSpace get() {
    lock.readLock().lock();
    try {
      return binderSpace;
    } finally {
      lock.readLock().unlock();
    }
  }

  public void reset() {
    lock.writeLock().lock();
    try {
      this.binderSpace = null;
    } finally {
      lock.writeLock().unlock();
    }
  }
}
