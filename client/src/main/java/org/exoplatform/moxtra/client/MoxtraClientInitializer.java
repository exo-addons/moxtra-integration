
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

import org.exoplatform.moxtra.oauth2.AccessToken;

/**
 * Facade to access to internals of {@link MoxtraClient}.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraClientStore.java 00000 Mar 25, 2015 pnedonosko $
 * 
 */
public class MoxtraClientInitializer {
  
  protected final MoxtraClient client;
  
  public MoxtraClientInitializer(MoxtraClient client) {
    this.client = client;
  }

  public void initClientToken(AccessToken token) {
    client.initOAuthToken(token);
  }
  
  public AccessToken getClientToken() {
    return client.getOAuthToken();
  }
}
