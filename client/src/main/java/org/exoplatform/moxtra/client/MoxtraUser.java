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

import java.util.Date;

/**
 * Moxtra user as described in <a
 * href="https://developer.moxtra.com/docs/docs-rest-api/#user-apis">
 * their REST API</a>.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraUser.java 00000 Feb 26, 2015 pnedonosko $
 * 
 */
public class MoxtraUser {

  public static final String USER_TYPE_NORMAL = "USER_TYPE_NORMAL";

  protected final String     id;

  protected final String     name;

  protected final String     email;

  protected final String     firstName;

  protected final String     lastName;

  protected final String     type;

  protected final String     status;

  protected final Date       createdTime;

  protected final Date       updatedTime;

  protected final int        hashCode;

  /**
   * Moxtra user full definition.
   * 
   * @param id
   * @param name
   * @param email
   * @param firstName
   * @param lastName
   * @param type
   * @param createdTime
   * @param updatedTime
   */
  public MoxtraUser(String id,
                    String name,
                    String email,
                    String firstName,
                    String lastName,
                    String type,
                    String status,
                    Date createdTime,
                    Date updatedTime) {
    super();
    this.email = email;
    this.id = id;
    // XXX name can be empty - use email then, if email is null then use id
    this.name = name != null && name.length() > 0 ? name : (email != null && email.length() > 0 ? email : id);
    this.firstName = firstName;
    this.lastName = lastName;
    this.type = type != null ? type : MoxtraUser.USER_TYPE_NORMAL;
    this.status = status;
    this.createdTime = createdTime;
    this.updatedTime = updatedTime;

    int hc = 1;
    if (id != null) {
      hc = hc * 31 + id.hashCode();
    }
    hc = hc * 31 + name.hashCode();
    hc = hc * 31 + email.hashCode();
    if (type != null) {
      hc = hc * 31 + type.hashCode();
    }
    if (createdTime != null) {
      hc = hc * 31 + createdTime.hashCode();
    }
    if (updatedTime != null) {
      hc = hc * 31 + updatedTime.hashCode();
    }
    this.hashCode = hc;
  }

  /**
   * User in Moxtra Contacts.<br>
   * 
   * @param id
   * @param name
   * @param email
   */
  public MoxtraUser(String id, String name, String email) {
    this(id, name, email, null, null, USER_TYPE_NORMAL, null, null, null);
  }

  /**
   * User in Moxtra Meet participants.<br>
   * 
   * @param id
   * @param name
   * @param email
   * @param type
   */
  public MoxtraUser(String id, String name, String email, String type) {
    this(id, name, email, null, null, type, null, null, null);
  }

  /**
   * User with only email known (for invitation).<br>
   * 
   * @param email
   */
  public MoxtraUser(String email) {
    this(email, email, email, null, null, USER_TYPE_NORMAL, null, null, null);
  }

  /**
   * User with name and email known (for invitation).<br>
   * 
   * @param name
   * @param email
   */
  public MoxtraUser(String name, String email) {
    this(email, name, email, null, null, USER_TYPE_NORMAL, null, null, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj != null) {
      if (obj instanceof MoxtraUser) {
        return isSameUser((MoxtraUser) obj);
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    String name = getName();
    String email = getEmail();
    String id = getId();
    if (email != null && (email.equals(id) || email.equals(name))) {
      return email;
    }
    StringBuilder str = new StringBuilder();
    str.append(name);
    str.append(" (");
    str.append(email);
    str.append(") ");
    str.append(id);
    return str.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return hashCode;
  }

  /**
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @return the email
   */
  public String getEmail() {
    return email;
  }

  /**
   * @return the firstName
   */
  public String getFirstName() {
    return firstName;
  }

  /**
   * @return the lastName
   */
  public String getLastName() {
    return lastName;
  }

  /**
   * User type depends on a context where user was retrieved. If user obtained as binder or meet participant,
   * then type describes its rights in the binder. By default type is {@link #USER_TYPE_NORMAL}.
   * 
   * @return the type
   */
  public String getType() {
    return type;
  }

  /**
   * User status depends on a context where user was retrieved. If user obtained as binder or meet
   * participant, then status describes its membership level in the binder (e.g.
   * {@link MoxtraBinder#USER_STATUS_BOARD_MEMBER}). By default status is <code>null</code>.
   * 
   * @return the status or <code>null</code>
   */
  public String getStatus() {
    return status;
  }

  /**
   * @return the createdTime
   */
  public Date getCreatedTime() {
    return createdTime;
  }

  /**
   * @return the updatedTime
   */
  public Date getUpdatedTime() {
    return updatedTime;
  }

  // ******** internals ******

  /**
   * Compare this user with given by id and then email.
   * 
   * @param user
   * @return <code>true</code> if users' id or emails equal
   */
  protected boolean isSameUser(MoxtraUser user) {
    String id = getId();
    if (id != null && id.equals(user.getId())) {
      return true;
    }
    String email = getEmail();
    if (email != null && email.equals(user.getEmail())) {
      return true;
    }
    return false;
  }

}
