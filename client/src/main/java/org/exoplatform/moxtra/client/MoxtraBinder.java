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
package org.exoplatform.moxtra.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Moxtra binder as described in <a
 * href="https://developer.moxtra.com/docs/docs-rest-api/#binder-apis">
 * their REST API</a>.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraBinder.java 00000 Feb 28, 2015 pnedonosko $
 * 
 */
public class MoxtraBinder {

  /**
   * Creator user type in a binder.
   */
  public static final String           USER_TYPE_BOARD_OWNER      = "BOARD_OWNER";

  /**
   * Invited user type in a binder with read/writer permissions.
   */
  public static final String           USER_TYPE_BOARD_READ_WRITE = "BOARD_READ_WRITE";

  /**
   * Creator user status in a binder.
   */
  public static final String           USER_STATUS_BOARD_MEMBER   = "BOARD_MEMBER";

  /**
   * Invited user status in a binder.
   */
  public static final String           USER_STATUS_BOARD_INVITED  = "BOARD_INVITED";

  /**
   * Binder ID.
   * 
   */
  protected String                     binderId;

  /**
   * Meet name, also known as binder name.
   */
  protected String                     name;

  protected Long                       revision;

  protected String                     thumbnailUrl;

  protected Date                       createdTime;

  protected Date                       updatedTime;

  /**
   * Binder users. Should be initialized by {@link #setUsers(List)}.
   */
  protected List<MoxtraUser>           users;

  protected transient final Boolean    isNew;

  protected transient boolean          deleted;

  /**
   * Original binder in editor instance. In other cases it is <code>null</code>.
   */
  private transient final MoxtraBinder original;

  /**
   * Added users in editor instance. In other cases <code>null</code>.
   */
  private transient List<MoxtraUser>   addedUsers;

  /**
   * Removed users in editor instance. In other cases <code>null</code>.
   */
  private transient List<MoxtraUser>   removedUsers;

  /**
   * Editor of this binder if any was created once by {@link #editor()} method or <code>null</code>.
   */
  private transient MoxtraBinder       editor;

  protected MoxtraBinder(String binderId,
                         String name,
                         Long revision,
                         String thumbnailUrl,
                         Date createdTime,
                         Date updatedTime) {
    this.isNew = Boolean.FALSE;
    this.deleted = false;
    this.binderId = binderId;
    this.name = name;
    this.revision = revision;
    this.thumbnailUrl = thumbnailUrl;
    this.createdTime = createdTime;
    this.updatedTime = updatedTime;
    this.original = null;
  }

  /**
   * New binder constructor. For creating a new binder in Moxtra.
   */
  public MoxtraBinder() {
    this.isNew = Boolean.TRUE;
    this.original = null;
  }

  /**
   * Existing binder constructor (for reading from Binder REST API).
   * 
   * @param binderId
   * @param name
   * @param revision
   * @param createdTime
   * @param updatedTime
   */
  public MoxtraBinder(String binderId, String name, Long revision, Date createdTime, Date updatedTime) {
    this(binderId, name, revision, null, createdTime, updatedTime);
  }

  /**
   * Editor constructor.
   * 
   * @param other
   */
  protected MoxtraBinder(MoxtraBinder other) {
    this.isNew = null; // should be never used
    this.deleted = false;
    this.original = other;
    this.addedUsers = new ArrayList<MoxtraUser>();
    this.removedUsers = new ArrayList<MoxtraUser>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    String bid = getBinderId();
    if (bid != null) {
      str.append(bid);
    }
    if (str.length() > 0) {
      str.append(' ');
    }
    str.append(getName());
    return str.toString();
  }

  /**
   * Return an editor instance for this binder. If invoke this method on already editor instance then it will
   * return itself. If invoke several times, the same editor instance will be returned. Editor exists as a
   * singleton for a particular binder.
   * 
   * @return {@link MoxtraBinder} edit instance
   */
  public MoxtraBinder editor() {
    if (editor != null) {
      return editor;
    } else if (isEditor()) {
      return this;
    } else {
      return editor = new MoxtraBinder(this);
    }
  }

  /**
   * Answers is this a new binder (not yet saved) for Moxtra.
   * 
   * @return boolean <code>true</code> if it is a new meet, <code>false</code> otherwise
   */
  public boolean isNew() {
    if (isEditor()) {
      return original.isNew();
    } else {
      return isNew;
    }
  }

  /**
   * Answers is this binder should be deleted in Moxtra.
   * 
   * @return boolean <code>true</code> if meet should be deleted, <code>false</code> otherwise
   */
  public boolean hasDeleted() {
    if (isEditor()) {
      return deleted;
    } else {
      return false;
    }
  }

