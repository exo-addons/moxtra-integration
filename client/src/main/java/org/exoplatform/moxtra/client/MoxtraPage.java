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

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraPage.java 00000 May 8, 2015 pnedonosko $
 * 
 */
public class MoxtraPage {

  public static String      PAGE_TYPE_PDF        = "PAGE_TYPE_PDF".intern();

  public static String      PAGE_TYPE_IMAGE      = "PAGE_TYPE_IMAGE".intern();

  public static String      PAGE_TYPE_WHITEBOARD = "PAGE_TYPE_WHITEBOARD".intern();

  public static String      PAGE_TYPE_NOTE       = "PAGE_TYPE_NOTE".intern();

  public static String      PAGE_TYPE_WEB        = "PAGE_TYPE_WEB".intern();

  public static String      PAGE_TYPE_URL        = "PAGE_TYPE_URL".intern();

  public static String      PAGE_TYPE_AUDIO      = "PAGE_TYPE_AUDIO".intern();

  public static String      PAGE_TYPE_VIDEO      = "PAGE_TYPE_VIDEO".intern();

  public static Set<String> NOTE_PAGE_TYPES;

  public static Set<String> DRAW_PAGE_TYPES;

  static {
    Set<String> notePageTypes = new HashSet<String>();
    notePageTypes.add(PAGE_TYPE_NOTE);
    notePageTypes.add(PAGE_TYPE_WEB);
    NOTE_PAGE_TYPES = Collections.unmodifiableSet(notePageTypes);

    Set<String> drawPageTypes = new HashSet<String>();
    drawPageTypes.add(PAGE_TYPE_PDF);
    drawPageTypes.add(PAGE_TYPE_IMAGE);
    drawPageTypes.add(PAGE_TYPE_WHITEBOARD);
    DRAW_PAGE_TYPES = Collections.unmodifiableSet(drawPageTypes);
  }

  protected Long            id;

  protected Long            revision;

  protected Long            index;

  protected String          number;

  protected String          originalFileName;

  protected String          type;

  protected String          url;

  protected String          thumbnailUrl;

  protected String          backgroundUrl;

  protected Date            createdTime;

  protected Date            updatedTime;

  protected Date            creatingTime;

  /**
   * New page constructor.
   */
  public MoxtraPage() {
  }

  /**
   * Existing page constructor.
   */
  public MoxtraPage(Long id,
                    Long revision,
                    Long index,
                    String number,
                    String type,
                    String originalFileName,
                    String url,
                    String thumbnailUrl,
                    String backgroundUrl) {
    this.id = id;
    this.revision = revision;
    this.index = index;
    this.number = number;
    this.type = type;
    this.originalFileName = originalFileName;
    this.url = url;
    this.thumbnailUrl = thumbnailUrl;
    this.backgroundUrl = backgroundUrl;
  }

  /**
   * Local, existing or creating page constructor.
   */
  @Deprecated
  public MoxtraPage(String originalFileName) {
    this.originalFileName = originalFileName;
  }

  /**
   * Constructor of existing locally and creating in Moxtra page.
   */
  public MoxtraPage(String originalFileName, Date creatingTime) {
    this.originalFileName = originalFileName;
    this.creatingTime = creatingTime;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return originalFileName != null ? originalFileName + (index > 0 ? " (" + index : ")")
                                   : (url != null ? url : id + " (" + index + ")");
  }

  /**
   * @return the id
   */
  public Long getId() {
    return id;
  }

  /**
   * @return the revision
   */
  public Long getRevision() {
    return revision;
  }

  /**
   * @return the index
   */
  public Long getIndex() {
    return index;
  }

  /**
   * @return the number
   */
  public String getNumber() {
    return number;
  }

  /**
   * @return the originalFileName
   */
  public String getOriginalFileName() {
    return originalFileName;
  }

  /**
   * @return the type
   */
  public String getType() {
    return type;
  }

  /**
   * @return the url
   */
  public String getUrl() {
    return url;
  }

  /**
   * @return the thumbnailUrl
   */
  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  /**
   * @return the backgroundUrl
   */
  public String getBackgroundUrl() {
    return backgroundUrl;
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
   * Answers does the page already created in Moxtra or just creating (uploading) and need wait before use it.
   * 
   * @return <code>true</code> if page ready for use, <code>false</code> otherwise
   */
  public boolean isCreated() {
    return id != null;
  }

  // ******** internals *********

  /**
   * @param id the id to set
   */
  protected void setId(Long id) {
    this.id = id;
  }

  /**
   * @param revision the revision to set
   */
  protected void setRevision(Long revision) {
    this.revision = revision;
  }

  /**
   * @param index the index to set
   */
  protected void setIndex(Long index) {
    this.index = index;
  }

  /**
   * @param number the number to set
   */
  protected void setNumber(String number) {
    this.number = number;
  }

  /**
   * @param originalFileName the originalFileName to set
   */
  protected void setOriginalFileName(String originalFileName) {
    this.originalFileName = originalFileName;
  }

  /**
   * @param type the type to set
   */
  protected void setType(String type) {
    this.type = type;
  }

  /**
   * @param url the url to set
   */
  protected void setUrl(String url) {
    this.url = url;
  }

  /**
   * @param thumbnailUrl the thumbnailUrl to set
   */
  protected void setThumbnailUrl(String thumbnailUrl) {
    this.thumbnailUrl = thumbnailUrl;
  }

  /**
   * @param backgroundUrl the backgroundUrl to set
   */
  protected void setBackgroundUrl(String backgroundUrl) {
    this.backgroundUrl = backgroundUrl;
  }

  /**
   * @param createdTime the createdTime to set
   */
  protected void setCreatedTime(Date createdTime) {
    this.createdTime = createdTime;
  }

  /**
   * @param updatedTime the updatedTime to set
   */
  protected void setUpdatedTime(Date updatedTime) {
    this.updatedTime = updatedTime;
  }

}
