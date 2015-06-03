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
package org.exoplatform.moxtra.commons;

import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.MoxtraService;
import org.exoplatform.moxtra.client.MoxtraAuthenticationException;
import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraUser;
import org.exoplatform.services.security.ConversationState;

import java.util.Collection;
import java.util.List;

/**
 * Base class for building container components and services used Moxtra client.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: BaseMoxtraService.java 00000 May 18, 2015 pnedonosko $
 * 
 */
public abstract class BaseMoxtraService {

  public static final String    MOXTRA_CURRENT_USER = "moxtra.currentUser";

  protected final MoxtraService moxtra;

  /**
   * 
   */
  public BaseMoxtraService(MoxtraService moxtraService) {
    this.moxtra = moxtraService;
  }

  /**
   * Checks if current user is already authorized to access Moxtra services.
   * 
   * @return <code>true</code> if user is authorized to access Moxtra services, <code>false</code> otherwise
   */
  public boolean isAuthorized() {
    return moxtra.getClient().isAuthorized();
  }

  public String getOAuth2Link() throws OAuthSystemException {
    return moxtra.getClient().authorizer().authorizationLink();
  }

  /**
   * Current user in Moxtra.<br>
   * This user will be cached in current {@link ConversationState} attribute.
   * 
   * @return {@link MoxtraUser}.
   * @throws MoxtraException
   * 
   * @see {@link MoxtraClient#getCurrentUser(boolean)}
   */
  public MoxtraUser getUser() throws MoxtraException {
    return moxtra.getClient().getCurrentUser();
  }

  /**
   * Current user contacts in Moxtra.<br>
   * 
   * @return {@link Collection} of {@link MoxtraUser}.
   * @throws MoxtraException
   * @throws MoxtraAuthenticationException
   */
  public List<MoxtraUser> getContacts() throws MoxtraAuthenticationException, MoxtraException {
    return moxtra.getClient().getContacts(getUser());
  }

}
