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

import static org.exoplatform.moxtra.Moxtra.formatDate;
import static org.exoplatform.moxtra.Moxtra.getCalendar;
import static org.exoplatform.moxtra.Moxtra.getDate;
import static org.exoplatform.moxtra.Moxtra.parseDate;

import net.oauth.client.httpclient4.OAuthCredentials;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponseFactory;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthRuntimeException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.NotFoundException;
import org.exoplatform.moxtra.oauth2.AccessToken;
import org.exoplatform.moxtra.rest.OAuthCodeAuthenticator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.ws.frameworks.json.JsonParser;
import org.exoplatform.ws.frameworks.json.impl.JsonDefaultHandler;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.impl.JsonParserImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.MediaType;

/**
 * Created by The eXo Platform SAS
 * 
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MoxtraClient.java 00000 Feb 25, 2015 pnedonosko $
 * 
 */
public class MoxtraClient {

  public static final String     MOXTRA_USER_ME              = "me";

  public static final String     API_V1                      = "https://api.moxtra.com/";

  public static final String     API_OAUTH_AUTHORIZE         = API_V1 + "oauth/authorize";

  public static final String     API_USER                    = API_V1 + "{user_id}";

  public static final String     API_USER_CONTACTS           = API_USER + "/contacts";

  public static final String     API_USER_MEETS              = API_USER + "/meets";

  public static final String     API_MEETS_SCHEDULE          = API_V1 + "meets/schedule";

  public static final String     API_MEETS_SESSION           = API_V1 + "meets/{session_key}";

  public static final String     API_MEETS_INVITEUSER        = API_V1 + "meets/inviteuser";

  public static final String     API_MEETS_RECORDINGS        = API_V1 + "meets/recordings/{session_key}";

  public static final String     API_MEETS_STATUS            = API_V1 + "meets/status/{session_key}";

  public static final String     API_BINDER                  = API_V1 + "{binder_id}";

  public static final String     API_BINDER_INVITEUSER       = API_BINDER + "/inviteuser";

  public static final String     API_BINDER_REMOVEUSER       = API_BINDER + "/removeuser";

  public static final String     RESPONSE_SUCCESS            = "RESPONSE_SUCCESS";

  public static final String     REQUEST_CONTENT_TYPE_JSON   = "application/json".intern();

  public static final String     REQUEST_CONTENT_TYPE_BINARY = "application/octet-stream".intern();

  public static final String     REQUEST_ACCEPT              = "Accept".intern();

  public static final String     RESPONSE_ALLOW              = "Allow".intern();

  public static final int        GET_MEETS_LIST_MAX_DAYS     = 30;

  public static final int        DAY_MILLISECONDS            = 86400000;

  public static final String     RESPONSE_ERROR_NOT_FOUND    = "RESPONSE_ERROR_NOT_FOUND".intern();

  protected static final Pattern HTML_ERROR_EXTRACT          = Pattern.compile("<u>(.+?)</u>");

  protected static final String  EMPTY                       = "".intern();

  protected static final Log     LOG                         = ExoLogger.getLogger(MoxtraClient.class);

  protected class RESTError {
    final String code;

    final String message;

    protected RESTError(String code, String message) {
      super();
      this.code = code;
      this.message = message;
    }
  }

  protected class HeaderValue {
    protected final String              value;

    protected final Map<String, String> values;

    protected HeaderValue(String value) {
      this.value = value;
      this.values = null;
    }

    protected HeaderValue(Map<String, String> values) {
      this.values = values;
      this.value = null;
    }

    protected boolean isSingleValue() {
      return this.value != null;
    }
  }

  protected class AuthProblemException extends OAuthProblemException {

    private static final long serialVersionUID = 4030969137768681190L;

    protected final Throwable cause;

    protected AuthProblemException(String error, Throwable cause) {
      super(error);
      this.cause = cause;
    }

    protected AuthProblemException(String error) {
      this(error, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Throwable getCause() {
      return cause != null ? cause : super.getCause();
    }
  }

  protected class RESTResponse extends OAuthClientResponse {

    protected JsonValue   jsonValue;

    protected String      stringValue;

    protected InputStream streamValue;

    protected Header[]    headers;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setContentType(String contentType) {
      this.contentType = contentType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setResponseCode(int responseCode) {
      this.responseCode = responseCode;
    }

    public int getResponseCode() {
      return responseCode;
    }

    public String getContentType() {
      return contentType;
    }

    public JsonValue getValue() {
      return jsonValue;
    }

    public String getText() {
      return stringValue;
    }

    public InputStream getInputStream() {
      return streamValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init(String body, String contentType, int responseCode) throws OAuthProblemException {
      this.setContentType(contentType);
      this.setResponseCode(responseCode);
    }

    protected void setBody(JsonValue value) {
      if (stringValue == null && streamValue == null) {
        this.jsonValue = value;
      } else {
        throw new IllegalStateException("Body already set");
      }
    }

    protected void setBody(String value) {
      if (jsonValue == null && streamValue == null) {
        this.stringValue = value;
      } else {
        throw new IllegalStateException("Body already set");
      }
    }

    protected void setBody(InputStream value) {
      if (jsonValue == null && stringValue == null) {
        this.streamValue = value;
      } else {
        throw new IllegalStateException("Body already set");
      }
    }

    protected void setHeaders(Header[] headers) {
      this.headers = headers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getParam(String param) {
      for (Header h : headers) {
        if (h.getName().equals(param)) {
          String hv = h.getValue();
          if (hv != null) {
            return hv;
          } else {
            // construct complex header value in single string - TODO do we really need it?
            StringBuilder pv = new StringBuilder();
            HeaderElement[] elems = h.getElements();
            for (int i = 0; i < elems.length; i++) {
              HeaderElement elem = elems[i];
              pv.append(elem.getName());
              pv.append('=');
              pv.append(elem.getValue());
              if (elem.getParameterCount() > 0) {
                pv.append(';');
                NameValuePair[] params = elem.getParameters();
                for (int pi = 0; pi < params.length; pi++) {
                  NameValuePair nvp = params[pi];
                  pv.append(nvp.getName());
                  pv.append("=\"");
                  pv.append(nvp.getValue());
                  pv.append('"');
                  if (pi < params.length - 1) {
                    pv.append(';');
                  }
                }
              }
              if (i < elems.length - 1) {
                pv.append(',');
              }
            }
            if (pv.length() > 0) {
              return pv.toString();
            }
          }
        }
      }

      return super.getParam(param);
    }

    Header getHeader(String name) {
      for (Header h : headers) {
        if (h.getName().equals(name)) {
          return h;
        }
      }
      return null;
    }
  }

  /**
   * Oltu HTTP client implementation using Apache HTTP Client.
   */
  protected class RESTClient implements org.apache.oltu.oauth2.client.HttpClient {

    public void shutdown() {
      if (httpClient != null) {
        ClientConnectionManager connectionManager = httpClient.getConnectionManager();
        if (connectionManager != null) {
          connectionManager.shutdown();
        }
      }
    }

    @SuppressWarnings("unchecked")
    public <T extends OAuthClientResponse> T execute(OAuthClientRequest request,
                                                     Map<String, String> headers,
                                                     String requestMethod,
                                                     Class<T> responseClass) throws OAuthSystemException,
                                                                            OAuthProblemException {
      try {
        URI location = new URI(request.getLocationUri());
        HttpRequestBase req;
        if (!OAuthUtils.isEmpty(requestMethod)) {
          if (OAuth.HttpMethod.POST.equals(requestMethod)) {
            HttpPost post = new HttpPost(location);
            HttpEntity entity = new StringEntity(request.getBody() == null ? EMPTY : request.getBody());
            post.setEntity(entity);
            req = post;
          } else if (OAuth.HttpMethod.PUT.equals(requestMethod)) {
            HttpPut put = new HttpPut(location);
            HttpEntity entity = new StringEntity(request.getBody() == null ? EMPTY : request.getBody());
            put.setEntity(entity);
            req = put;
          } else if (OAuth.HttpMethod.DELETE.equals(requestMethod)) {
            req = new HttpDelete(location);
          } else if (HttpHead.METHOD_NAME.equals(requestMethod)) {
            req = new HttpHead(location);
          } else if (HttpOptions.METHOD_NAME.equals(requestMethod)) {
            req = new HttpOptions(location);
          } else {
            // GET otherwise assumed
            req = new HttpGet(location);
          }
        } else {
          // GET by default
          req = new HttpGet(location);
        }

        if (headers != null && !headers.isEmpty()) {
          for (Map.Entry<String, String> header : headers.entrySet()) {
            req.setHeader(header.getKey(), header.getValue());
          }
        }
        if (request.getHeaders() != null) {
          for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            req.setHeader(header.getKey(), header.getValue());
          }
        }
        HttpResponse response = httpClient.execute(req);
        HttpEntity entity = response.getEntity();

        String contentType;
        Header contentTypeHeader = entity.getContentType();
        if (contentTypeHeader != null) {
          contentType = contentTypeHeader.getValue();
        } else {
          contentType = REQUEST_CONTENT_TYPE_BINARY;
        }

        int responseCode = response.getStatusLine().getStatusCode();

        boolean isRestReponse = RESTResponse.class.isAssignableFrom(responseClass);

        if (LOG.isDebugEnabled()) {
          String url = location.toASCIIString();
          int i = url.indexOf("?access_token=");
          if (i < 0) {
            i = url.indexOf("access_token=");
          }
          if (i > 0) {
            url = url.substring(0, i);
          }
          LOG.debug("Moxtra " + requestMethod + " " + url + " > " + responseCode + " " + contentType + " "
              + entity.getContentLength() + "bytes REST:" + isRestReponse);
        }

        T clientResp;
        if (isRestReponse) {
          RESTResponse restResp = new RESTResponse();
          restResp.setResponseCode(responseCode);
          restResp.setHeaders(response.getAllHeaders());
          restResp.setContentType(contentType);
          if (entity != null) {
            if (contentType.startsWith(REQUEST_CONTENT_TYPE_JSON)) {
              restResp.setBody(readJson(response));
            } else if (contentType.startsWith("text/")) {
              restResp.setBody(EntityUtils.toString(entity));
            } else {
              restResp.setBody(entity.getContent());
            }
          }
          clientResp = (T) restResp;
        } else {
          // ensure not null body always
          String responseBody = entity != null ? responseBody = EntityUtils.toString(entity) : EMPTY;
          responseBody = responseBody != null ? responseBody : EMPTY;
          try {
            clientResp = OAuthClientResponseFactory.createCustomResponse(responseBody,
                                                                         contentType,
                                                                         responseCode,
                                                                         responseClass);
          } catch (OAuthProblemException e) {
            // Here we can get the response entity and read extra data about the error
            if (responseBody != EMPTY) {
              if (contentType.startsWith(MediaType.APPLICATION_JSON)) {
                // try read as JSON
                try {
                  JsonParser jsonParser = new JsonParserImpl();
                  JsonDefaultHandler handler = new JsonDefaultHandler();
                  jsonParser.parse(new StringReader(responseBody), handler);
                  JsonValue responseJson = handler.getJsonObject();
                  String details = readOAuthProblem(responseJson);
                  if (details != null) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("Authorization problem (" + details
                          + ")\r\n====== Moxtra response JSON ======\r\n" + responseJson.toString()
                          + "\r\n=========================\r\n", e);
                    }

                    AuthProblemException cause = new AuthProblemException(details);
                    AuthProblemException newE = new AuthProblemException(e.getMessage(), cause);
                    newE.setStackTrace(e.getStackTrace());
                    newE.setRedirectUri(e.getRedirectUri());
                    newE.scope(e.getScope());
                    newE.state(e.getState());
                    // copy all ex parameters
                    for (Map.Entry<String, String> pe : e.getParameters().entrySet()) {
                      newE.setParameter(pe.getKey(), pe.getValue());
                    }
                    throw newE;
                  }
                } catch (JsonException jsone) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot read errouneus response as JSON", e);
                  }
                }
              }
            }
            throw e;
          }
        }

        return clientResp;
      } catch (IOException e) {
        throw new OAuthSystemException(e);
      } catch (URISyntaxException e) {
        throw new OAuthSystemException(e);
      } catch (JsonException e) {
        throw new OAuthSystemException(e);
      } catch (IllegalStateException e) {
        throw new OAuthRuntimeException(e);
      }
    }

