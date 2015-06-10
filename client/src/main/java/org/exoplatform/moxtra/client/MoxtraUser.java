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

  protected final String     uniqueId;

  protected final String     orgId;

  protected final String     name;

  protected final String     email;

  protected final String     firstName;

  protected final String     lastName;

  protected final String     pictureUri;

  protected final String     type;

  protected final Date       createdTime;

  protected final Date       updatedTime;

  protected final int        hashCode;

  protected String           status;

  /**
   * Moxtra user full definition.
   * 
   * @param id
   * @param uniqueId
   * @param orgId
   * @param name
   * @param email
   * @param firstName
   * @param lastName
   * @param pictureUri
   * @param type
   * @param createdTime
   * @param updatedTime
   */
  public MoxtraUser(String id,
                    String uniqueId,
                    String orgId,
                    String name,
                    String email,
                    String firstName,
                    String lastName,
                    String pictureUri,
                    String type,
                    Date createdTime,
                    Date updatedTime) {
    super();
    this.email = email;
    this.id = id;
    this.uniqueId = uniqueId;
    this.orgId = orgId;
    // XXX name can be empty - use email then, if email is null then use id
    this.name = name != null && name.length() > 0 ? name
                                                 : (email != null && email.length() > 0 ? email
                                                                                       : (uniqueId != null ? uniqueId
                                                                                                          : id));
    this.firstName = firstName;
    this.lastName = lastName;
    this.pictureUri = pictureUri;
    this.type = type != null ? type : MoxtraUser.USER_TYPE_NORMAL;
    this.createdTime = createdTime;
    this.updatedTime = updatedTime;

    int hc = 1;
    if (id != null) {
      hc = hc * 31 + id.hashCode();
    }
    hc = hc * 31 + this.name.hashCode();
    hc = hc * 31 + this.email.hashCode();
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
   * Moxtra user definition without unique_id and org_id.
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
  @Deprecated
  public MoxtraUser(String id,
                    String name,
                    String email,
                    String firstName,
                    String lastName,
                    String pictureUri,
                    String type,
                    Date createdTime,
                    Date updatedTime) {
    this(id, null, null, name, email, firstName, lastName, pictureUri, type, createdTime, updatedTime);
  }

  /**
   * User in Moxtra Binder/Meet memebers.<br>
   * 
   * @param id
   * @param uniqueId
   * @param orgId
   * @param name
   * @param email
   * @param pictureUri
   * @param type
   */
  public MoxtraUser(String id,
                    String uniqueId,
                    String orgId,
                    String name,
                    String email,
                    String pictureUri,
                    String type) {
    this(id, uniqueId, orgId, name, email, null, null, pictureUri, type, null, null);
  }

  /**
   * User with name and email known (for invitation of local eXo users).<br>
   * 
   * @param uniqueId
   * @param orgId
   * @param name
   * @param email
   */
  public MoxtraUser(String uniqueId, String orgId, String name, String email) {
    this(null, uniqueId, orgId, name, email, null, null, null, USER_TYPE_NORMAL, null, null);
  }

  /**
   * User with email known (for invitation).<br>
   * 
   * @param uniqueId
   * @param orgId
   * @param email
   */
  public MoxtraUser(String uniqueId, String orgId, String email) {
    this(uniqueId, orgId, email, email);
  }

  /**
   * User with only email known (for invitation).<br>
   * 
   * @param email
   */
  public MoxtraUser(String email) {
    this(null, null, email);
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
    String uniqueId = getUniqueId();
    if (uniqueId != null) {
      str.append(' ');
      str.append(uniqueId);
    }
    String orgId = getOrgId();
    if (orgId != null) {
      if (uniqueId == null) {
        str.append(' ');
      }
      str.append('+');
      str.append(orgId);
    }
    str.append(") ");
    if (id != null) {
      str.append(id);
    }
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

  /**
   * @return the pictureUri
   */
  public String getPictureUri() {
    return pictureUri;
  }

  /**
   * @return the uniqueId
   */
  public String getUniqueId() {
    return uniqueId;
  }

  /**
   * @return the orgId
   */
  public String getOrgId() {
    return orgId;
  }

  /**
   * Check if this user has the same identity as given (Unique ID + Org ID).
   * 
   * @param uniqueId
   * @param orgId
   * @return <code>true</code> if it is the same user, <code>false</code> otherwise
   */
  public boolean isSameIdentity(String uniqueId, String orgId) {
    if (this.uniqueId != null && this.uniqueId.equals(uniqueId)) {
      return isSameOrganization(orgId);
    }
    return false;
  }

  /**
   * Check if belongs to the sane organization as given (by Org ID).
   * 
   * @param orgId
   * @return <code>true</code> if it is the same organization, <code>false</code> otherwise
   */
  public boolean isSameOrganization(String orgId) {
    if (this.orgId != null && orgId != null && this.orgId.equals(orgId)) {
      return true;
    } else if (this.orgId == null && orgId == null) {
      return true;
    }
    return false;
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
    if (isSameIdentity(user.getUniqueId(), user.getOrgId())) {
      return true;
    }
    String email = getEmail();
    if (email != null && email.equals(user.getEmail())) {
      return true;
    }
    return false;
  }

  /**
   * Set user status in binder. Actual for binder members only.
   * 
   * @param status
   */
  protected void setStatus(String status) {
    this.status = status;
  }

}
