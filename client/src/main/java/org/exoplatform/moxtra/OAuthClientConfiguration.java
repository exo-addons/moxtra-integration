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

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.container.configuration.ConfigurationException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: OAuthClientConfiguration.java 00000 Feb 27, 2015 pnedonosko $
 * 
 */
public class OAuthClientConfiguration extends BaseComponentPlugin {

  public static final String          CONFIG_CLIENT_ID            = "client-id";

  public static final String          CONFIG_CLIENT_SECRET        = "client-secret";

  public static final String          CONFIG_CLIENT_HOST          = "client-host";

  public static final String          CONFIG_CLIENT_SCHEMA        = "client-schema";

  public static final String          CONFIG_CLIENT_AUTH_METHOD   = "client-auth-method";

  public static final String          CONFIG_CLIENT_ORGID         = "client-orgid";

  public static final String          CLIENT_AUTH_METHOD_OAUTH2   = "OAUTH2";

  public static final String          CLIENT_AUTH_METHOD_UNIQUEID = "SSO-UNIQUEID";

  public static final String          CLIENT_AUTH_METHOD_SAML     = "SSO-SAML-BEARER";

  public static final String          CLIENT_DEFAULT_ORGID        = "__eXo";

  protected static final Log          LOG                         = ExoLogger.getLogger(OAuthClientConfiguration.class);

  protected final Map<String, String> config;

  protected final String              clientId;

  protected final String              clientSecret;

  protected final String              clientHost;

  protected final String              clientSchema;

  protected final String              clientAuthMethod;

  protected final String              clientOrgId;

  /**
   * 
   */
  public OAuthClientConfiguration(InitParams params) throws ConfigurationException {
    PropertiesParam param = params.getPropertiesParam("client-configuration");

    if (param != null) {
      config = Collections.unmodifiableMap(param.getProperties());
    } else {
      throw new ConfigurationException("Property parameters drive-configuration required.");
    }

    String clientId = config.get(CONFIG_CLIENT_ID);
    if (clientId == null || (clientId = clientId.trim()).length() == 0) {
      throw new ConfigurationException("Property parameter " + CONFIG_CLIENT_ID + " required.");
    }
    this.clientId = clientId;

    String clientSecret = config.get(CONFIG_CLIENT_SECRET);
    if (clientSecret == null || (clientSecret = clientSecret.trim()).length() == 0) {
      throw new ConfigurationException("Property parameter " + CONFIG_CLIENT_SECRET + " required.");
    }
    this.clientSecret = clientSecret;

    String clientAuthMethod = config.get(CONFIG_CLIENT_AUTH_METHOD);
    String clientOrgId;
    if (clientAuthMethod == null || (clientAuthMethod = clientAuthMethod.trim()).length() == 0) {
      clientAuthMethod = CLIENT_AUTH_METHOD_UNIQUEID;
      clientOrgId = null;
      LOG.info("Using default authentication method for Moxtra clients: " + clientAuthMethod);
    } else if (clientAuthMethod.equals(CLIENT_AUTH_METHOD_UNIQUEID)
        || clientAuthMethod.equals(CLIENT_AUTH_METHOD_SAML)) {
      clientOrgId = config.get(CONFIG_CLIENT_ORGID);
      if (clientOrgId == null || (clientOrgId = clientOrgId.trim()).length() == 0) {
        clientOrgId = CLIENT_DEFAULT_ORGID;
        LOG.info("Using SSO for Moxtra clients: " + clientAuthMethod + " with single organization team.");
      } else {
        LOG.info("Using SSO for Moxtra clients: " + clientAuthMethod + " with " + clientOrgId
            + " organization team.");
      }
    } else {
      if (!clientAuthMethod.equals(CLIENT_AUTH_METHOD_UNIQUEID)) {
        LOG.info("Unknown authentication method configured for Moxtra clients: " + clientAuthMethod
            + ". Will use default one: " + CLIENT_AUTH_METHOD_UNIQUEID);
        clientAuthMethod = CLIENT_AUTH_METHOD_UNIQUEID;
      }
      clientOrgId = null;
    }
    this.clientAuthMethod = clientAuthMethod;
    this.clientOrgId = clientOrgId;

    String schema = config.get(CONFIG_CLIENT_SCHEMA);
    if (schema == null || (schema = schema.trim()).length() == 0) {
      schema = "http";
    }
    this.clientSchema = schema;

    String host = config.get(CONFIG_CLIENT_HOST);
    if (host != null && (host = host.trim()).length() > 0) {
      this.clientHost = host;
    } else {
      try {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (host == null && interfaces.hasMoreElements()) {
          NetworkInterface nic = interfaces.nextElement();
          Enumeration<InetAddress> addresses = nic.getInetAddresses();
          while (host == null && addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();
            if (!address.isLoopbackAddress()) {
              host = address.getHostName();
            }
          }
        }
      } catch (SocketException e) {
        // cannot get net interfaces
      }

      if (host == null) {
        try {
          host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
          host = "localhost";
        }
      }

      this.clientHost = host;
      LOG.warn("Configuration of " + CONFIG_CLIENT_HOST + " is not set, will use " + host);
    }
  }

  /**
   * @return the clientId
   */
  public String getClientId() {
    return clientId;
  }

  /**
   * @return the clientSecret
   */
  public String getClientSecret() {
    return clientSecret;
  }

  /**
   * @return the clientAuthMethod
   */
  public String getClientAuthMethod() {
    return clientAuthMethod;
  }

  /**
   * @return the clientOrgId
   */
  public String getClientOrgId() {
    return clientOrgId;
  }

  /**
   * @return the serverHost
   */
  public String getClientHost() {
    return clientHost;
  }

  /**
   * @return the serverSchema
   */
  public String getClientSchema() {
    return clientSchema;
  }

}
