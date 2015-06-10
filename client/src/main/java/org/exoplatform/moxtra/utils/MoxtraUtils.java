
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
package org.exoplatform.moxtra.utils;

import org.exoplatform.services.organization.User;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraUtils.java 00000 Jun 10, 2015 pnedonosko $
 * 
 */
public class MoxtraUtils {
  
  /**
   * Find most viable user's full name.  
   * 
   * @param user {@link User} organization user
   * @return
   */
  public static String fullName(User user) {
    String name = user.getDisplayName();
    if (name == null || name.trim().length() == 0) {
      name = user.getFirstName() + " " + user.getLastName();
      if (name.trim().length() == 0) {
        name = user.getEmail();
        if (name == null || name.trim().length() == 0) {
          name = user.getUserName();
        }
      }
    }
    return name;
  }
  
  /**
   * Return meaningful value of some text in the string. If string is empty then <code>null</code> will be returned.  
   * 
   * @param value input {@link String}
   * @return textual value or <code>null</code>
   */
  public static String cleanValue(String value) {
    if (value != null && value.trim().length() == 0) {
      return null;
    }
    return value;
  }

}