    protected JsonValue readJson(HttpResponse resp) throws JsonException, IllegalStateException, IOException {
      HttpEntity entity = resp.getEntity();
      Header contentType = entity.getContentType();
      if (contentType != null && contentType.getValue() != null
          && contentType.getValue().startsWith(MediaType.APPLICATION_JSON)) {
        InputStream content = entity.getContent();
        JsonParser jsonParser = new JsonParserImpl();
        JsonDefaultHandler handler = new JsonDefaultHandler();
        jsonParser.parse(new InputStreamReader(content), handler);
        return handler.getJsonObject();
      } else {
        throw new JsonException("Not JSON content");
      }
    }

    protected String readOAuthProblem(JsonValue json) {
      JsonValue stackTrace = json.getElement("stackTrace");
      if (stackTrace != null) {
        JsonValue errorMsg = json.getElement("message");
        if (errorMsg != null) {
          return errorMsg.getStringValue();
        }
      }
      return null;
    }
  }

  /**
   * Builder-style helper for Moxtra authorization via OAuth2.
   */
  public class OAuth2Authorizer {
    protected String redirectLink;

    /**
     * Create authorization link using default redirect link handled by {@link OAuthCodeAuthenticator} REST
     * service.
     * 
     * @return {@link String}
     * @throws OAuthSystemException
     * @see {@link OAuthCodeAuthenticator}
     */
    public String authorizationLink() throws OAuthSystemException {
      StringBuilder redirectLink = new StringBuilder();
      redirectLink.append(oAuthClientSchema);
      redirectLink.append("://");
      redirectLink.append(oAuthClientHost);
      redirectLink.append('/');
      redirectLink.append(PortalContainer.getCurrentRestContextName());
      redirectLink.append("/moxtra/login");

      return authorizationLink(redirectLink.toString());
    }

    /**
     * Create authorization link using given redirect link.
     * 
     * @param redirectLink {@link String}
     * @return {@link String}
     * @throws OAuthSystemException
     */
    public String authorizationLink(String redirectLink) throws OAuthSystemException {
      this.redirectLink = redirectLink;
      OAuthClientRequest request = OAuthClientRequest.authorizationLocation(API_OAUTH_AUTHORIZE)
                                                     .setClientId(oAuthClientId)
                                                     .setResponseType(OAuth.OAUTH_CODE)
                                                     .setRedirectURI(redirectLink)
                                                     .buildQueryMessage();
      return request.getLocationUri();
    }

    public MoxtraClient authorize(String code) throws OAuthSystemException, OAuthProblemException {
      if (redirectLink != null) {
        OAuthClientRequest request = OAuthClientRequest.tokenLocation("https://api.moxtra.com/oauth/token")
                                                       .setGrantType(GrantType.AUTHORIZATION_CODE)
                                                       .setClientId(oAuthClientId)
                                                       .setClientSecret(oAuthClientSecret)
                                                       .setRedirectURI(redirectLink)
                                                       .setCode(code)
                                                       .buildQueryMessage();

        // FYI Content-Type will be set to OAuth.ContentType.URL_ENCODED in oAuthClient.accessToken()
        // request.setHeader(OAuth.HeaderType.CONTENT_TYPE, REQUEST_CONTENT_TYPE_JSON);
        // we want any request in JSON, even errors
        request.setHeader(REQUEST_ACCEPT, REQUEST_CONTENT_TYPE_JSON + "; q=1.0, text/*; q=0.9");

        OAuthAccessTokenResponse resp = oAuthClient.accessToken(request, OAuth.HttpMethod.POST);

        initOAuthToken(AccessToken.createToken(resp.getAccessToken(),
                                               resp.getRefreshToken(),
                                               resp.getExpiresIn(),
                                               resp.getScope()));

        return MoxtraClient.this;
      } else {
        throw new OAuthSystemException("Redirect link required");
      }
    }

    public MoxtraClient refresh() throws OAuthSystemException, OAuthProblemException {
      String refreshToken = getOAuthToken().getRefreshToken();
      if (refreshToken != null) {
        OAuthClientRequest request = OAuthClientRequest.tokenLocation("https://api.moxtra.com/oauth/token")
                                                       .setGrantType(GrantType.REFRESH_TOKEN)
                                                       .setClientId(oAuthClientId)
                                                       .setClientSecret(oAuthClientSecret)
                                                       .setRefreshToken(refreshToken)
                                                       .buildQueryMessage();

        // FYI Content-Type will be set to OAuth.ContentType.URL_ENCODED in oAuthClient.accessToken()
        // request.setHeader(OAuth.HeaderType.CONTENT_TYPE, REQUEST_CONTENT_TYPE_JSON);
        // we want any request in JSON, even errors
        request.setHeader(REQUEST_ACCEPT, REQUEST_CONTENT_TYPE_JSON + "; q=1.0, text/*; q=0.9");

        OAuthAccessTokenResponse resp = oAuthClient.accessToken(request, OAuth.HttpMethod.POST);

        initOAuthToken(AccessToken.createToken(resp.getAccessToken(),
                                               resp.getRefreshToken(),
                                               resp.getExpiresIn(),
                                               resp.getScope()));

        return MoxtraClient.this;
      } else {
        throw new OAuthSystemException("Refresh token required");
      }
    }

    /**
     * @return the redirectLink
     */
    public String getRedirectLink() {
      return redirectLink;
    }
  }

  protected final HttpClient    httpClient;

  protected final OAuthClient   oAuthClient;

  protected final HttpContext   context;

  protected final String        oAuthClientId, oAuthClientSecret;

  protected final String        oAuthClientSchema, oAuthClientHost;

  protected final Lock          authLock   = new ReentrantLock();

  protected final ReadWriteLock accessLock = new ReentrantReadWriteLock();

  protected AccessToken         oAuthToken = AccessToken.newToken();      // unauthorized by default

  protected OAuth2Authorizer    authorizer;

