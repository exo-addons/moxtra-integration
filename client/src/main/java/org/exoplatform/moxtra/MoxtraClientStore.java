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
package org.exoplatform.moxtra;

import org.exoplatform.moxtra.client.MoxtraClient;
import org.exoplatform.moxtra.client.MoxtraMeet;
import org.exoplatform.moxtra.client.MoxtraUser;

/**
 * Store for {@link MoxtraClient} in eXo infrastructure. It can be added as a plugin to {@link MoxtraService}
 * and then the service will store and then load clients from this store.<br>
 * 
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraClientStore.java 00000 Mar 25, 2015 pnedonosko $
 * 
 */
public interface MoxtraClientStore {

  /**
   * Save given client data in the store. If client already found in the store this method will update its
   * data. All required listeners will be added to the client to track later updates and save them in this
   * store.
   * 
   * @param client {@link MoxtraClient}
   * @throws MoxtraStoreException
   */
  void save(MoxtraClient client) throws MoxtraStoreException;

  /**
   * Load stored client data into given client instance. If client data found in the store this method return
   * <code>true</code> and add required listeners to the client to track later updates and save them in the
   * store.
   * 
   * @param client {@link MoxtraClient}
   * @return boolean <code>true</code> if client data found and loaded successfully, <code>false</code>
   *         otherwise
   * @throws MoxtraStoreException
   */
  boolean load(MoxtraClient client) throws MoxtraStoreException;
  
  void saveUser(MoxtraClient client, MoxtraUser user) throws MoxtraStoreException;
  
  MoxtraUser readUser(MoxtraClient client) throws MoxtraStoreException;
  
  void saveMeet(MoxtraClient client, MoxtraMeet meet) throws MoxtraStoreException;
  
  MoxtraMeet readMeet(MoxtraClient client) throws MoxtraStoreException;

}
