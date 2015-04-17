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

import org.exoplatform.moxtra.MoxtraException;

/**
 * Meet session was deleted in Moxtra.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MeetDeletedException.java 00000 Apr 16, 2015 pnedonosko $
 * 
 */
public class MeetDeletedException extends MoxtraException {

  /**
   * 
   */
  private static final long serialVersionUID = -1275453882202078519L;

  /**
   * @param message
   */
  public MeetDeletedException(String message) {
    super(message);
  }

  public MeetDeletedException(String message, Throwable cause) {
    super(message, cause);
  }

}
