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
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraPage.java 00000 May 8, 2015 pnedonosko $
 * 
 */
public class MoxtraFeed {

  public static final String OBJECT_TYPE_BINDER     = "binder".intern();

  public static final String OBJECT_TYPE_PERSON     = "person".intern();

  public static final String OBJECT_TYPE_COMMENT    = "comment".intern();

  public static final String OBJECT_TYPE_PAGE       = "page".intern();

  public static final String OBJECT_TYPE_FILE       = "file".intern();

  public static final String OBJECT_TYPE_ANNOTATION = "annotation".intern();

  public static final String VERB_POST              = "post".intern();

  public static final String VERB_CREATE            = "create".intern();

  public static final String VERB_TAG               = "tag".intern();

  public static final String VERB_UPLOAD            = "upload".intern();
  
  public static final String VERB_UPDATE            = "update".intern();
  
  public static final String VERB_DELETE            = "delete".intern();

  protected final String     verb;

  protected final String     generatorId;

  protected final String     actorId;

  protected final String     actorType;

  protected final String     objectId;

  protected final String     objectType;

  protected final String     objectUrl;

  protected final String     objectDisplayName;

  protected final String     objectMimeType;

  protected final String     objectContent;

  protected final String     targetId;

  protected final String     targetType;

  protected final String     targetUrl;

  protected final Date       publishedTime;

  public MoxtraFeed(String verb,
                    String generatorId,
                    String actorId,
                    String actorType,
                    String objectId,
                    String objectType,
                    String objectUrl,
                    String objectDisplayName,
                    String objectMimeType,
                    String objectContent,
                    String targetId,
                    String targetType,
                    String targetUrl,
                    Date publishedTime) {
    this.verb = verb;
    this.publishedTime = publishedTime;
    this.generatorId = generatorId;
    this.actorId = actorId;
    this.actorType = actorType;
    this.objectId = objectId;
    this.objectType = objectType;
    this.objectUrl = objectUrl;
    this.objectDisplayName = objectDisplayName;
    this.objectMimeType = objectMimeType;
    this.objectContent = objectContent;
    this.targetId = targetId;
    this.targetType = targetType;
    this.targetUrl = targetUrl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof MoxtraFeed) {
      MoxtraFeed other = (MoxtraFeed) obj;
      return isSimilar(other) && this.actorId.equals(other.actorId)
          && this.publishedTime.equals(other.publishedTime) && this.generatorId.equals(other.generatorId);
    }
    return false;
  }

  /**
   * Check if given feed is similar to this one by verb, object and target. Actor and time may differ.
   * 
   * @param other {@link MoxtraFeed}
   * @return <code>true</code> if similar, <code>false</code> otherwise
   */
  public boolean isSimilar(MoxtraFeed other) {
    return this.verb.equals(other.verb) && isSameObject(other);
  }

  /**
   * Check if given feed has same object of the target as given.
   * 
   * @param other {@link MoxtraFeed}
   * @return <code>true</code> if same object, <code>false</code> otherwise
   */
  public boolean isSameObject(MoxtraFeed other) {
    return this.targetId.equals(other.targetId)
        && ((this.objectId != null && other.objectId != null && this.objectId.equals(other.objectId)) || this.objectId == null
            && other.objectId == null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append(verb);
    str.append(' ');
    str.append(objectType);
    if (objectId != null) {
      str.append(" (");
      str.append(objectId);
      str.append(")");
    }
    str.append(" in ");
    str.append(targetType);
    str.append(" (");
    str.append(targetId);
    str.append(")");
    str.append(" by ");
    str.append(actorType);
    str.append(" (");
    str.append(actorId);
    str.append(")");
    return str.toString();
  }

  /**
   * @return the verb
   */
  public String getVerb() {
    return verb;
  }

  /**
   * @return the generatorId
   */
  public String getGeneratorId() {
    return generatorId;
  }

  /**
   * @return the actorId
   */
  public String getActorId() {
    return actorId;
  }

  /**
   * @return the actorType
   */
  public String getActorType() {
    return actorType;
  }

  /**
   * @return the objectId
   */
  public String getObjectId() {
    return objectId;
  }

  /**
   * @return the objectType
   */
  public String getObjectType() {
    return objectType;
  }

  /**
   * @return the objectUrl
   */
  public String getObjectUrl() {
    return objectUrl;
  }

  /**
   * @return the objectDisplayName
   */
  public String getObjectDisplayName() {
    return objectDisplayName;
  }

  /**
   * @return the objectMimeType
   */
  public String getObjectMimeType() {
    return objectMimeType;
  }

  /**
   * @return the objectContent
   */
  public String getObjectContent() {
    return objectContent;
  }

  /**
   * @return the targetId
   */
  public String getTargetId() {
    return targetId;
  }

  /**
   * @return the targetType
   */
  public String getTargetType() {
    return targetType;
  }

  /**
   * @return the targetUrl
   */
  public String getTargetUrl() {
    return targetUrl;
  }

  /**
   * @return the publishedTime
   */
  public Date getPublishedTime() {
    return publishedTime;
  }

  // ******** internals *********

}