  /**
   * Mark this binder as such that should be deleted in Moxtra.
   */
  public void delete() {
    if (isEditor()) {
      this.deleted = true;
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  /**
   * Mark this binder as such that should not be deleted in Moxtra. It should be created or updated depending
   * on {@link #isNew()} result.
   */
  public void undelete() {
    if (isEditor()) {
      this.deleted = false;
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  /**
   * @return the binder id
   */
  public String getBinderId() {
    return binderId != null ? binderId : (isEditor() ? original.getBinderId() : null);
  }

  /**
   * @return the name
   */
  public String getName() {
    return name != null ? name : (isEditor() ? original.getName() : null);
  }

  /**
   * @return the revision
   */
  public Long getRevision() {
    return revision != null ? revision : (isEditor() ? original.getRevision() : null);
  }

  /**
   * @return the thumbnailUrl
   */
  public String getThumbnailUrl() {
    return thumbnailUrl != null ? thumbnailUrl : (isEditor() ? original.getThumbnailUrl() : null);
  }

  /**
   * @return the createdTime
   */
  public Date getCreatedTime() {
    return createdTime != null ? createdTime : (isEditor() ? original.getCreatedTime() : null);
  }

  /**
   * @return the updatedTime
   */
  public Date getUpdatedTime() {
    return updatedTime != null ? updatedTime : (isEditor() ? original.getUpdatedTime() : null);
  }

  /**
   * @param user
   */
  public synchronized void addUser(MoxtraUser user) {
    if (isEditor()) {
      for (MoxtraUser added : addedUsers) {
        if (added.equals(user)) {
          return;
        }
      }
      List<MoxtraUser> origUsers = original.getUsers();
      if (origUsers != null) {
        for (MoxtraUser existing : origUsers) {
          if (existing.equals(user)) {
            return;
          }
        }
      }
      for (Iterator<MoxtraUser> riter = removedUsers.iterator(); riter.hasNext();) {
        MoxtraUser removed = riter.next();
        if (removed.equals(user)) {
          riter.remove();
        }
      }
      this.addedUsers.add(user);
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  /**
   * @param user
   */
  public synchronized void removeUser(MoxtraUser user) {
    if (isEditor()) {
      for (MoxtraUser removed : removedUsers) {
        if (removed.equals(user)) {
          return;
        }
      }
      for (Iterator<MoxtraUser> aiter = addedUsers.iterator(); aiter.hasNext();) {
        MoxtraUser added = aiter.next();
        if (added.equals(user)) {
          aiter.remove();
        }
      }
      this.removedUsers.add(user);
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  /**
   * @return the users
   */
  public synchronized List<MoxtraUser> getUsers() {
    if (isEditor()) {
      List<MoxtraUser> origUsers = original.getUsers();
      if (removedUsers != null) {
        // removed and added already consistent, see removeUser()
        List<MoxtraUser> res = new ArrayList<MoxtraUser>();
        if (origUsers != null) {
          next: for (MoxtraUser existing : origUsers) {
            for (MoxtraUser removed : removedUsers) {
              if (existing.equals(removed)) {
                continue next;
              }
            }
            res.add(existing);
          }
        }
        if (addedUsers != null) {
          res.addAll(addedUsers);
        }
        return res;
      } else if (addedUsers != null) {
        // added already consistent with existing, see addUser()
        List<MoxtraUser> res = new ArrayList<MoxtraUser>();
        if (origUsers != null) {
          res.addAll(origUsers);
        }
        res.addAll(addedUsers);
        return res;
      } else {
        return origUsers;
      }
    } else {
      return users;
    }
  }

  /**
   * @return the addedUsers
   */
  public List<MoxtraUser> getAddedUsers() {
    if (isEditor()) {
      return Collections.unmodifiableList(addedUsers);
    } else {
      return null;
    }
  }

  public boolean hasUsersAdded() {
    if (isEditor()) {
      return this.addedUsers.size() > 0;
    } else {
      return false;
    }
  }

  /**
   * @return the removedUsers
   */
  public List<MoxtraUser> getRemovedUsers() {
    if (isEditor()) {
      return Collections.unmodifiableList(removedUsers);
    } else {
      return null;
    }
  }

  public boolean hasUsersRemoved() {
    if (isEditor()) {
      return this.removedUsers.size() > 0;
    } else {
      return false;
    }
  }

  public void editName(String newName) {
    if (isEditor()) {
      this.name = newName;
    } else {
      throw new IllegalStateException("Not editor instance");
    }
  }

  public boolean hasNameChanged() {
    if (isEditor() && this.name != null) {
      return !this.name.equals(original.getName());
    } else {
      return false;
    }
  }

  public boolean isEditor() {
    return original != null;
  }

  // ******* internals *******

  /**
   * Set binder users.
   * 
   * @param users
   */
  protected void setUsers(List<MoxtraUser> users) {
    if (isEditor()) {
      this.original.setUsers(users);
      this.users = null;
      this.deleted = false;
      this.addedUsers.clear();
      this.removedUsers.clear();
    } else {
      this.users = users;
    }
  }

  /**
   * @param String the binder id to set
   */
  protected void setBinderId(String binderId) {
    if (isEditor()) {
      this.original.setBinderId(binderId);
      this.binderId = null;
      this.deleted = false;
    } else {
      this.binderId = binderId;
    }
  }

  /**
   * @param name the name to set
   */
  protected void setName(String name) {
    if (isEditor()) {
      this.original.setName(name);
      this.name = null;
      this.deleted = false;
    } else {
      this.name = name;
    }
  }

  /**
   * @param revision the revision to set
   */
  protected void setRevision(Long revision) {
    if (isEditor()) {
      this.original.setRevision(revision);
      this.revision = null;
      this.deleted = false;
    } else {
      this.revision = revision;
    }
  }

  /**
   * @param String the thumbnailUrl to set
   */
  protected void setThumbnailUrl(String url) {
    if (isEditor()) {
      this.original.setThumbnailUrl(url);
      this.deleted = false;
    } else {
      this.thumbnailUrl = url;
    }
  }

  /**
   * @param createdTime the createdTime to set
   */
  protected void setCreatedTime(Date createdTime) {
    if (isEditor()) {
      this.original.setCreatedTime(createdTime);
      this.deleted = false;
    } else {
      this.createdTime = createdTime;
    }
  }

  /**
   * @param updatedTime the updatedTime to set
   */
  protected void setUpdatedTime(Date updatedTime) {
    if (isEditor()) {
      this.original.setUpdatedTime(updatedTime);
      this.deleted = false;
    } else {
      this.updatedTime = updatedTime;
    }
  }
}