  /**
   * Moxtra client using OAuth2 authentication.
   * 
   * @throws MoxtraConfigurationException
   */
  public MoxtraClient(String oauthClientId,
                      String oauthClientSecret,
                      String oauthClientSchema,
                      String oauthClientHost) {
    this.oAuthClientId = oauthClientId;
    this.oAuthClientSecret = oauthClientSecret;

    this.oAuthClientSchema = oauthClientSchema;
    this.oAuthClientHost = oauthClientHost;

    SchemeRegistry schemeReg = new SchemeRegistry();
    schemeReg.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
    SSLSocketFactory socketFactory;
    try {
      SSLContext sslContext = SSLContext.getInstance(SSLSocketFactory.TLS);
      KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmfactory.init(null, null);
      KeyManager[] keymanagers = kmfactory.getKeyManagers();
      TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmfactory.init((KeyStore) null);
      TrustManager[] trustmanagers = tmfactory.getTrustManagers();
      sslContext.init(keymanagers, trustmanagers, null);
      socketFactory = new SSLSocketFactory(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    } catch (Exception ex) {
      throw new IllegalStateException("Failure initializing default SSL context for Moxtra REST client", ex);
    }
    schemeReg.register(new Scheme("https", 443, socketFactory));

    ThreadSafeClientConnManager connectionManager = new ThreadSafeClientConnManager(schemeReg);
    // 2 recommended by RFC 2616 sec 8.1.4, we make it bigger for quicker // ops
    connectionManager.setDefaultMaxPerRoute(4);
    // 20 by default, we twice it also
    connectionManager.setMaxTotal(40);

    DefaultHttpClient client = new DefaultHttpClient(connectionManager);

    try {
      URI uri = new URI(API_V1);
      HttpHost moxtraHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());

      client.getCredentialsProvider().setCredentials(new AuthScope(moxtraHost.getHostName(),
                                                                   moxtraHost.getPort()),
                                                     new OAuthCredentials(oauthClientId, oauthClientSecret));

      this.httpClient = client;

      // TODO Create AuthCache instance
      // AuthCache authCache = new BasicAuthCache();
      // Generate BASIC scheme object and add it to the local auth cache
      // BasicScheme basicAuth = new BasicScheme();
      // authCache.put(moxtraHost, basicAuth);

      // Add AuthCache to the execution context
      this.context = new BasicHttpContext();
      // this.context.setAttribute(ClientContext.AUTH_CACHE, authCache);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(new MoxtraConfigurationException("Wrong Moxtra service URL syntax. "
          + e.getMessage(), e));
    }

    this.oAuthClient = new OAuthClient(new RESTClient());
  }

  /**
   * Authorize via OAuth2 flow.
   * 
   * @return {@link OAuth2Authorizer}
   */
  public OAuth2Authorizer authorizer() {
    if (authorizer == null) {
      authLock.lock();
      try {
        if (authorizer == null) {
          authorizer = new OAuth2Authorizer();
        }
      } finally {
        authLock.unlock();
      }
    }
    return authorizer;
  }

  /**
   * Checks if this client is already authorized to access Moxtra services.
   * 
   * @return <code>true</code> if client is authorized to access Moxtra services, <code>false</code> otherwise
   */
  public boolean isAuthorized() {
    return oAuthToken.isInitialized();
  }

  public MoxtraUser getCurrentUser() throws MoxtraAuthenticationException, MoxtraException {
    return getUser(MOXTRA_USER_ME);
  }

  public MoxtraUser getUser(String userId) throws MoxtraAuthenticationException, MoxtraException {
    if (isAuthorized()) {
      try {
        String url = API_USER.replace("{user_id}", userId);
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

        // TODO make dedicated response object for each API service endpoint
        // and return business object(s) from it,
        // parse business object by reflection assuming that all public fields should be in a response:
        // use GSON?
        JsonValue json = resp.getValue();
        JsonValue dv = json.getElement("data");
        if (!isNull(dv)) {
          JsonValue vid = dv.getElement("id");
          if (isNull(vid)) {
            throw new MoxtraException("User request doesn't return user id");
          }
          JsonValue vname = dv.getElement("name");
          if (isNull(vname)) {
            throw new MoxtraException("User request doesn't return user name");
          }
          JsonValue vemail = dv.getElement("email");
          if (isNull(vemail)) {
            throw new MoxtraException("User request doesn't return user email");
          }
          JsonValue vFirstName = dv.getElement("first_name");
          if (isNull(vFirstName)) {
            throw new MoxtraException("User request doesn't return user first name");
          }
          JsonValue vLastName = dv.getElement("last_name");
          if (isNull(vLastName)) {
            throw new MoxtraException("User request doesn't return user last name");
          }
          JsonValue vtype = dv.getElement("type");
          if (isNull(vtype)) {
            throw new MoxtraException("User request doesn't return user type");
          }
          JsonValue vCreatedTime = dv.getElement("created_time");
          if (isNull(vCreatedTime)) {
            throw new MoxtraException("User request doesn't return user created time");
          }
          JsonValue vUpdatedTime = dv.getElement("updated_time");
          if (isNull(vUpdatedTime)) {
            throw new MoxtraException("User request doesn't return user updated time");
          }
          MoxtraUser user = new MoxtraUser(vid.getStringValue(),
                                           vname.getStringValue(),
                                           vemail.getStringValue(),
                                           vFirstName.getStringValue(),
                                           vLastName.getStringValue(),
                                           vtype.getStringValue(),
                                           MoxtraUser.USER_TYPE_NORMAL,
                                           new Date(vCreatedTime.getLongValue()),
                                           new Date(vUpdatedTime.getLongValue()));
          return user;
        } else {
          throw new MoxtraException("User request doesn't return an expected body (data)");
        }
      } catch (OAuthSystemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      } catch (OAuthProblemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  public List<MoxtraUser> getContacts(MoxtraUser user) throws MoxtraAuthenticationException, MoxtraException {
    return getContacts(user.getId());
  }

  protected List<MoxtraUser> getContacts(String userId) throws MoxtraAuthenticationException, MoxtraException {
    if (isAuthorized()) {
      try {
        String url = API_USER_CONTACTS.replace("{user_id}", userId);
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

        // TODO make dedicated response object for each API service endpoint
        // and return business object(s) from it,
        // parse business object by reflection assuming that all public fields should be in a response:
        // use GSON?
        JsonValue json = resp.getValue();
        JsonValue dv = json.getElement("data");
        if (!isNull(dv)) {
          JsonValue csv = dv.getElement("contacts");
          if (!isNull(csv) && csv.isArray()) {
            List<MoxtraUser> contacts = new ArrayList<MoxtraUser>();
            for (Iterator<JsonValue> citer = csv.getElements(); citer.hasNext();) {
              JsonValue cv = citer.next();
              JsonValue vemail = cv.getElement("email");
              if (isNull(vemail)) {
                throw new MoxtraException("User request doesn't return user email");
              }
              JsonValue vid = cv.getElement("id");
              if (isNull(vid)) {
                throw new MoxtraException("User request doesn't return user id");
              }
              JsonValue vname = cv.getElement("name");
              if (isNull(vname)) {
                throw new MoxtraException("User request doesn't return user name");
              }
              MoxtraUser contact = new MoxtraUser(vid.getStringValue(),
                                                  vname.getStringValue(),
                                                  vemail.getStringValue());
              contacts.add(contact);
            }
            return contacts;
          } else {
            throw new MoxtraException("User request doesn't return an expected body (contacts)");
          }
        } else {
          throw new MoxtraException("User request doesn't return an expected body (data)");
        }
      } catch (OAuthSystemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      } catch (OAuthProblemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  @Deprecated
  public MoxtraMeet getMeetBinder(String binderId) throws OAuthSystemException,
                                                  OAuthProblemException,
                                                  MoxtraException,
                                                  MoxtraClientException {
    if (isAuthorized()) {
      String url = API_BINDER.replace("{binder_id}", binderId);
      RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

      JsonValue json = resp.getValue();
      JsonValue dv = json.getElement("data");
      if (!isNull(dv)) {
        JsonValue vbid = dv.getElement("id");
        if (isNull(vbid)) {
          throw new MoxtraException("Binder request doesn't return id");
        }
        JsonValue vbname = dv.getElement("name");
        if (isNull(vbname)) {
          throw new MoxtraException("Binder request doesn't return name");
        }
        JsonValue vrevision = dv.getElement("revision");
        if (isNull(vrevision)) {
          throw new MoxtraException("Binder request doesn't return revision");
        }
        JsonValue vcreated = dv.getElement("created_time");
        if (isNull(vcreated)) {
          throw new MoxtraException("Binder request doesn't return created_time");
        }
        JsonValue vupdated = dv.getElement("updated_time");
        if (isNull(vupdated)) {
          throw new MoxtraException("Binder request doesn't return updated_time");
        }
        JsonValue vusers = dv.getElement("users");
        if (isNull(vusers) || !vusers.isArray()) {
          throw new MoxtraException("Binder request doesn't return users array");
        }

        // read meet participants - it's binder users
        List<MoxtraUser> participants = new ArrayList<MoxtraUser>();
        for (Iterator<JsonValue> vuiter = vusers.getElements(); vuiter.hasNext();) {
          JsonValue vue = vuiter.next();

          JsonValue vtype = vue.getElement("type");
          if (isNull(vtype)) {
            throw new MoxtraException("Binder request doesn't return user type");
          }
          JsonValue vstatus = vue.getElement("status");
          if (isNull(vstatus)) {
            throw new MoxtraException("Binder request doesn't return user status");
          }
          JsonValue vCreatedTime = vue.getElement("created_time");
          if (isNull(vCreatedTime)) {
            throw new MoxtraException("Binder request doesn't return user created time");
          }
          JsonValue vUpdatedTime = vue.getElement("updated_time");
          if (isNull(vUpdatedTime)) {
            throw new MoxtraException("Binder request doesn't return user updated time");
          }

          // user element
          JsonValue vu = vue.getElement("user");
          if (isNull(vu)) {
            throw new MoxtraException("Binder request doesn't return user in users");
          }
          String userEmail;
          JsonValue vemail = vu.getElement("email");
          if (isNull(vemail)) {
            throw new MoxtraException("Binder request doesn't return user email");
          } else {
            userEmail = vemail.getStringValue();
          }
          String userId;
          JsonValue vid = vu.getElement("id");
          if (isNull(vid)) {
            // throw new MoxtraException("Binder request doesn't return user id");
            userId = userEmail;
          } else {
            userId = vid.getStringValue();
          }
          String userName;
          JsonValue vname = vu.getElement("name");
          if (isNull(vname)) {
            // throw new MoxtraException("Binder request doesn't return user name");
            userName = userEmail;
          } else {
            userName = vname.getStringValue();
          }

          MoxtraUser user = new MoxtraUser(userId, userName, userEmail, //
                                           null, // first name
                                           null, // last name
                                           vtype.getStringValue(),
                                           vstatus.getStringValue(),
                                           new Date(vCreatedTime.getLongValue()),
                                           new Date(vUpdatedTime.getLongValue()));

          participants.add(user);
        }

        MoxtraMeet meet = new MoxtraMeet(vbid.getStringValue(),
                                         vbname.getStringValue(),
                                         vrevision.getLongValue(),
                                         new Date(vcreated.getLongValue()),
                                         new Date(vupdated.getLongValue()));

        meet.setUsers(participants);
        return meet;
      } else {
        throw new MoxtraException("Binder request doesn't return an expected body (data)");
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * DO NOT USE IT!
   * 
   * @param sessionKey
   * @return
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  @Deprecated
  public MoxtraMeet getUpdateMeet(String sessionKey) throws OAuthSystemException,
                                                    OAuthProblemException,
                                                    MoxtraException,
                                                    MoxtraClientException {
    // XXX here we do a workaround: we post update meet with no changes to get a meet current data
    // indeed this way we actually do update the meet: its revision increments each request
    if (isAuthorized()) {
      // prepare body
      JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
      Map<String, Object> params = new HashMap<String, Object>(); // empty JSON
      try {
        String url = API_MEETS_SESSION.replace("{session_key}", sessionKey);
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(params)
                                                                           .toString());

        JsonValue json = resp.getValue();
        JsonValue vcode = json.getElement("code");
        if (!isNull(vcode) && vcode.getStringValue().equals(RESPONSE_SUCCESS)) {
          JsonValue vdata = json.getElement("data");
          if (!isNull(vdata)) {
            JsonValue vkey = vdata.getElement("session_key");
            if (isNull(vkey)) {
              throw new MoxtraException("Meet reading request doesn't return session_key");
            }
            sessionKey = vkey.getStringValue(); // for a case :)
            JsonValue vbid = vdata.getElement("schedule_binder_id");
            if (isNull(vbid)) {
              throw new MoxtraException("Meet reading request doesn't return schedule_binder_id");
            }
            JsonValue vbname = vdata.getElement("binder_name");
            if (isNull(vbname)) {
              throw new MoxtraException("Meet reading request doesn't return binder_name");
            }
            JsonValue vrevision = vdata.getElement("revision");
            if (isNull(vrevision)) {
              throw new MoxtraException("Meet reading request doesn't return revision");
            }
            JsonValue vurl = vdata.getElement("startmeet_url");
            if (isNull(vurl)) {
              throw new MoxtraException("Meet reading request doesn't return startmeet_url");
            }
            JsonValue vcreated = vdata.getElement("created_time");
            if (isNull(vcreated)) {
              throw new MoxtraException("Meet reading request doesn't return created_time");
            }
            JsonValue vupdated = vdata.getElement("updated_time");
            if (isNull(vupdated)) {
              throw new MoxtraException("Meet reading request doesn't return updated_time");
            }
            // time can be in two different forms starts/ends or scheduled_starts/scheduled_ends
            Date startTime, endTime;
            JsonValue vstarts = vdata.getElement("starts");
            if (isNull(vstarts)) {
              vstarts = vdata.getElement("scheduled_starts");
              if (isNull(vstarts)) {
                throw new MoxtraException("Meet reading request doesn't return starts time");
              }
              startTime = parseDate(vstarts.getStringValue());
            } else {
              startTime = new Date(vstarts.getLongValue());
            }
            JsonValue vends = vdata.getElement("ends");
            if (isNull(vends)) {
              vends = vdata.getElement("scheduled_ends");
              if (isNull(vends)) {
                throw new MoxtraException("Meet reading request doesn't return ends time");
              }
              endTime = parseDate(vends.getStringValue());
            } else {
              endTime = new Date(vends.getLongValue());
            }
            JsonValue vagenda = vdata.getElement("agenda");
            if (isNull(vagenda)) {
              throw new MoxtraException("Meet reading request doesn't return agenda");
            }
            JsonValue vautorec = vdata.getElement("auto_recording");
            if (isNull(vautorec)) {
              throw new MoxtraException("Meet reading request doesn't return auto_recording");
            }

            String status = getMeetStatus(sessionKey);

            return new MoxtraMeet(vkey.getStringValue(), //
                                  null, // sessionId,
                                  vbid.getStringValue(),
                                  vbname.getStringValue(),
                                  vagenda.getStringValue(),
                                  vrevision.getLongValue(),
                                  vurl.getStringValue(),
                                  getDate(vcreated.getLongValue()),
                                  getDate(vupdated.getLongValue()),
                                  startTime,
                                  endTime,
                                  vautorec.getBooleanValue(),
                                  status);
          } else {
            throw new MoxtraException("Meet schedule request doesn't return an expected body (data)");
          }
        } else {
          throw new MoxtraException("Meet schedule request doesn't return an expected body (code)");
        }
      } catch (JsonException e) {
        throw new MoxtraClientException("Error creating JSON request from meet parameters", e);
      } catch (ParseException e) {
        throw new MoxtraException("Error parsing meet time " + e.getMessage(), e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Get meet info from Moxtra.<br>
   * Don't use this remote for getting meet's all metadata as Moxtra doesn't return such fields as
   * agenda, auto_recording, startmeet_url. Use {@link #refreshMeet(MoxtraMeet)} instead to refresh available
   * fields of existing meet. In other cases this method can work only to get actual state of some field or
   * users (w/ hoster) of the meet, e.g. on meet creation when finally updating local nodes.
   * 
   * @param sessionKey
   * @return
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public MoxtraMeet getMeet(String sessionKey) throws OAuthSystemException,
                                              OAuthProblemException,
                                              MoxtraException,
                                              MoxtraClientException {
    if (isAuthorized()) {
      try {
        String url = API_MEETS_SESSION.replace("{session_key}", sessionKey);
        RESTResponse resp = restRequest(url.toString(), OAuth.HttpMethod.GET);

        JsonValue json = resp.getValue();
        JsonValue vd = json.getElement("data");
        if (!isNull(vd)) {
          MoxtraMeet meet = readMeet(vd);
          return meet;
        } else {
          throw new MoxtraException("Meets request doesn't return an expected body (data)");
        }
      } catch (NotFoundException e) {
        throw new MeetDeletedException("Meet session " + sessionKey + " was deleted", e);
      } catch (ParseException e) {
        throw new MoxtraException("Error parsing meet time " + e.getMessage(), e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Refresh given (local) meet with recent information from Moxtra.
   * 
   * @param meet {@link MoxtraMeet} meet object to refresh
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void refreshMeet(MoxtraMeet meet) throws OAuthSystemException,
                                          OAuthProblemException,
                                          MoxtraException,
                                          MoxtraClientException {

    MoxtraMeet remoteMeet = getMeet(meet.getSessionKey());
    String bid = remoteMeet.getBinderId();
    if (bid != null && bid.length() > 0) {
      meet.setBinderId(remoteMeet.getBinderId());
    }
    String name = remoteMeet.getName();
    if (name != null) {
      meet.setName(name);
    }
    String sessionId = remoteMeet.getSessionId();
    if (sessionId != null) {
      meet.setSessionId(sessionId);
    }
    String sessionKey = remoteMeet.getSessionKey();
    if (sessionKey != null) {
      meet.setSessionId(sessionKey);
    }
    String agenda = remoteMeet.getAgenda();
    if (agenda != null) {
      meet.setAgenda(agenda);
    }
    String startLink = remoteMeet.getStartMeetUrl();
    if (startLink != null) {
      meet.setStartMeetUrl(startLink);
    }
    Boolean autorec = remoteMeet.getAutoRecording();
    if (autorec != null) {
      meet.setAutoRecording(autorec);
    }
    Date startTime = remoteMeet.getStartTime();
    if (startTime != null) {
      meet.setStartTime(startTime);
    }
    Date endTime = remoteMeet.getEndTime();
    if (endTime != null) {
      meet.setEndTime(endTime);
    }
    try {
      meet.setHostUser(remoteMeet.getHostUser());
    } catch (MoxtraException e) {
      // well... it will be not set for started meet
    }
    meet.setStatus(remoteMeet.getStatus());
    // other binder fields
    meet.setCreatedTime(remoteMeet.getCreatedTime());
    meet.setUpdatedTime(remoteMeet.getUpdatedTime());
    meet.setRevision(remoteMeet.getRevision());
    meet.setThumbnailUrl(remoteMeet.getThumbnailUrl());
    // users
    meet.setUsers(remoteMeet.getUsers());
  }

  /**
   * Get meets for given user (by id) and time period.
   * 
   * @param moxtraUserId {@link String}
   * @param startsFrom {@link Date} from this date
   * @param days {@link Integer} for number of days, but not more that 30 (will be cut if more)
   * @return list of {@link MoxtraMeet}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public List<MoxtraMeet> getMeets(String moxtraUserId, Date startsFrom, Integer days) throws OAuthSystemException,
                                                                                      OAuthProblemException,
                                                                                      MoxtraException,
                                                                                      MoxtraClientException {
    if (isAuthorized()) {
      if (days > GET_MEETS_LIST_MAX_DAYS) {
        LOG.warn("Meets search time period too large. Period cut to maximum " + GET_MEETS_LIST_MAX_DAYS
            + " days");
        days = GET_MEETS_LIST_MAX_DAYS;
      }

      String starts = formatDate(startsFrom);
      try {
        StringBuilder url = new StringBuilder();
        url.append(API_USER_MEETS.replace("{user_id}", moxtraUserId));
        url.append("?starts=");
        url.append(URLEncoder.encode(starts, "UTF-8"));
        if (days != null && days > 0) {
          url.append("&days=");
          url.append(days);
        }

        RESTResponse resp = restRequest(url.toString(), OAuth.HttpMethod.GET);

        JsonValue json = resp.getValue();
        JsonValue dv = json.getElement("data");
        if (!isNull(dv)) {
          JsonValue vmeets = dv.getElement("meets");
          if (isNull(vmeets) || !vmeets.isArray()) {
            throw new MoxtraException("Meets request doesn't return meets array");
          }
          List<MoxtraMeet> meets = new ArrayList<MoxtraMeet>();
          for (Iterator<JsonValue> vmiter = vmeets.getElements(); vmiter.hasNext();) {
            JsonValue vmeet = vmiter.next();
            meets.add(readMeet(vmeet));
          }
          return meets;
        } else {
          throw new MoxtraException("Meets request doesn't return an expected body (data)");
        }
      } catch (UnsupportedEncodingException e) {
        throw new MoxtraException("Error encoding meet search start date '" + starts + "'", e);
      } catch (ParseException e) {
        throw new MoxtraException("Error parsing meet time " + e.getMessage(), e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Find current user meets in given time period.
   * 
   * @param from {@link Date} from date
   * @param toDays {@link Integer} days since the from date, max 30 or it will be cut to the maximum
   * @return list of {@link MoxtraMeet}, if nothing found it will be empty
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public List<MoxtraMeet> getMeets(Date from, Integer toDays) throws OAuthSystemException,
                                                             OAuthProblemException,
                                                             MoxtraException,
                                                             MoxtraClientException {
    return getMeets(MOXTRA_USER_ME, from, toDays);
  }

  /**
   * Find current user meet by given binder id in given date. This method will search in range minus 7 days
   * from the date and for next 30 days. <br>
   * XXX this method is a workaround to find a single meet data by its ID. Use {@link #getMeet(String)}
   * instead.
   * 
   * @param binderId {@link String} scheduled binder id
   * @param date {@link Date} date where the meet should have a place
   * @return {@link MoxtraMeet} of <code>null</code> if nothing found
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  @Deprecated
  public MoxtraMeet findMeet(String binderId, Date date) throws OAuthSystemException,
                                                        OAuthProblemException,
                                                        MoxtraException,
                                                        MoxtraClientException {
    // XXX it is a kind of workaround to find a single meet data in future around current date
    Calendar from = getCalendar(date);
    from.add(Calendar.HOUR_OF_DAY, -168); // minus 7 days
    int toDays = 23; // max 30 days, but minus 7 as above
    for (MoxtraMeet meet : getMeets(MOXTRA_USER_ME, from.getTime(), toDays)) {
      if (binderId.equals(meet.getBinderId())) {
        return meet;
      }
    }

    // if here then no meet found - try as a binder
    MoxtraBinder meetBinder = getBinder(binderId);

    // this meet should be used with notice for null fields
    MoxtraMeet meet = new MoxtraMeet(null, // sessionKey,
                                     null, // sessionId,
                                     meetBinder.getBinderId(),
                                     meetBinder.getName(),
                                     null, // agenda,
                                     meetBinder.getRevision(),
                                     null, // startMeetUrl,
                                     meetBinder.getCreatedTime(),
                                     meetBinder.getUpdatedTime(),
                                     null, // startTime,
                                     null, // endTime,
                                     null, // autoRecording,
                                     null // status
    );

    // now fill the meet participants: read users from Moxtra for full names
    List<MoxtraUser> participants = new ArrayList<MoxtraUser>();
    for (MoxtraUser bu : meetBinder.getUsers()) {
      String userId = bu.getId();
      if (userId != null && userId.length() > 0) {
        try {
          MoxtraUser u = getUser(userId);
          participants.add(new MoxtraUser(u.getId(), u.getName(), bu.getEmail(), // email from binder user
                                          u.getFirstName(),
                                          u.getLastName(),
                                          bu.getType(),
                                          bu.getStatus(),
                                          bu.getCreatedTime(),
                                          bu.getUpdatedTime()));
        } catch (MoxtraClientException e) {
          // so... use what we do have in binder
          LOG.warn("Error reading moxtra user " + bu.getId() + " (" + bu.getEmail() + ")");
          participants.add(bu);
        }
      } else {
        // it's invited (by email) external to Moxtra user
        participants.add(bu);
      }
    }
    meet.setUsers(participants);

    return meet;
  }

  public MoxtraBinder getBinder(String binderId) throws OAuthSystemException,
                                                OAuthProblemException,
                                                MoxtraException,
                                                MoxtraClientException {
    if (isAuthorized()) {
      String url = API_BINDER.replace("{binder_id}", binderId);
      RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

      JsonValue json = resp.getValue();
      JsonValue dv = json.getElement("data");
      if (!isNull(dv)) {
        JsonValue vbid = dv.getElement("id");
        if (isNull(vbid)) {
          throw new MoxtraException("Binder request doesn't return id");
        }
        JsonValue vbname = dv.getElement("name");
        if (isNull(vbname)) {
          throw new MoxtraException("Binder request doesn't return name");
        }
        JsonValue vrevision = dv.getElement("revision");
        if (isNull(vrevision)) {
          throw new MoxtraException("Binder request doesn't return revision");
        }
        JsonValue vcreated = dv.getElement("created_time");
        if (isNull(vcreated)) {
          throw new MoxtraException("Binder request doesn't return created_time");
        }
        JsonValue vupdated = dv.getElement("updated_time");
        if (isNull(vupdated)) {
          throw new MoxtraException("Binder request doesn't return updated_time");
        }
        JsonValue vusers = dv.getElement("users");
        if (isNull(vusers) || !vusers.isArray()) {
          throw new MoxtraException("Binder request doesn't return users array");
        }

        // read binder users
        List<MoxtraUser> users = new ArrayList<MoxtraUser>();
        for (Iterator<JsonValue> vuiter = vusers.getElements(); vuiter.hasNext();) {
          JsonValue vue = vuiter.next();

          JsonValue vutype = vue.getElement("type");
          if (isNull(vutype)) {
            throw new MoxtraException("Binder request doesn't return user type");
          }
          JsonValue vustatus = vue.getElement("status");
          if (isNull(vustatus)) {
            throw new MoxtraException("Binder request doesn't return user status");
          }
          JsonValue vucreated = vue.getElement("created_time");
          if (isNull(vucreated)) {
            throw new MoxtraException("Binder request doesn't return user created time");
          }
          JsonValue vuupdated = vue.getElement("updated_time");
          if (isNull(vuupdated)) {
            throw new MoxtraException("Binder request doesn't return user updated time");
          }

          // user element
          JsonValue vu = vue.getElement("user");
          if (isNull(vu)) {
            throw new MoxtraException("Binder request doesn't return user in users");
          }
          String userEmail;
          JsonValue vuemail = vu.getElement("email");
          if (isNull(vuemail)) {
            throw new MoxtraException("Binder request doesn't return user email");
          } else {
            userEmail = vuemail.getStringValue();
          }
          String userId;
          JsonValue vuid = vu.getElement("id");
          if (isNull(vuid)) {
            // throw new MoxtraException("Binder request doesn't return user id");
            userId = userEmail;
          } else {
            userId = vuid.getStringValue();
          }
          String userName;
          JsonValue vuname = vu.getElement("name");
          if (isNull(vuname)) {
            // throw new MoxtraException("Binder request doesn't return user name");
            userName = userEmail;
          } else {
            userName = vuname.getStringValue();
          }

          MoxtraUser user = new MoxtraUser(userId, userName, userEmail, //
                                           null, // first name
                                           null, // last name
                                           vutype.getStringValue(),
                                           vustatus.getStringValue(),
                                           new Date(vucreated.getLongValue()),
                                           new Date(vuupdated.getLongValue()));

          users.add(user);
        }

        MoxtraBinder binder = new MoxtraBinder(vbid.getStringValue(),
                                               vbname.getStringValue(),
                                               vrevision.getLongValue(),
                                               new Date(vcreated.getLongValue()),
                                               new Date(vupdated.getLongValue()));

        binder.setUsers(users);
        return binder;
      } else {
        throw new MoxtraException("Binder request doesn't return an expected body (data)");
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Create new Moxtra meet and join its users as participants. If users is <code>null</code> or empty
   * then no users will be added explicitly. If start time is <code>null</code> then it will be set to a next
   * minute. If end time is <code>null</code> or before the start time, then it will be adjusted for 30 min
   * since the start.
   * 
   * @param meet {@link MoxtraMeet}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void createMeet(MoxtraMeet meet) throws OAuthSystemException,
                                         OAuthProblemException,
                                         MoxtraException,
                                         MoxtraClientException {
    if (isAuthorized()) {
      // prepare body
      JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("name", meet.getName());
      params.put("agenda", meet.getAgenda());
      meet.checkTime(); // ensure time in natural order
      // params.put("start_time", meet.getStartTime().getTime());
      // params.put("end_time", meet.getEndTime().getTime());
      params.put("starts", formatDate(meet.getStartTime()));
      params.put("ends", formatDate(meet.getEndTime()));
      params.put("auto_recording", meet.isAutoRecording());
      // TODO join_before_minutes
      try {
        RESTResponse resp = restRequest(API_MEETS_SCHEDULE,
                                        OAuth.HttpMethod.POST,
                                        jsonGen.createJsonObjectFromMap(params).toString());

        // TODO make dedicated response object for each API service endpoint
        // and return business object(s) from it,
        // parse business object by reflection assuming that all public fields should be in a response:
        // use GSON?
        JsonValue json = resp.getValue();
        JsonValue vcode = json.getElement("code");
        if (!isNull(vcode) && vcode.getStringValue().equals(RESPONSE_SUCCESS)) {
          JsonValue vdata = json.getElement("data");
          if (!isNull(vdata)) {
            JsonValue vkey = vdata.getElement("session_key");
            if (isNull(vkey)) {
              throw new MoxtraException("Meet schedule request doesn't return session_key");
            }
            JsonValue vbid = vdata.getElement("schedule_binder_id");
            if (isNull(vbid)) {
              throw new MoxtraException("Meet schedule request doesn't return schedule_binder_id");
            }
            JsonValue vbname = vdata.getElement("binder_name");
            if (isNull(vbname)) {
              throw new MoxtraException("Meet schedule request doesn't return binder_name");
            }
            JsonValue vrevision = vdata.getElement("revision");
            if (isNull(vrevision)) {
              throw new MoxtraException("Meet schedule request doesn't return revision");
            }
            JsonValue vurl = vdata.getElement("startmeet_url");
            if (isNull(vurl)) {
              throw new MoxtraException("Meet schedule request doesn't return startmeet_url");
            }
            JsonValue vcreated = vdata.getElement("created_time");
            if (isNull(vcreated)) {
              throw new MoxtraException("Meet schedule request doesn't return created_time");
            }
            JsonValue vupdated = vdata.getElement("updated_time");
            if (isNull(vupdated)) {
              throw new MoxtraException("Meet schedule request doesn't return updated_time");
            }

            // update meet object with returned data
            meet.setSessionKey(vkey.getStringValue());
            meet.setBinderId(vbid.getStringValue());
            meet.setName(vbname.getStringValue());
            meet.setRevision(vrevision.getLongValue());
            meet.setStartMeetUrl(vurl.getStringValue());
            meet.setCreatedTime(getDate(vcreated.getLongValue()));
            meet.setUpdatedTime(getDate(vupdated.getLongValue()));

            // inviteUsers(meet);
            inviteMeetUsers(meet);
          } else {
            throw new MoxtraException("Meet schedule request doesn't return an expected body (data)");
          }
        } else {
          throw new MoxtraException("Meet schedule request doesn't return an expected body (code)");
        }
      } catch (JsonException e) {
        throw new MoxtraClientException("Error creating JSON request from meet parameters", e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  public List<MoxtraMeetRecording> getMeetRecordings(MoxtraMeet meet) throws OAuthSystemException,
                                                                     OAuthProblemException,
                                                                     MoxtraException,
                                                                     MoxtraClientException {

    if (isAuthorized()) {
      try {
        String url = API_MEETS_RECORDINGS.replace("{session_key}", meet.getSessionKey());
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

        JsonValue json = resp.getValue();
        JsonValue vd = json.getElement("data");
        if (!isNull(vd)) {
          JsonValue vrec = vd.getElement("recordings");
          if (!isNull(vrec) && vrec.isArray()) {
            List<MoxtraMeetRecording> recs = new ArrayList<MoxtraMeetRecording>();
            for (Iterator<JsonValue> riter = vrec.getElements(); riter.hasNext();) {
              JsonValue vr = riter.next();
              JsonValue vlink = vr.getElement("download_url");
              if (isNull(vlink)) {
                throw new MoxtraException("Recordings request doesn't return download link");
              }
              JsonValue vtype = vr.getElement("content_type");
              if (isNull(vtype)) {
                throw new MoxtraException("Recordings request doesn't return content type");
              }
              JsonValue vlen = vr.getElement("content_length");
              if (isNull(vlen)) {
                throw new MoxtraException("Recordings request doesn't return content length");
              }
              JsonValue vcreated = vr.getElement("created_time");
              if (isNull(vcreated)) {
                throw new MoxtraException("Recordings request doesn't return created time");
              }
              MoxtraMeetRecording rec = new MoxtraMeetRecording(vtype.getStringValue(),
                                                                vlen.getLongValue(),
                                                                getDate(vcreated.getLongValue()),
                                                                vlink.getStringValue());
              recs.add(rec);
            }
            return recs;
          } else {
            throw new MoxtraException("User request doesn't return an expected body (recordings)");
          }
        } else {
          throw new MoxtraException("User request doesn't return an expected body (data)");
        }
      } catch (OAuthSystemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      } catch (OAuthProblemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  protected String getMeetStatus(String sessionKey) throws MoxtraException,
                                                   MoxtraClientException,
                                                   MoxtraAuthenticationException {

    if (isAuthorized()) {
      try {
        String url = API_MEETS_STATUS.replace("{session_key}", sessionKey);
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

        JsonValue json = resp.getValue();
        JsonValue vd = json.getElement("data");
        if (!isNull(vd)) {
          JsonValue vstatus = vd.getElement("status");
          if (isNull(vstatus)) {
            throw new MoxtraException("Meet status request doesn't return status");
          }
          return vstatus.getStringValue();
        } else {
          throw new MoxtraException("Meet status request doesn't return an expected body (data)");
        }
      } catch (OAuthSystemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      } catch (OAuthProblemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Raw GET request to given URL with OAuth2 access token.
   * 
   * @param url
   * @param contentType
   * @return
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public InputStream requestGet(String url, String contentType) throws OAuthSystemException,
                                                               OAuthProblemException,
                                                               MoxtraException,
                                                               MoxtraClientException {

    if (isAuthorized()) {
      try {
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET, contentType, null);
        InputStream res = resp.getInputStream();
        return res;
      } catch (OAuthSystemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      } catch (OAuthProblemException e) {
        throw new MoxtraAuthenticationException("Authentication error", e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }

  }

  /**
   * Rename existing Moxtra meet. This method also will check if the meet have added/removed users and
   * invite/remove them respectively.
   * If start/end time, agenda or auto recording flag changed then these changes will be propagated to the
   * meet. <br>
   * Method is deprecated, use {@link #updateMeet(MoxtraMeet)} instead.
   *
   * @param meet {@link MoxtraMeet}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   * @see #updateMeet(MoxtraMeet)
   */
  @Deprecated
  public void renameMeet(MoxtraMeet meet) throws OAuthSystemException,
                                         OAuthProblemException,
                                         MoxtraException,
                                         MoxtraClientException {
    if (meet.isNameChanged()) {
      renameBinder(meet);
    }
    if (meet.isUsersAdded()) {
      inviteMeetUsers(meet);
      // inviteUsers(meet);
    }
    if (meet.isUsersRemoved()) {
      removeUsers(meet);
    }
  }

  /**
   * Update Moxtra meet and join/remove its users as participants. If users is <code>null</code> or empty
   * then no users will be added explicitly. If start time is <code>null</code> then it will be set to a next
   * minute. If end time is <code>null</code> or before the start time, then it will be adjusted for 30 min
   * since the start.
   * 
   * @param meet {@link MoxtraMeet}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void updateMeet(MoxtraMeet meet) throws OAuthSystemException,
                                         OAuthProblemException,
                                         MoxtraException,
                                         MoxtraClientException {
    if (isAuthorized()) {
      String meetStatus = getMeetStatus(meet.getSessionKey());
      if (MoxtraMeet.SESSION_SCHEDULED.equals(meetStatus)) {
        // metadata update possible only for scheduled meets
        boolean haveUpdates = false;
        // prepare body
        JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
        Map<String, Object> params = new HashMap<String, Object>();
        if (meet.isNameChanged()) {
          params.put("name", meet.getName());
          haveUpdates = true;
        }
        if (meet.isAgendaChanged()) {
          params.put("agenda", meet.getAgenda());
          haveUpdates = true;
        }
        // params.put("start_time", meet.getStartTime().getTime());
        // params.put("end_time", meet.getEndTime().getTime());
        if (meet.isStartTimeChanged() || meet.isEndTimeChanged()) {
          meet.checkTime();
          params.put("starts", formatDate(meet.getStartTime()));
          params.put("ends", formatDate(meet.getEndTime()));
          haveUpdates = true;
        }
        if (meet.isAutoRecordingChanged()) {
          params.put("auto_recording", meet.isAutoRecording());
          haveUpdates = true;
        }
        // TODO join_before_minutes
        if (haveUpdates) {
          // params.put("session_key", meet.getSessionKey());
          try {
            String url = API_MEETS_SESSION.replace("{session_key}", meet.getSessionKey());
            RESTResponse resp = restRequest(url,
                                            OAuth.HttpMethod.POST,
                                            jsonGen.createJsonObjectFromMap(params).toString());

            // TODO make dedicated response object for each API service endpoint
            // and return business object(s) from it,
            // parse business object by reflection assuming that all public fields should be in a response:
            // use GSON?
            JsonValue json = resp.getValue();
            JsonValue vcode = json.getElement("code");
            if (!isNull(vcode) && vcode.getStringValue().equals(RESPONSE_SUCCESS)) {
              JsonValue vdata = json.getElement("data");
              if (!isNull(vdata)) {
                JsonValue vkey = vdata.getElement("session_key");
                if (isNull(vkey)) {
                  throw new MoxtraException("Meet update request doesn't return session_key");
                }
                JsonValue vbid = vdata.getElement("schedule_binder_id");
                if (isNull(vbid)) {
                  throw new MoxtraException("Meet update request doesn't return schedule_binder_id");
                }
                JsonValue vbname = vdata.getElement("binder_name");
                if (isNull(vbname)) {
                  throw new MoxtraException("Meet update request doesn't return binder_name");
                }
                JsonValue vrevision = vdata.getElement("revision");
                if (isNull(vrevision)) {
                  throw new MoxtraException("Meet update request doesn't return revision");
                }
                JsonValue vurl = vdata.getElement("startmeet_url");
                if (isNull(vurl)) {
                  throw new MoxtraException("Meet update request doesn't return startmeet_url");
                }
                JsonValue vcreated = vdata.getElement("created_time");
                if (isNull(vcreated)) {
                  throw new MoxtraException("Meet update request doesn't return created_time");
                }
                JsonValue vupdated = vdata.getElement("updated_time");
                if (isNull(vupdated)) {
                  throw new MoxtraException("Meet update request doesn't return updated_time");
                }

                // update meet object with returned data
                meet.setSessionKey(vkey.getStringValue());
                meet.setBinderId(vbid.getStringValue());
                meet.setName(vbname.getStringValue());
                meet.setRevision(vrevision.getLongValue());
                meet.setStartMeetUrl(vurl.getStringValue());
                meet.setCreatedTime(getDate(vcreated.getLongValue()));
                meet.setUpdatedTime(getDate(vupdated.getLongValue()));
              } else {
                throw new MoxtraException("Meet update request doesn't return an expected body (data)");
              }
            } else {
              throw new MoxtraException("Meet update request doesn't return an expected body (code)");
            }
          } catch (JsonException e) {
            throw new MoxtraClientException("Error creating JSON request from meet update", e);
          }
        } else {
          // else, meet actually not changed - do we need read recent data from Moxtra?
          if (LOG.isDebugEnabled()) {
            LOG.debug("Meet has not changes " + meet.getBinderId() + " " + meet.getName());
          }
        }
      }
      
      meet.setStatus(meetStatus);

      // TODO user invitation/removal for started also?
      // if (MoxtraMeet.SESSION_SCHEDULED.equals(meetStatus) || MoxtraMeet.SESSION_STARTED.equals(meetStatus))
      // {
      // handle users
      if (meet.isUsersAdded()) {
        inviteMeetUsers(meet);
        // inviteUsers(meet);
      }
      if (meet.isUsersRemoved()) {
        removeUsers(meet);
      }
      // }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Delete Moxtra meet by its session key (meet id).
   * 
   * @param meet {@link MoxtraMeet}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void deleteMeet(MoxtraMeet meet) throws OAuthSystemException,
                                         OAuthProblemException,
                                         MoxtraException,
                                         MoxtraClientException {
    if (isAuthorized()) {
      String url = API_MEETS_SESSION.replace("{session_key}", meet.getSessionKey());
      restRequest(url, OAuth.HttpMethod.DELETE);
      meet.setStatus(MoxtraMeet.SESSION_DELETED);
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Delete Moxtra binder.
   * 
   * @param binder {@link MoxtraBinder}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void deleteBinder(MoxtraBinder binder) throws OAuthSystemException,
                                               OAuthProblemException,
                                               MoxtraException,
                                               MoxtraClientException {
    if (isAuthorized()) {
      String url = API_BINDER.replace("{binder_id}", binder.getBinderId());
      restRequest(url, OAuth.HttpMethod.DELETE);
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Rename Moxtra binder.
   * 
   * @param binder {@link MoxtraBinder}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void renameBinder(MoxtraBinder binder) throws OAuthSystemException,
                                               OAuthProblemException,
                                               MoxtraException,
                                               MoxtraClientException {
    // FYI to remove a meet we remove its binder (created by scheduling the meet)
    if (isAuthorized()) {
      // prepare body
      JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("name", binder.getName());
      try {
        String url = API_BINDER.replace("{binder_id}", binder.getBinderId());
        restRequest(url, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(params).toString());
      } catch (JsonException e) {
        throw new MoxtraClientException("Error creating JSON request from binder parameters", e);
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  /**
   * Invite users from given {@link MoxtraMeet} to the remote meet.
   * 
   * @param meet {@link MoxtraMeet}
   * @param inviteList
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public boolean inviteMeetUsers(MoxtraMeet meet) throws OAuthSystemException,
                                                 OAuthProblemException,
                                                 MoxtraException,
                                                 MoxtraClientException {
    List<MoxtraUser> users;
    if (meet.isNew()) {
      users = meet.getUsers();
    } else if (meet.isUsersAdded()) {
      users = meet.getAddedUsers();
    } else {
      users = null;
    }
    if (users != null && users.size() > 0) {
      if (isAuthorized()) {
        // prepare body
        JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("session_key", meet.getSessionKey());
        List<Object> usersList = new ArrayList<Object>();
        for (MoxtraUser user : users) {
          Map<String, Object> emailMap = new HashMap<String, Object>();
          emailMap.put("email", user.getEmail());
          Map<String, Object> userMap = new HashMap<String, Object>();
          userMap.put("user", emailMap);
          usersList.add(userMap);
        }
        params.put("users", usersList);
        params.put("message", "Please join the " + meet.getName()); // TODO message from user
        try {
          restRequest(API_MEETS_INVITEUSER, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(params)
                                                                          .toString());
          return true;
        } catch (JsonException e) {
          throw new MoxtraClientException("Error creating JSON request from binder parameters", e);
        }
      } else {
        throw new MoxtraException("Authorization required");
      }
    } else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("inviteMeetUsers: empty users list for " + meet.getName() + " "
            + (meet.isNew() ? "" : "(" + meet.getBinderId() + ")"));
      }
      return false;
    }
  }

  /**
   * Invite users from {@link MoxtraBinder} to the remote binder.
   * 
   * @param binder {@link MoxtraBinder}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public boolean inviteUsers(MoxtraBinder binder) throws OAuthSystemException,
                                                 OAuthProblemException,
                                                 MoxtraException,
                                                 MoxtraClientException {
    List<MoxtraUser> users;
    if (binder.isNew()) {
      users = binder.getUsers();
    } else if (binder.isUsersAdded()) {
      users = binder.getAddedUsers();
    } else {
      users = null;
    }
    if (users != null && users.size() > 0) {
      if (isAuthorized()) {
        // prepare body
        JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
        Map<String, Object> params = new HashMap<String, Object>();
        List<Object> usersList = new ArrayList<Object>();
        for (MoxtraUser user : users) {
          Map<String, Object> emailMap = new HashMap<String, Object>();
          emailMap.put("email", user.getEmail());
          Map<String, Object> userMap = new HashMap<String, Object>();
          userMap.put("user", emailMap);
          usersList.add(userMap);
        }
        params.put("users", usersList);
        params.put("message", "Please join the " + binder.getName()); // TODO message from user
        try {
          String url = API_BINDER_INVITEUSER.replace("{binder_id}", binder.getBinderId());
          restRequest(url, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(params).toString());
          return true;
        } catch (JsonException e) {
          throw new MoxtraClientException("Error creating JSON request from binder parameters", e);
        }
      } else {
        throw new MoxtraException("Authorization required");
      }
    } else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("inviteUsers: empty users list for " + binder.getName() + " "
            + (binder.isNew() ? "" : "(" + binder.getBinderId() + ")"));
      }
      return false;
    }
  }

  /**
   * Remove given users from the binder.
   * 
   * @param binder {@link MoxtraBinder}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void removeUsers(MoxtraBinder binder) throws OAuthSystemException,
                                              OAuthProblemException,
                                              MoxtraException,
                                              MoxtraClientException {
    // FYI to remove a meet we remove its binder (created by scheduling the meet)
    List<MoxtraUser> users;
    if (binder.isUsersRemoved()) {
      users = binder.getRemovedUsers();
    } else {
      users = null;
    }
    if (users != null && users.size() > 0) {
      if (isAuthorized()) {
        // XXX need remove user one by one
        for (MoxtraUser user : users) {
          // prepare body
          JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
          Map<String, Object> params = new HashMap<String, Object>();
          params.put("email", user.getEmail());
          try {
            String url = API_BINDER_REMOVEUSER.replace("{binder_id}", binder.getBinderId());
            restRequest(url, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(params).toString());
          } catch (JsonException e) {
            throw new MoxtraClientException("Error creating JSON request from binder user removal "
                + user.getEmail(), e);
          } catch (NotFoundException e) {
            // well... already removed user
            if (LOG.isDebugEnabled()) {
              LOG.debug("removeUsers: user " + user + " not found in " + binder.getBinderId() + " \""
                  + binder.getName() + "\"");
            }
          }
        }
      } else {
        throw new MoxtraException("Authorization required");
      }
    } else if (LOG.isDebugEnabled()) {
      LOG.debug("removeUsers: empty users list for " + binder.getBinderId() + " " + binder.getName());
    }
  }

  // ******* internals ********

  protected RESTError readError(RESTResponse resp) {
    String code;
    String message;
    JsonValue json = resp.getValue();
    if (!isNull(json)) {
      JsonValue error = json.getElement("error");
      if (!isNull(error)) {
        JsonValue msg = error.getElement("message");
        if (!isNull(msg)) {
          JsonValue emv = msg.getElement("value");
          if (!isNull(msg)) {
            message = emv.getStringValue();
          } else {
            message = EMPTY;
          }
        } else {
          message = error.getStringValue();
        }
        JsonValue c = json.getElement("code");
        if (!isNull(c)) {
          code = c.getStringValue();
        } else {
          code = null;
        }
      } else {
        message = EMPTY;
        code = null;
      }
      if (message == EMPTY) {
        JsonValue msg = json.getElement("message");
        if (!isNull(msg)) {
          message = msg.getStringValue();
        }
      }
      if (code == null) {
        JsonValue c = json.getElement("code");
        if (!isNull(c)) {
          code = c.getStringValue();
        }
      }
    } else {
      String text = resp.getText();
      if (text != null) {
        if (resp.getContentType().startsWith("text")) {
          // TODO extract error from text?
          final Matcher matcher = HTML_ERROR_EXTRACT.matcher(text);
          if (matcher.find()) {
            message = matcher.group(1);
          } else {
            message = EMPTY;
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("Moxtra response (" + resp.getResponseCode() + "): =======================\r\n"
                + resp.getText() + "\r\n=======================");
          }
        } else {
          message = EMPTY;
        }
      } else {
        message = EMPTY;
      }
      code = null;
    }
    return new RESTError(code, message);
  }

  protected void initOAuthToken(AccessToken newToken) {
    accessLock.writeLock().lock();
    try {
      this.oAuthToken.merge(newToken);
    } finally {
      accessLock.writeLock().unlock();
    }
  }

  protected AccessToken getOAuthToken() {
    accessLock.readLock().lock();
    try {
      return oAuthToken;
    } finally {
      accessLock.readLock().unlock();
    }
  }

  protected String accessToken() {
    accessLock.readLock().lock();
    try {
      return oAuthToken.getAccessToken();
    } finally {
      accessLock.readLock().unlock();
    }
  }

  protected void checkError(RESTResponse resp) throws MoxtraException {
    if (resp.getResponseCode() == HttpStatus.SC_UNAUTHORIZED) {
      RESTError e = readError(resp);
      throw new MoxtraAccessException("Unauthorized, " + e.message);
    } else if (resp.getResponseCode() == HttpStatus.SC_FORBIDDEN) {
      RESTError e = readError(resp);
      throw new MoxtraAccessException("Forbidden, " + e.message);
    } else if (resp.getResponseCode() == HttpStatus.SC_NOT_FOUND) {
      RESTError e = readError(resp);
      if (RESPONSE_ERROR_NOT_FOUND.equals(e.code)) {
        throw new NotFoundException(e.message);
      }
      throw new MoxtraClientException("Not found, " + e.message);
    } else if (resp.getResponseCode() == HttpStatus.SC_METHOD_NOT_ALLOWED) {
      RESTError e = readError(resp);
      if (e.message != EMPTY) {
        throw new MoxtraClientException("Method not allowed, " + e.message);
      }
      Header allowHeader = resp.getHeader(RESPONSE_ALLOW);
      if (allowHeader != null) {
        throw new MoxtraClientException("Method not allowed. Allowed by server: " + allowHeader.getValue());
      }
      throw new MoxtraClientException("Method not allowed");
    } else if (resp.getResponseCode() == HttpStatus.SC_CONFLICT) {
      RESTError e = readError(resp);
      throw new MoxtraClientException("Conflict " + e.message);
    } else if (resp.getResponseCode() == HttpStatus.SC_REQUEST_TOO_LONG) {
      RESTError e = readError(resp);
      throw new MoxtraClientException("Data too larger, " + e.message);
    } else if (resp.getResponseCode() == 429) { // Too Many Requests
      RESTError e = readError(resp);
      throw new MoxtraException("Too many requests, " + e.message);
    } else if (resp.getResponseCode() >= HttpStatus.SC_BAD_REQUEST) {
      RESTError e = readError(resp);
      if (e.message != EMPTY) {
        throw new MoxtraClientException("Client error, " + e.message);
      }
      throw new MoxtraClientException("Service not found or invalid parameters. Response type was "
          + resp.getContentType());
    } else if (resp.getResponseCode() >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
      RESTError e = readError(resp);
      throw new MoxtraException("Moxtra service error, " + e.message);
    } else if (resp.getResponseCode() <= 0) {
      throw new MoxtraException("No response code");
    }
  }

  protected RESTResponse restRequest(String url, String method) throws OAuthSystemException,
                                                               OAuthProblemException,
                                                               MoxtraException,
                                                               MoxtraClientException {
    return restRequest(url, method, null);
  }

  protected RESTResponse restRequest(String url, String method, String body) throws OAuthSystemException,
                                                                            OAuthProblemException,
                                                                            MoxtraException,
                                                                            MoxtraClientException {
    return restRequest(url, method, REQUEST_CONTENT_TYPE_JSON, body);
  }

  protected RESTResponse restRequest(String url, String method, String contentType, String body) throws OAuthSystemException,
                                                                                                OAuthProblemException,
                                                                                                MoxtraException,
                                                                                                MoxtraClientException {
    if (isAuthorized()) {
      RESTResponse resp = null;
      boolean wasRetry = false;
      retry: while (true) {
        try {
          OAuthClientRequest bearerClientRequest = new OAuthBearerClientRequest(url).setAccessToken(accessToken())
                                                                                    .buildQueryMessage();
          if (contentType != null) {
            bearerClientRequest.setHeader(OAuth.HeaderType.CONTENT_TYPE, contentType);
          }

          if (body != null) {
            bearerClientRequest.setBody(body);
          }

          resp = oAuthClient.resource(bearerClientRequest, method, RESTResponse.class);

          checkError(resp);

          return resp;
        } catch (MoxtraAccessException e) {
          // check expired token
          // Authentication error: Unable to respond to any of these challenges: {bearer=WWW-Authenticate:
          // Bearer realm="oauth", error="invalid_token",
          // error_description="Access token expired: ByIwMgAAAUxn-rC9AACowFVZUHBSUFA1SUxiMlJCbnpMT05jTzY2IAAAAANUa0hienlIeVRuaTZQSEVTamJrNUg0M3NXdTM2b0lrenM0"}
          // Moxtra response: 401 application/json;charset=UTF-8 -1bytes isRestReponse:true
          if (resp != null && !wasRetry && resp.getResponseCode() == HttpStatus.SC_UNAUTHORIZED) {
            if (resp.getValue().toString().indexOf("invalid_token") >= 0) {
              // need update access token (by refresh token)
              authorizer().refresh(); // TODO handle error when refresh failed and need cause reauth in UI
              wasRetry = true;
              continue retry;
            }
          } else {
            throw e;
          }
        }
      }
    } else {
      throw new MoxtraException("Authorization required");
    }
  }

  protected boolean isNull(JsonValue json) {
    return json == null || json.isNull();
  }

  protected MoxtraMeet readMeet(JsonValue vmeet) throws MoxtraException,
                                                OAuthSystemException,
                                                OAuthProblemException,
                                                ParseException {
    JsonValue vsession = vmeet.getElement("session_key");
    if (isNull(vsession)) {
      throw new MoxtraException("Meet request doesn't return meet session key");
    }
    JsonValue vbid = vmeet.getElement("binder_id");
    if (isNull(vbid)) {
      throw new MoxtraException("Meet request doesn't return meet binder id");
    }
    JsonValue vtopic = vmeet.getElement("topic");
    if (isNull(vtopic)) {
      throw new MoxtraException("Meet request doesn't return meet topic");
    }
    String status;
    JsonValue vstatus = vmeet.getElement("status");
    if (isNull(vstatus)) {
      throw new MoxtraException("Meet request doesn't return meet status");
    } else {
      status = vstatus.getStringValue();
    }
    boolean meetStarted = MoxtraMeet.SESSION_STARTED.equals(status);
    // time can be in two different forms starts/ends or scheduled_starts/scheduled_ends
    Date startTime, endTime;
    JsonValue vstarts = vmeet.getElement("starts");
    if (isNull(vstarts)) {
      vstarts = vmeet.getElement("scheduled_starts");
      if (isNull(vstarts)) {
        throw new MoxtraException("Meet request doesn't return starts time");
      }
      startTime = parseDate(vstarts.getStringValue());
    } else {
      startTime = new Date(vstarts.getLongValue());
    }
    JsonValue vends = vmeet.getElement("ends");
    if (isNull(vends)) {
      vends = vmeet.getElement("scheduled_ends");
      if (isNull(vends)) {
        if (meetStarted) {
          // XXX ends undefined for started meet, in eXo need use previous value until meet will end
          endTime = null;
        } else {
          throw new MoxtraException("Meet request doesn't return ends time");
        }
      } else {
        endTime = parseDate(vends.getStringValue());
      }
    } else {
      endTime = new Date(vends.getLongValue());
    }

    // read meet binder for other required fields below
    String binderId = vbid.getStringValue();
    MoxtraBinder meetBinder = getBinder(binderId);

    // gather meet users
    List<JsonValue> vusers = new ArrayList<JsonValue>();
    // invitees for scheduled, not yet started, expired meets
    JsonValue vtotalInvites = vmeet.getElement("total_invitees");
    if (!isNull(vtotalInvites) && vtotalInvites.getIntValue() > 0) {
      JsonValue vinvitees = vmeet.getElement("invitees");
      if (!isNull(vinvitees) && vinvitees.isArray()) {
        for (Iterator<JsonValue> vuiter = vinvitees.getElements(); vuiter.hasNext();) {
          vusers.add(vuiter.next());
        }
      }
    }
    // participants for started, ended meets
    JsonValue vtotalParticipants = vmeet.getElement("total_participants");
    if (!isNull(vtotalParticipants) && vtotalParticipants.getIntValue() > 0) {
      JsonValue vparticipants = vmeet.getElement("participants");
      if (!isNull(vparticipants) && vparticipants.isArray()) {
        for (Iterator<JsonValue> vuiter = vparticipants.getElements(); vuiter.hasNext();) {
          vusers.add(vuiter.next());
        }
      }
    }
    if (vusers.isEmpty() && !meetStarted) {
      throw new MoxtraException("Meet request doesn't return invitees/participants array");
    }
    MoxtraUser hostUser = null;
    List<MoxtraUser> participants = new ArrayList<MoxtraUser>();
    for (JsonValue vu : vusers) {
      String userEmail;
      JsonValue vemail = vu.getElement("email");
      if (isNull(vemail)) {
        throw new MoxtraException("Meet request doesn't return user email");
      } else {
        userEmail = vemail.getStringValue();
      }
      String userName;
      JsonValue vname = vu.getElement("name");
      if (isNull(vname)) {
        userName = null;
      } else {
        userName = vname.getStringValue();
      }
      JsonValue vhost = vu.getElement("host");
      if (isNull(vhost)) {
        throw new MoxtraException("Meet request doesn't return user host flag");
      }

      // find user from associated binder
      MoxtraUser binderUser = null;
      for (MoxtraUser bu : meetBinder.getUsers()) {
        if (userEmail.equals(bu.getEmail())) {
          binderUser = bu;
          break;
        }
      }

      MoxtraUser user;
      if (binderUser == null) {
        // TODO just invited user? why not already in the binder?
        // throw new MoxtraException("Cannot find meet participant in its binder users " + userEmail
        // + " (" + userName + ")");
        user = new MoxtraUser(userEmail);
      } else {
        user = new MoxtraUser(binderUser.getId(),
                              userName != null ? userName : binderUser.getName(),
                              userEmail,
                              binderUser.getFirstName(),
                              binderUser.getLastName(),
                              binderUser.getType(),
                              binderUser.getStatus(),
                              binderUser.getCreatedTime(),
                              binderUser.getUpdatedTime());
      }

      participants.add(user);
      if (vhost.getBooleanValue()) {
        hostUser = user;
      }
    }

    // XXX not actually returning fields
    String agenda;
    JsonValue vagenda = vmeet.getElement("agenda");
    if (isNull(vagenda)) {
      // throw new MoxtraException("Meet request doesn't return meet agenda");
      agenda = null;
    } else {
      agenda = vagenda.getStringValue();
    }
    String startMeetUrl;
    JsonValue vlink = vmeet.getElement("startmeet_url");
    if (isNull(vlink)) {
      // throw new MoxtraException("Meet request doesn't return meet start URL");
      startMeetUrl = null;
    } else {
      startMeetUrl = vlink.getStringValue();
    }
    Boolean autorec;
    JsonValue vautorec = vmeet.getElement("auto_recording");
    if (isNull(vautorec)) {
      // throw new MoxtraException("Meet request doesn't return auto_recording");
      autorec = null;
    } else {
      autorec = vautorec.getBooleanValue();
    }

    // as for fields that cannot be read: we could do met update and that response return such data
    MoxtraMeet meet = new MoxtraMeet(vsession.getStringValue(), null, // sessionId
                                     binderId,
                                     vtopic.getStringValue(), // name
                                     agenda, // XXX agenda cannot be read = null
                                     meetBinder.getRevision(),
                                     startMeetUrl, // XXX startMeetUrl cannot be read = null
                                     meetBinder.getCreatedTime(),
                                     meetBinder.getUpdatedTime(),
                                     startTime,
                                     endTime, // XXX will be null for started meet
                                     autorec, // XXX autoRecording cannot be read = null
                                     status);

    meet.setUsers(participants); // XXX will be empty list for started meet
    meet.setHostUser(hostUser); // XXX will be null for started meet
    return meet;
  }

}
