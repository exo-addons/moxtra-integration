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
package org.exoplatform.moxtra.oauth2;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * OAuth2 token data (access and refresh tokens).<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: UserToken.java 00000 Mar 25, 2015 pnedonosko $
 * 
 */
public class AccessToken {

  private String                          accessToken;

  private String                          refreshToken;

  private Calendar                        expirationTime;

  private String                          scope;

  private Set<AccessTokenRefreshListener> listeners = new LinkedHashSet<AccessTokenRefreshListener>();

  /**
   * Create empty not initialized token object. Use {@link #init(String, String, long, String)} to set access
   * data to the object.
   * 
   * @return {@link AccessToken}
   */
  public static AccessToken newToken() {
    return new AccessToken();
  }

  /**
   * Init token from existing data.
   * 
   * @param accessToken
   * @param refreshToken
   * @param expiresInSeconds
   * @param scope
   * @return {@link AccessToken}
   */
  public static AccessToken createToken(String accessToken,
                                        String refreshToken,
                                        Long expiresInSeconds,
                                        String scope) {
    AccessToken token = new AccessToken();
    token.init(accessToken, refreshToken, expiresInSeconds, scope);
    return token;
  }

  /**
   * Create a copy of existing token object but without registered in it listeners.
   * 
   * @param otherToken {@link AccessToken}
   * @return {@link AccessToken}
   */
  public static AccessToken copyToken(AccessToken otherToken) {
    AccessToken token = new AccessToken();
    token.merge(otherToken);
    return token;
  }

  /**
   * Create empty store.
   * 
   * @param id
   */
  protected AccessToken() {
  }

  /**
   * Add listener to this access token. Optionally fire this listener immediately.
   * 
   * @param listener {@link AccessTokenRefreshListener}
   * @param fireNow boolean if <code>true</code> fire given listener on this access token
   */
  public void addListener(AccessTokenRefreshListener listener, boolean fireNow) {
    this.listeners.add(listener);
    if (fireNow) {
      listener.onTokenRefresh(this);
    }
  }

  public void removeListener(AccessTokenRefreshListener listener) {
    this.listeners.remove(listener);
  }

  /**
   * @return the accessToken
   */
  public String getAccessToken() {
    return accessToken;
  }

  /**
   * @return the refreshToken, can be <code>null</code>
   */
  public String getRefreshToken() {
    return refreshToken;
  }

  /**
   * @return the expirationTime
   */
  public Calendar getExpirationTime() {
    return expirationTime;
  }

  /**
   * @return the scope, can be <code>null</code>
   */
  public String getScope() {
    return scope;
  }

  /**
   * Import OAuth2 tokens from a new {@link AccessToken} and unregister listeners of that instance.
   * 
   * @param newToken {@link AccessToken}
   * @throws CloudDriveException
   */
  public void merge(AccessToken newToken) {
    if (newToken.isInitialized()) {
      newToken.removeListeners();
      load(newToken.getAccessToken(),
           newToken.getRefreshToken(),
           newToken.getExpirationTime(),
           newToken.getScope());
      fireListeners();
    }
  }

  /**
   * Set new OAuth2 token data and fire registered listeners.
   * 
   * @param accessToken
   * @param refreshToken
   * @param expiresInSeconds {@link Long} when token expires in seconds
   * @param scope
   */
  public void init(String accessToken, String refreshToken, Long expiresInSeconds, String scope) {
    if (accessToken == null) {
      throw new NullPointerException("Not null accessToken required");
    }

    if (expiresInSeconds == null) {
      throw new NullPointerException("Not null expiresInSeconds required");
    }

    Calendar expirationTime = Calendar.getInstance(); // time now
    // add time to expiration:
    // expiresIn is in seconds, decrease it a bit to decrease probability of unexpected expiration on Moxtra
    int expiresIn = expiresInSeconds.intValue();
    expirationTime.add(Calendar.SECOND, expiresIn > 120 ? expiresIn - 120 : expiresIn);
    load(accessToken, refreshToken, expirationTime, scope);
    fireListeners();
  }

  /**
   * Load OAuth2 token from given data.
   * 
   * @param accessToken
   * @param refreshToken
   * @param expirationTime {@link Calendar}
   * @param scope
   */
  public void load(String accessToken, String refreshToken, Calendar expirationTime, String scope) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.scope = scope;
    this.expirationTime = expirationTime;
  }

  /**
   * Return <code>true</code> if access token expired and should be renewed.
   * 
   * @return <code>true</code> if expired, <code>false</code> otherwise
   */
  public boolean isExpired() {
    return isExpired(Calendar.getInstance());
  }

  /**
   * Return <code>true</code> if access token expired to given date.
   *
   * @param toDate {@link Calendar} some date to check if this token will be expired after it
   * @return <code>true</code> if expired, <code>false</code> otherwise
   */
  public boolean isExpired(Calendar toDate) {
    return expirationTime.after(toDate);
  }

  /**
   * Return <code>true</code> if this instance of {@link AccessToken} initialized with access token.
   * 
   * @return <code>true</code> if initialized, <code>false</code> otherwise
   */
  public boolean isInitialized() {
    return accessToken != null;
  }

  // ********* internals **********

  protected void removeListeners() {
    listeners.clear();
  }

  protected void fireListeners() {
    for (AccessTokenRefreshListener listener : listeners) {
      listener.onTokenRefresh(this);
    }
  }

}
