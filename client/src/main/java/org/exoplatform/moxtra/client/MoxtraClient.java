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
import static org.exoplatform.moxtra.Moxtra.fullName;
import static org.exoplatform.moxtra.Moxtra.getCalendar;
import static org.exoplatform.moxtra.Moxtra.getDate;
import static org.exoplatform.moxtra.Moxtra.parseDate;
import static org.exoplatform.moxtra.OAuthClientConfiguration.CLIENT_AUTH_METHOD_SAML;
import static org.exoplatform.moxtra.OAuthClientConfiguration.CLIENT_AUTH_METHOD_UNIQUEID;
import static org.exoplatform.moxtra.OAuthClientConfiguration.CLIENT_DEFAULT_ORGID;
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
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.request.ClientHeaderParametersApplier;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest.TokenRequestBuilder;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponseFactory;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthRuntimeException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.parameters.BodyURLEncodedParametersApplier;
import org.apache.oltu.oauth2.common.parameters.QueryParameterApplier;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.apache.ws.commons.util.Base64;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.moxtra.Moxtra;
import org.exoplatform.moxtra.MoxtraException;
import org.exoplatform.moxtra.NotFoundException;
import org.exoplatform.moxtra.oauth2.AccessToken;
import org.exoplatform.moxtra.rest.OAuthCodeAuthenticator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.Query;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.ConversationState;
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
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

  public static final String     MOXTRA_USER_ME                     = "me";

  public static final String     MOXTRA_URL                         = "https://www.moxtra.com/";

  public static final String     API_V1                             = "https://api.moxtra.com/";

  public static final String     API_OAUTH_AUTHORIZE                = API_V1 + "oauth/authorize";

  public static final String     API_USER                           = API_V1 + "{user_id}";

  public static final String     MOXTRA_CURRENT_USER                = API_V1 + MOXTRA_USER_ME + "#";

  public static final String     MOXTRA_CURRENT_USER_EXPIRE         = MOXTRA_CURRENT_USER + "#expire-";

  public static final long       MOXTRA_CURRENT_USER_EXPIRE_TIMEOUT = 1000 * 60 * 10;                         // 10min

  public static final String     API_USER_CONTACTS                  = API_USER + "/contacts";

  public static final String     API_USER_MEETS                     = API_USER + "/meets";

  public static final String     API_MEETS_SCHEDULE                 = API_V1 + "meets/schedule";

  public static final String     API_MEETS_SESSION                  = API_V1 + "meets/{session_key}";

  public static final String     API_MEETS_INVITEUSER               = API_V1 + "meets/inviteuser";

  public static final String     API_MEETS_RECORDINGS               = API_V1
                                                                        + "meets/recordings/{session_key}";

  public static final String     API_MEETS_STATUS                   = API_V1 + "meets/status/{session_key}";

  public static final String     API_BINDER                         = API_V1 + "{binder_id}";

  public static final String     API_BINDER_INVITEUSER              = API_BINDER + "/inviteuser";

  public static final String     API_BINDER_REMOVEUSER              = API_BINDER + "/removeuser";

  public static final String     API_BINDER_ADDTEAMUSER             = API_BINDER + "/addteamuser";

  public static final String     API_BINDER_PAGEUPLOAD              = API_BINDER + "/pageupload";

  public static final String     API_BINDER_VIEWONLYLINK            = API_BINDER + "/viewonlylink";

  public static final String     API_BINDER_CONVERSATIONS           = API_BINDER + "/conversations";

  public static final String     API_BINDER_DOWNLOADPDF             = API_BINDER + "/downloadpdf";

  public static final String     API_BINDERS                        = API_V1 + "{user_id}/binders";

  public static final String     RESPONSE_SUCCESS                   = "RESPONSE_SUCCESS";

  public static final String     REQUEST_CONTENT_TYPE_JSON          = "application/json".intern();

  public static final String     REQUEST_CONTENT_TYPE_BINARY        = "application/octet-stream".intern();

  public static final String     REQUEST_ACCEPT                     = "Accept".intern();

  public static final String     RESPONSE_ALLOW                     = "Allow".intern();

  public static final int        GET_MEETS_LIST_MAX_DAYS            = 30;

  public static final int        DAY_MILLISECONDS                   = 86400000;

  public static final String     RESPONSE_ERROR_NOT_FOUND           = "RESPONSE_ERROR_NOT_FOUND".intern();

  protected static final Pattern HTML_ERROR_EXTRACT                 = Pattern.compile("<u>(.+?)</u>");

  protected static final String  EMPTY                              = "".intern();

  protected static final Log     LOG                                = ExoLogger.getLogger(MoxtraClient.class);

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

    /**
     * Message from failed response if cause available.
     * 
     * @return String
     */
    public String getErrorMessage() {
      return cause != null ? cause.getMessage() : EMPTY;
    }
  }

  protected class RESTRequestBuilder extends OAuthBearerClientRequest {

    public RESTRequestBuilder(String url) {
      super(url);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RESTRequest buildQueryMessage() throws OAuthSystemException {
      RESTRequest request = new RESTRequest(url);
      this.applier = new QueryParameterApplier();
      return (RESTRequest) applier.applyOAuthParameters(request, parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RESTRequest buildBodyMessage() throws OAuthSystemException {
      RESTRequest request = new RESTRequest(url);
      this.applier = new BodyURLEncodedParametersApplier();
      return (RESTRequest) applier.applyOAuthParameters(request, parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RESTRequest buildHeaderMessage() throws OAuthSystemException {
      RESTRequest request = new RESTRequest(url);
      this.applier = new ClientHeaderParametersApplier();
      return (RESTRequest) applier.applyOAuthParameters(request, parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RESTRequestBuilder setAccessToken(String accessToken) {
      return (RESTRequestBuilder) super.setAccessToken(accessToken);
    }
  }

  protected class RESTRequest extends OAuthClientRequest {

    protected HttpEntity bodyEntity;

    protected RESTRequest(String url) {
      super(url);
    }

    protected boolean hasEntity() {
      return bodyEntity != null;
    }

    /**
     * @param bodyEntity the bodyEntity to set
     */
    protected void setBodyEntity(HttpEntity bodyEntity) {
      this.bodyEntity = bodyEntity;
      this.body = null;
    }

    /**
     * @return the bodyEntity
     */
    protected HttpEntity getBodyEntity() {
      return bodyEntity;
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
            post.setEntity(entity(request));
            req = post;
          } else if (OAuth.HttpMethod.PUT.equals(requestMethod)) {
            HttpPut put = new HttpPut(location);
            put.setEntity(entity(request));
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
              String details;
              String responseText;
              if (contentType.startsWith(MediaType.APPLICATION_JSON)) {
                // try read as JSON
                try {
                  JsonParser jsonParser = new JsonParserImpl();
                  JsonDefaultHandler handler = new JsonDefaultHandler();
                  jsonParser.parse(new StringReader(responseBody), handler);
                  JsonValue responseJson = handler.getJsonObject();
                  details = readOAuthProblem(responseJson);
                  responseText = responseJson.toString();
                } catch (JsonException jsone) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot read errouneus response as JSON", e);
                  }
                  details = e.getMessage();
                  responseText = responseBody;
                }
              } else {
                StringBuilder dbuilder = new StringBuilder();
                dbuilder.append(e.getMessage());
                dbuilder.append(".");
                String desc = e.getDescription();
                if (desc != null && desc.length() > 0) {
                  dbuilder.append(" ");
                  dbuilder.append(desc);
                  dbuilder.append(".");
                }
                String err = e.getError();
                if (err != null && err.length() > 0) {
                  dbuilder.append(" ");
                  dbuilder.append(err);
                  dbuilder.append(".");
                }
                dbuilder.append(" [");
                dbuilder.append(responseCode);
                dbuilder.append("]");

                details = dbuilder.toString();
                responseText = responseBody;
              }
              if (LOG.isDebugEnabled()) {
                LOG.debug("Authorization problem (" + details + ")\r\n====== Moxtra response " + contentType
                    + " ======\r\n" + responseText + "\r\n=========================\r\n", e);
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

    protected HttpEntity entity(OAuthClientRequest request) throws UnsupportedEncodingException {
      HttpEntity entity = null;
      if (request instanceof RESTRequest) {
        RESTRequest restRequest = (RESTRequest) request;
        if (restRequest.hasEntity()) {
          entity = restRequest.getBodyEntity();
        }
      }

      if (entity == null) {
        entity = new StringEntity(request.getBody() == null ? EMPTY : request.getBody());
      }
      return entity;
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

  public abstract class Authorizer {

  }

  /**
   * Builder-style helper for Moxtra authorization via OAuth2.
   */
  public class OAuth2Authorizer extends Authorizer {
    /**
     * Redirect link for standard OAuth2 authorization.
     */
    protected String redirectLink;

    /**
     * Organization user identifiers for custom OAuth2 authorization.
     */
    protected String userId, firstName, lastName;

    /**
     * Create authorization link using default redirect link handled by {@link OAuthCodeAuthenticator} REST
     * service.
     * 
     * @return {@link String}
     * @throws OAuthSystemException
     * @see {@link OAuthCodeAuthenticator}
     */
    public String authorizationLink() throws OAuthSystemException {
      return authorizationCodeLink();
    }

    /**
     * Create link for code based authorization using default redirect link handled by
     * {@link OAuthCodeAuthenticator} REST service.
     * 
     * @return {@link String}
     * @throws OAuthSystemException
     * @see {@link OAuthCodeAuthenticator}
     */
    public String authorizationCodeLink() throws OAuthSystemException {
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

    public MoxtraClient authorizeOrg(String userId, String firstName, String lastName) throws OAuthSystemException,
                                                                                      OAuthProblemException,
                                                                                      MoxtraClientException {
      // save user identifiers
      this.userId = userId;
      this.firstName = firstName;
      this.lastName = lastName;

      // generate code
      try {
        String timestamp = Long.toString(System.currentTimeMillis());
        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(oAuthClientSecret.getBytes(), "HmacSHA256");
        sha256HMAC.init(secretKey);
        StringBuffer total = new StringBuffer();
        total.append(oAuthClientId);
        total.append(userId);
        total.append(timestamp);
        String signature = encodeUrlSafe(sha256HMAC.doFinal(total.toString().getBytes()));

        TokenRequestBuilder rbuilder = OAuthClientRequest.tokenLocation("https://api.moxtra.com/oauth/token")
                                                         .setParameter(OAuth.OAUTH_GRANT_TYPE,
                                                                       "http://www.moxtra.com/auth_uniqueid")
                                                         .setParameter("uniqueid", userId)
                                                         .setParameter("timestamp", timestamp)
                                                         .setParameter("signature", signature)
                                                         .setParameter("firstname", firstName)
                                                         .setParameter("lastname", lastName)
                                                         .setClientId(oAuthClientId)
                                                         .setClientSecret(oAuthClientSecret);
        if (!isSingleOrg()) {
          rbuilder.setParameter("orgid", URLEncoder.encode(orgId, "UTF8"));
        }

        OAuthClientRequest request = rbuilder.buildQueryMessage();

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
      } catch (InvalidKeyException e) {
        throw new MoxtraClientException("Error preparing user authentication for " + userId, e);
      } catch (NoSuchAlgorithmException e) {
        throw new MoxtraClientException("Error preparing user authentication for " + userId, e);
      } catch (UnsupportedEncodingException e) {
        throw new MoxtraClientException("Error preparing user authentication for " + userId, e);
      }
    }

    public MoxtraClient refresh() throws OAuthSystemException, OAuthProblemException, MoxtraClientException {
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
      } else if (isSSOAuth() && userId != null) {
        // request new access token using stored user identifiers from previous call of authorizeOrg()
        return authorizeOrg(userId, firstName, lastName);
      } else {
        throw new OAuthSystemException("Refresh token required");
      }
    }

    /**
     * Try authorize the client transparently (actual for SSO modes only).
     * 
     * @return boolean <code>true</code> if client authorized, <code>false</code> otherwise
     */
    public boolean tryAuthorize() {
      AccessToken accessToken = getOAuthToken();
      if (!accessToken.isInitialized() || accessToken.isExpired()) {
        if (isSSOAuth() && userId != null) {
          try {
            // request new access token using stored user identifiers from previous call of authorizeOrg()
            authorizeOrg(userId, firstName, lastName);
          } catch (Throwable e) {
            // we don't want stop the client work here
            LOG.warn("Error trying to refresh client authorization: " + e.getMessage(), e);
          }
        } // else, we cannot something else without real user interaction (in UI)
      }

      return accessToken.isExpired();
    }

    /**
     * @return the redirectLink
     */
    public String getRedirectLink() {
      return redirectLink;
    }
  }

  public class Content {

    protected final InputStream content;

    protected final String      contentType;

    protected Content(InputStream content, String contentType) {
      super();
      this.content = content;
      this.contentType = contentType;
    }

    /**
     * @return the content
     */
    public InputStream getContent() {
      return content;
    }

    /**
     * @return the contentType
     */
    public String getContentType() {
      return contentType;
    }
  }

  protected final OrganizationService orgService;

  /**
   * Org_ID in Moxtra notion. Can be <code>null</code> if single org should be used.
   */
  protected final String              orgId;

  protected final String              exoUserName;

  protected final String              authMethod;

  protected final boolean             ssoAuth;

  protected final boolean             ssoAuthSAML;

  protected final boolean             ssoAuthUniqueId;

  protected final HttpClient          httpClient;

  protected final OAuthClient         oAuthClient;

  protected final HttpContext         context;

  protected final String              oAuthClientId, oAuthClientSecret;

  protected final String              oAuthClientSchema, oAuthClientHost;

  protected final Lock                authLock   = new ReentrantLock();

  protected final ReadWriteLock       accessLock = new ReentrantReadWriteLock();

  protected AccessToken               oAuthToken = AccessToken.newToken();      // unauthorized by default

  protected OAuth2Authorizer          authorizer;

  /**
   * Moxtra client using OAuth2 authentication.
   * 
   * @throws MoxtraConfigurationException
   */
  public MoxtraClient(String oauthClientId,
                      String oauthClientSecret,
                      String oauthClientSchema,
                      String oauthClientHost,
                      String authMethod,
                      String orgId,
                      String exoUserName,
                      OrganizationService orgService) {
    this.orgService = orgService;
    this.authMethod = authMethod;
    this.orgId = orgId;
    this.exoUserName = exoUserName;

    // find auth flags
    this.ssoAuthUniqueId = CLIENT_AUTH_METHOD_UNIQUEID.equals(authMethod);
    this.ssoAuthSAML = CLIENT_AUTH_METHOD_SAML.equals(authMethod);
    this.ssoAuth = ssoAuthUniqueId || ssoAuthSAML;

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
          if (isSSOAuth()) {
            try {
              User user = orgService.getUserHandler().findUserByName(exoUserName);
              if (user != null) {
                authorizer.authorizeOrg(exoUserName, user.getFirstName(), user.getLastName());
              } else {
                LOG.error("User not found in organization service " + exoUserName
                    + ". Moxtra SSO not possible.");
              }
            } catch (Exception e) {
              LOG.error("Error searching user in organization service " + exoUserName, e);
            }
          }
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
    boolean tryRefresh;
    if (oAuthToken.isInitialized()) {
      if (oAuthToken.isExpired()) {
        tryRefresh = true;
      } else {
        return true;
      }
    } else if (isSSOAuth()) {
      tryRefresh = true;
    } else {
      tryRefresh = false;
    }

    if (tryRefresh) {
      // try refresh the token causing call to Moxtra (not cached)
      // if refresh token is valid or SSO mode, then access will be refreshed with new expiration time
      try {
        authorizer();// .refresh();
        getCurrentUser(false);
        return true;
      } catch (MoxtraAuthorizationException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("isAuthorized: " + e.getMessage());
        }
        // it means user have to re-authorize
      } catch (MoxtraException e) {
        LOG.warn("Error getting current user while checking authorization status", e);
        // an error during auth* assumed as user not authorized
      }
    }

    return false;
  }

  /**
   * Currently authorized user. This user stored in current conversation state for caching purpose and will
   * expire in {@value #MOXTRA_CURRENT_USER_EXPIRE_TIMEOUT} milliseconds since last reading from Moxtra.
   * 
   * @return {@link MoxtraUser}
   * @throws MoxtraAuthenticationException
   * @throws MoxtraException
   */
  public MoxtraUser getCurrentUser() throws MoxtraAuthenticationException, MoxtraException {
    return getCurrentUser(true);
  }

  /**
   * Return currently authorized user using cache or reading directly from Moxtra. When use of cache chosen
   * an attribute of current conversation state will be checked first for a cached user and if not expired
   * in {@value #MOXTRA_CURRENT_USER_EXPIRE_TIMEOUT} milliseconds since last reading from Moxtra, the user
   * will be returned. Otherwise an user will be read from Moxtra API and then cached in the conversation
   * state.
   * 
   * @param useCached boolean, if <code>true</code> then first check for cached user in current
   *          {@link ConversationState}, <code>false</code> read user directly from Moxtra and cache the
   *          actual state
   * @return {@link MoxtraUser}
   * @throws MoxtraAuthenticationException
   * @throws MoxtraException
   */
  public MoxtraUser getCurrentUser(boolean useCached) throws MoxtraAuthenticationException, MoxtraException {
    String userKey = MOXTRA_CURRENT_USER + exoUserName;
    String expireKey = MOXTRA_CURRENT_USER_EXPIRE + exoUserName;
    ConversationState currentConvo = ConversationState.getCurrent();
    if (currentConvo != null) {
      if (useCached) {
        Object uobj = currentConvo.getAttribute(userKey);
        if (uobj != null) {
          Object tobj = currentConvo.getAttribute(expireKey);
          if (tobj != null && ((Long) tobj) < System.currentTimeMillis()) {
            return (MoxtraUser) uobj;
          }
        }
      }
      MoxtraUser user = getUser(MOXTRA_USER_ME);
      currentConvo.setAttribute(userKey, user);
      currentConvo.setAttribute(expireKey, System.currentTimeMillis() + MOXTRA_CURRENT_USER_EXPIRE_TIMEOUT);
      return user;
    } else {
      return getUser(MOXTRA_USER_ME);
    }
  }

  public MoxtraUser getUser(String userId) throws MoxtraAuthenticationException, MoxtraException {
    if (isInitialized()) {
      try {
        String url = API_USER.replace("{user_id}", userId);
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

        JsonValue json = resp.getValue();
        JsonValue dv = json.getElement("data");
        if (!isNull(dv)) {
          // type for user declared as USER_TYPE_NORMAL in the docs
          String type;
          JsonValue vtype = dv.getElement("type");
          if (isNull(vtype)) {
            // throw new MoxtraException("User request doesn't return user type");
            type = MoxtraUser.USER_TYPE_NORMAL;
          } else {
            type = vtype.getStringValue();
          }
          MoxtraUser user = readUser(dv, type);
          // TODO do we need strict check here?
          if (user.getName() == null) {
            throw new MoxtraException("Request doesn't return user name");
          }
          if (user.getEmail() == null) {
            throw new MoxtraException("Request doesn't return user email");
          }
          if (user.getFirstName() == null) {
            throw new MoxtraException("Request doesn't return user first_name");
          }
          if (user.getLastName() == null) {
            throw new MoxtraException("Request doesn't return user last_name");
          }
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
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  public List<MoxtraUser> getContacts(MoxtraUser user) throws MoxtraAuthenticationException, MoxtraException {
    return getContacts(user.getId());
  }

  protected List<MoxtraUser> getContacts(String userId) throws MoxtraAuthenticationException, MoxtraException {
    if (isInitialized()) {
      try {
        String url = API_USER_CONTACTS.replace("{user_id}", userId);
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

        JsonValue json = resp.getValue();
        JsonValue dv = json.getElement("data");
        if (!isNull(dv)) {
          JsonValue csv = dv.getElement("contacts");
          if (!isNull(csv) && csv.isArray()) {
            List<MoxtraUser> contacts = new ArrayList<MoxtraUser>();
            for (Iterator<JsonValue> citer = csv.getElements(); citer.hasNext();) {
              JsonValue cv = citer.next();

              // user type for a contact not define in the docs
              String type;
              JsonValue vtype = cv.getElement("type");
              if (isNull(vtype)) {
                type = MoxtraUser.USER_TYPE_NORMAL;
              } else {
                type = vtype.getStringValue();
              }
              MoxtraUser contact = readUser(cv, type);
              contacts.add(contact);
            }
            return contacts;
          } else {
            return Collections.emptyList();
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
      throw new MoxtraAuthorizationException("Authorization required");
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
    if (isInitialized()) {
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
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  /**
   * Refresh given (local) binder with recent information from Moxtra.
   * 
   * @param binder {@link MoxtraBinder} binder object to refresh
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void refreshBinder(MoxtraBinder binder) throws OAuthSystemException,
                                                OAuthProblemException,
                                                MoxtraException,
                                                MoxtraClientException {

    MoxtraBinder remoteBinder = getBinder(binder.getBinderId());
    String bid = remoteBinder.getBinderId();
    if (bid != null && bid.length() > 0) {
      binder.setBinderId(remoteBinder.getBinderId());
    }
    String name = remoteBinder.getName();
    if (name != null) {
      binder.setName(name);
    }
    // other binder fields
    binder.setCreatedTime(remoteBinder.getCreatedTime());
    binder.setUpdatedTime(remoteBinder.getUpdatedTime());
    binder.setRevision(remoteBinder.getRevision());
    binder.setThumbnailUrl(remoteBinder.getThumbnailUrl());
    // pages
    binder.setPages(remoteBinder.getPages());
    // users
    binder.setUsers(remoteBinder.getUsers());
    // reset isNew
    binder.resetNew();
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
    // reset isNew
    meet.resetNew();
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
    if (isInitialized()) {
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
      throw new MoxtraAuthorizationException("Authorization required");
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
    if (isInitialized()) {
      String url = API_BINDER.replace("{binder_id}", binderId);
      RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

      JsonValue json = resp.getValue();
      JsonValue dv = json.getElement("data");
      if (!isNull(dv)) {
        return readBinder(dv);
      } else {
        throw new MoxtraException("Binder request doesn't return an expected body (data)");
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  /**
   * Get binder list identified by current user.
   * 
   * @param user {@link MoxtraUser}
   * @return list of {@link MoxtraBinder} objects
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public List<MoxtraBinder> getBinders(MoxtraUser user) throws OAuthSystemException,
                                                       OAuthProblemException,
                                                       MoxtraException,
                                                       MoxtraClientException {
    if (isInitialized()) {
      String url = API_BINDERS.replace("{user_id}", user.getId());
      RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

      JsonValue json = resp.getValue();
      JsonValue dv = json.getElement("data");
      if (!isNull(dv)) {
        JsonValue vbinders = dv.getElement("binders");
        if (isNull(vbinders) || !vbinders.isArray()) {
          throw new MoxtraException("Binders request doesn't return binders array");
        }
        List<MoxtraBinder> binders = new ArrayList<MoxtraBinder>();
        for (Iterator<JsonValue> vbiter = vbinders.getElements(); vbiter.hasNext();) {
          JsonValue vbe = vbiter.next();
          JsonValue vbinder = vbe.getElement("binder");
          if (isNull(vbinder)) {
            throw new MoxtraException("Binders request doesn't return binder in the array");
          }
          // TODO there is also "category" element in Json
          binders.add(readBinder(vbinder));
        }
        return binders;
      } else {
        throw new MoxtraException("Binders request doesn't return an expected body (data)");
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  /**
   * Create new Moxtra binder and join its users as participants. If users is <code>null</code> or empty
   * then no users will be added explicitly. Binder
   * 
   * @param ownerUser {@link MoxtraUser}
   * @param binder {@link MoxtraBinder}
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void createBinder(MoxtraUser ownerUser, MoxtraBinder binder) throws OAuthSystemException,
                                                                     OAuthProblemException,
                                                                     MoxtraException,
                                                                     MoxtraClientException {
    if (isInitialized()) {
      // prepare body
      JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("name", binder.getName());
      try {
        String url = API_BINDERS.replace("{user_id}", ownerUser.getId());
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(params)
                                                                           .toString());

        JsonValue json = resp.getValue();
        JsonValue vcode = json.getElement("code");
        if (!isNull(vcode) && vcode.getStringValue().equals(RESPONSE_SUCCESS)) {
          JsonValue vdata = json.getElement("data");
          if (!isNull(vdata)) {
            JsonValue vbid = vdata.getElement("id");
            if (isNull(vbid)) {
              throw new MoxtraException("Binder creation request doesn't return id");
            }
            JsonValue vbname = vdata.getElement("name");
            if (isNull(vbname)) {
              throw new MoxtraException("Binder creation request doesn't return name");
            }
            JsonValue vrevision = vdata.getElement("revision");
            if (isNull(vrevision)) {
              throw new MoxtraException("Binder creation request doesn't return revision");
            }
            JsonValue vcreated = vdata.getElement("created_time");
            if (isNull(vcreated)) {
              throw new MoxtraException("Binder creation request doesn't return created_time");
            }
            JsonValue vupdated = vdata.getElement("updated_time");
            if (isNull(vupdated)) {
              throw new MoxtraException("Binder creation request doesn't return updated_time");
            }

            // update binder object with returned data
            binder.setBinderId(vbid.getStringValue());
            binder.setName(vbname.getStringValue());
            binder.setRevision(vrevision.getLongValue());
            binder.setCreatedTime(getDate(vcreated.getLongValue()));
            binder.setUpdatedTime(getDate(vupdated.getLongValue()));

            inviteUsers(binder);
          } else {
            throw new MoxtraException("Binder creation request doesn't return an expected body (data)");
          }
        } else {
          throw new MoxtraException("Binder creation request doesn't return an expected body (code)");
        }
      } catch (JsonException e) {
        throw new MoxtraClientException("Error creating JSON request from binder creation", e);
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  public List<MoxtraFeed> getBinderConversations(String binderId, long timestamp) throws OAuthSystemException,
                                                                                 OAuthProblemException,
                                                                                 MoxtraException,
                                                                                 MoxtraClientException {
    if (isInitialized()) {
      String url = API_BINDER_CONVERSATIONS.replace("{binder_id}", binderId);
      if (timestamp > 0) {
        url = url + "?timestamp=" + timestamp;
      }

      RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

      JsonValue json = resp.getValue();
      JsonValue vd = json.getElement("data");
      if (!isNull(vd)) {
        try {
          JsonValue vfeeds = vd.getElement("feeds");
          List<MoxtraFeed> feeds = new ArrayList<MoxtraFeed>();
          if (!isNull(vfeeds) && vfeeds.isArray()) {
            for (Iterator<JsonValue> vfiter = vfeeds.getElements(); vfiter.hasNext();) {
              JsonValue vf = vfiter.next();

              String verb;
              JsonValue vverb = vf.getElement("verb");
              if (isNotNull(vverb)) {
                verb = vverb.getStringValue();
              } else {
                throw new MoxtraException("Binder conversations request doesn't return verb");
              }
              Date publishedTime;
              JsonValue vpublished = vf.getElement("published");
              if (isNotNull(vpublished)) {
                publishedTime = parseDate(vpublished.getStringValue());
              } else {
                throw new MoxtraException("Binder conversations request doesn't return published time");
              }
              String generatorId;
              JsonValue vgenerator = vf.getElement("generator");
              if (isNotNull(vgenerator)) {
                vgenerator = vgenerator.getElement("id");
                if (isNotNull(vpublished)) {
                  generatorId = vgenerator.getStringValue();
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return generator id");
                }
              } else {
                throw new MoxtraException("Binder conversations request doesn't return generator data");
              }
              // object
              String objectType, objectId;
              JsonValue vobject = vf.getElement("object");
              if (isNotNull(vobject)) {
                JsonValue vtype = vobject.getElement("objectType");
                if (isNotNull(vtype)) {
                  objectType = vtype.getStringValue();
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return object type");
                }
                JsonValue vid = vobject.getElement("id");
                if (isNotNull(vid)) {
                  objectId = vid.getStringValue();
                } else {
                  // else, some objects has no id (e.g. annotation)
                  objectId = null;
                }
              } else {
                throw new MoxtraException("Binder conversations request doesn't return object data");
              }
              // target
              String targetType, targetId, targetUrl;
              JsonValue vtarget = vf.getElement("target");
              if (isNotNull(vtarget)) {
                JsonValue vtype = vtarget.getElement("objectType");
                if (isNotNull(vtype)) {
                  targetType = vtype.getStringValue();
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return target type");
                }
                JsonValue vid = vtarget.getElement("id");
                if (isNotNull(vid)) {
                  targetId = vid.getStringValue();
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return target id");
                }
                JsonValue vurl = vtarget.getElement("url");
                if (isNotNull(vurl)) {
                  targetUrl = vurl.getStringValue();
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return target url");
                }
              } else {
                if (verb.equals(MoxtraFeed.VERB_CREATE) && generatorId.equals(objectId)) {
                  // it's OK, target is null for newly created binder
                  targetType = targetId = targetUrl = null;
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return target data");
                }
              }
              // actor
              String actorType, actorId;
              JsonValue vactor = vf.getElement("actor");
              if (isNotNull(vactor)) {
                JsonValue vtype = vactor.getElement("objectType");
                if (isNotNull(vtype)) {
                  actorType = vtype.getStringValue();
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return actor type");
                }
                JsonValue vid = vactor.getElement("id");
                if (isNotNull(vid)) {
                  actorId = vid.getStringValue();
                } else {
                  throw new MoxtraException("Binder conversations request doesn't return actor id");
                }
              } else {
                throw new MoxtraException("Binder conversations request doesn't return actor data");
              }

              MoxtraFeed feed = new MoxtraFeed(verb,
                                               generatorId,
                                               actorId,
                                               actorType,
                                               objectId,
                                               objectType,
                                               targetId,
                                               targetType,
                                               targetUrl,
                                               publishedTime);
              feeds.add(feed);
            }
          }
          return feeds;
        } catch (ParseException e) {
          throw new MoxtraException("Error parsing meet time " + e.getMessage(), e);
        }
      } else {
        throw new MoxtraException("Binder conversations request doesn't return an expected body (data)");
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  public Content downloadBinderPage(String binderId, String pageId) throws OAuthSystemException,
                                                                         OAuthProblemException,
                                                                         MoxtraException,
                                                                         MoxtraClientException {
    if (isInitialized()) {
      String url = API_BINDER_DOWNLOADPDF.replace("{binder_id}", binderId);
      url += ("?filter=" + pageId);
      RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

      InputStream content = resp.getInputStream();
      if (content != null) {
        return new Content(content, resp.getContentType());
      } else {
        throw new MoxtraException("Binder page request doesn't return an expected content stream");
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
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
    if (isInitialized()) {
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

            // TODO remove meet on users invitation (aka rollback the creation)
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
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  public MoxtraMeetRecordings getMeetRecordings(MoxtraMeet meet) throws OAuthSystemException,
                                                                OAuthProblemException,
                                                                MoxtraException,
                                                                MoxtraClientException {

    if (isInitialized()) {
      try {
        String url = API_MEETS_RECORDINGS.replace("{session_key}", meet.getSessionKey());
        RESTResponse resp = restRequest(url, OAuth.HttpMethod.GET);

        JsonValue json = resp.getValue();
        JsonValue vd = json.getElement("data");
        if (!isNull(vd)) {
          int count;
          JsonValue vcounts = vd.getElement("count");
          if (isNotNull(vcounts)) {
            count = vcounts.getIntValue();
          } else {
            count = -1;
          }
          List<MoxtraMeetRecording> recs = new ArrayList<MoxtraMeetRecording>();
          JsonValue vrec = vd.getElement("recordings");
          if (!isNull(vrec) && vrec.isArray()) {
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
            return new MoxtraMeetRecordings(count, recs);
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
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  protected String getMeetStatus(String sessionKey) throws MoxtraException,
                                                   MoxtraClientException,
                                                   MoxtraAuthenticationException {

    if (isInitialized()) {
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
      throw new MoxtraAuthorizationException("Authorization required");
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

    if (isInitialized()) {
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
      throw new MoxtraAuthorizationException("Authorization required");
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
    if (meet.hasNameChanged()) {
      renameBinder(meet);
    }
    if (meet.hasUsersAdded()) {
      inviteMeetUsers(meet);
      // inviteUsers(meet);
    }
    if (meet.hasUsersRemoved()) {
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
    if (isInitialized()) {
      String meetStatus = getMeetStatus(meet.getSessionKey());
      if (MoxtraMeet.SESSION_SCHEDULED.equals(meetStatus)) {
        // metadata update possible only for scheduled meets
        boolean haveUpdates = false;
        // prepare body
        JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
        Map<String, Object> params = new HashMap<String, Object>();
        if (meet.hasNameChanged()) {
          params.put("name", meet.getName());
          haveUpdates = true;
        }
        if (meet.hasAgendaChanged()) {
          params.put("agenda", meet.getAgenda());
          haveUpdates = true;
        }
        // params.put("start_time", meet.getStartTime().getTime());
        // params.put("end_time", meet.getEndTime().getTime());
        if (meet.hasStartTimeChanged() || meet.hasEndTimeChanged()) {
          meet.checkTime();
          params.put("starts", formatDate(meet.getStartTime()));
          params.put("ends", formatDate(meet.getEndTime()));
          haveUpdates = true;
        }
        if (meet.hasAutoRecordingChanged()) {
          params.put("auto_recording", meet.isAutoRecording());
          haveUpdates = true;
        } else if (haveUpdates) {
          // XXX need point auto-rec always to Moxtra meet upd
          params.put("auto_recording", meet.isAutoRecording());
        }
        // TODO join_before_minutes
        if (haveUpdates) {
          // params.put("session_key", meet.getSessionKey());
          try {
            String url = API_MEETS_SESSION.replace("{session_key}", meet.getSessionKey());
            RESTResponse resp = restRequest(url,
                                            OAuth.HttpMethod.POST,
                                            jsonGen.createJsonObjectFromMap(params).toString());

            JsonValue json = resp.getValue();
            JsonValue vcode = json.getElement("code");
            if (!isNull(vcode) && vcode.getStringValue().equals(RESPONSE_SUCCESS)) {
              JsonValue vdata = json.getElement("data");
              if (!isNull(vdata)) {
                // read available data and update meet object
                JsonValue vkey = vdata.getElement("session_key");
                if (isNotNull(vkey)) {
                  meet.setSessionKey(vkey.getStringValue());
                } else {
                  // throw new MoxtraException("Meet update request doesn't return session_key");
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet update request doesn't return session_key. Meet: " + meet);
                  }
                }
                JsonValue vbid = vdata.getElement("schedule_binder_id");
                if (isNotNull(vbid)) {
                  meet.setBinderId(vbid.getStringValue());
                } else {
                  // throw new MoxtraException("Meet update request doesn't return schedule_binder_id");
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet update request doesn't return schedule_binder_id. Meet: " + meet);
                  }
                }
                JsonValue vbname = vdata.getElement("binder_name");
                if (isNotNull(vbname)) {
                  meet.setName(vbname.getStringValue());
                } else {
                  // throw new MoxtraException("Meet update request doesn't return binder_name");
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet update request doesn't return binder_name. Meet: " + meet);
                  }
                }
                JsonValue vrevision = vdata.getElement("revision");
                if (isNotNull(vrevision)) {
                  meet.setRevision(vrevision.getLongValue());
                } else {
                  // throw new MoxtraException("Meet update request doesn't return revision");
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet update request doesn't return revision. Meet: " + meet);
                  }
                }
                JsonValue vurl = vdata.getElement("startmeet_url");
                if (isNotNull(vurl)) {
                  meet.setStartMeetUrl(vurl.getStringValue());
                } else {
                  // throw new MoxtraException("Meet update request doesn't return startmeet_url");
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet update request doesn't return startmeet_url. Meet: " + meet);
                  }
                }
                JsonValue vcreated = vdata.getElement("created_time");
                if (isNotNull(vcreated)) {
                  meet.setCreatedTime(getDate(vcreated.getLongValue()));
                } else {
                  // throw new MoxtraException("Meet update request doesn't return created_time");
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet update request doesn't return created_time. Meet: " + meet);
                  }
                }
                JsonValue vupdated = vdata.getElement("updated_time");
                if (isNotNull(vupdated)) {
                  meet.setUpdatedTime(getDate(vupdated.getLongValue()));
                } else {
                  // throw new MoxtraException("Meet update request doesn't return updated_time");
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Meet update request doesn't return updated_time. Meet: " + meet);
                  }
                }
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

      // handle users
      if (meet.hasUsersAdded()) {
        inviteMeetUsers(meet);
        // inviteUsers(meet);
      }
      if (meet.hasUsersRemoved()) {
        removeUsers(meet);
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
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
    if (isInitialized()) {
      String url = API_MEETS_SESSION.replace("{session_key}", meet.getSessionKey());
      restRequest(url, OAuth.HttpMethod.DELETE);
      meet.setStatus(MoxtraMeet.SESSION_DELETED);
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
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
    if (isInitialized()) {
      String url = API_BINDER.replace("{binder_id}", binder.getBinderId());
      restRequest(url, OAuth.HttpMethod.DELETE);
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
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
    if (isInitialized()) {
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
      throw new MoxtraAuthorizationException("Authorization required");
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
    } else if (meet.hasUsersAdded()) {
      users = meet.getAddedUsers();
    } else {
      users = null;
    }
    if (users != null && users.size() > 0) {
      if (isInitialized()) {
        // prepare body
        JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("session_key", meet.getSessionKey());
        List<Object> usersList = new ArrayList<Object>();
        MoxtraUser currentUser = getCurrentUser();
        for (MoxtraUser user : users) {
          // skip current user (already invited by Moxtra)
          if (!currentUser.getEmail().equals(user.getEmail())) {
            Map<String, Object> userMap = new HashMap<String, Object>();
            Map<String, Object> invMap = new HashMap<String, Object>();
            userMap.put("user", invMap);
            usersList.add(userMap);

            boolean isLocal = user.getId() == null;
            String uniqueId = user.getUniqueId();
            if (uniqueId != null && ((isSSOAuth() && isLocal) || !isLocal)) {
              // invite locals w/ unique_id and SSO enabled and existing in Moxtra by unique_id + org_id (user
              // from another org, invite it by unique_id independently does SSO used by current client or
              // not)
              invMap.put("unique_id", uniqueId);
              String userOrgId = user.getOrgId();
              if (isAnotherOrgId(userOrgId) && !user.isSameOrganization(this.orgId)) {
                invMap.put("org_id", userOrgId);
              }
              continue;
            }

            // invite by email
            invMap.put("email", user.getEmail());
          }
        }
        if (usersList.size() > 0) {
          params.put("users", usersList);
          params.put("message", "Please join the " + meet.getName()); // TODO message from user
          try {
            restRequest(API_MEETS_INVITEUSER, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(params)
                                                                            .toString());
          } catch (JsonException e) {
            throw new MoxtraClientException("Error creating JSON request from binder parameters", e);
          }
        } // else meet host user already a member (all invitees are already members)
        return true;
      } else {
        throw new MoxtraAuthorizationException("Authorization required");
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
    } else if (binder.hasUsersAdded()) {
      users = binder.getAddedUsers();
    } else {
      users = null;
    }
    if (users != null && users.size() > 0) {
      if (isInitialized()) {
        // prepare body
        JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
        MoxtraUser currentUser = getCurrentUser();
        List<Object> inviteesList = new ArrayList<Object>();
        List<Object> teamList = new ArrayList<Object>();

        // we split users on team members (if unique_id found for ones) and invitees
        for (MoxtraUser user : users) {
          // skip current user (already invited by Moxtra)
          if (!currentUser.getEmail().equals(user.getEmail())) {
            Map<String, Object> userMap = new HashMap<String, Object>();
            Map<String, Object> invMap = new HashMap<String, Object>();
            userMap.put("user", invMap);

            boolean isLocal = user.getId() == null;
            String uniqueId = user.getUniqueId();
            if (uniqueId != null) {
              if (isSSOAuth()) {
                // if user local only add this user with its uniqueId (it is eXo user name) as team member
                if (isLocal || user.isSameOrganization(this.orgId)) {
                  // local and same org users add as a team member
                  invMap.put("unique_id", uniqueId);
                  // FYI read_only can be set additionally
                  teamList.add(userMap);
                  continue;
                }
              }

              if (!isLocal) {
                // invite exiting in Moxtra by unique_id + org_id (user from another org)
                invMap.put("unique_id", uniqueId);
                String userOrgId = user.getOrgId();
                if (isAnotherOrgId(userOrgId)) {
                  invMap.put("org_id", userOrgId);
                }
                inviteesList.add(userMap);
                continue;
              }
            }

            // invite by email
            invMap.put("email", user.getEmail());
            inviteesList.add(userMap);
          }
        }

        if (teamList.size() > 0) {
          Map<String, Object> team = new HashMap<String, Object>();
          team.put("users", teamList);
          try {
            String url = API_BINDER_ADDTEAMUSER.replace("{binder_id}", binder.getBinderId());
            restRequest(url, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(team).toString());
          } catch (JsonException e) {
            throw new MoxtraClientException("Error creating JSON request for adding users to binder", e);
          }
        }

        if (inviteesList.size() > 0) {
          Map<String, Object> invitees = new HashMap<String, Object>();
          invitees.put("users", inviteesList);
          invitees.put("message", "Please join the " + binder.getName()); // TODO message from user
          try {
            String url = API_BINDER_INVITEUSER.replace("{binder_id}", binder.getBinderId());
            restRequest(url, OAuth.HttpMethod.POST, jsonGen.createJsonObjectFromMap(invitees).toString());
          } catch (JsonException e) {
            throw new MoxtraClientException("Error creating JSON request for inviting users to binder", e);
          }
        }

        // if lists empty - meet host user already a member (all invitees are already members)
        return true;
      } else {
        throw new MoxtraAuthorizationException("Authorization required");
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
    if (binder.hasUsersRemoved()) {
      users = binder.getRemovedUsers();
    } else {
      users = null;
    }
    if (users != null && users.size() > 0) {
      if (isInitialized()) {
        // XXX need remove user one by one
        for (MoxtraUser user : users) {
          // prepare body
          JsonGeneratorImpl jsonGen = new JsonGeneratorImpl();
          Map<String, Object> params = new HashMap<String, Object>();

          String uniqueId = user.getUniqueId();
          if (uniqueId != null) {
            params.put("unique_id", uniqueId);
            if (!user.isSameOrganization(orgId)) {
              params.put("org_id", user.getOrgId());
            }
          } else {
            params.put("email", user.getEmail());
          }

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
        throw new MoxtraAuthorizationException("Authorization required");
      }
    } else if (LOG.isDebugEnabled()) {
      LOG.debug("removeUsers: empty users list for " + binder.getBinderId() + " " + binder.getName());
    }
  }

  /**
   * Upload pages to the binder identified by {binder_id} via multipart/form-data.
   * 
   * @param binder
   * @param contentType
   * @param content
   * @param contentFileName
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void pageUpload(MoxtraBinder binder, String contentType, InputStream content, String contentFileName) throws OAuthSystemException,
                                                                                                              OAuthProblemException,
                                                                                                              MoxtraException,
                                                                                                              MoxtraClientException {
    if (isInitialized()) {
      String url = API_BINDER_PAGEUPLOAD.replace("{binder_id}", binder.getBinderId());
      RESTResponse resp = restRequest(url, OAuth.HttpMethod.POST, contentType, content, contentFileName);

      JsonValue json = resp.getValue();
      JsonValue vcode = json.getElement("code");
      if (!isNull(vcode)) {
        String code = vcode.getStringValue();
        if (!code.equals(RESPONSE_SUCCESS)) {
          throw new MoxtraException("Page upload request ended with not success: " + code);
        }
        // try find and return the page?
      } else {
        throw new MoxtraException("Page upload request doesn't return an expected body (code)");
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  /**
   * Upload file to a session currently running in Moxtra.
   * 
   * @param binder
   * @param contentType
   * @param content
   * @param contentFileName
   * @throws OAuthSystemException
   * @throws OAuthProblemException
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  public void boardUpload(String sessionKey,
                          String sessionId,
                          InputStream content,
                          long contentLength,
                          String contentFileName) throws OAuthSystemException,
                                                 OAuthProblemException,
                                                 MoxtraException,
                                                 MoxtraClientException {
    if (isInitialized()) {
      StringBuilder url = new StringBuilder();
      url.append(MOXTRA_URL);
      url.append("board/upload");
      url.append("?type=original");
      url.append("&sessionid=");
      url.append(sessionId);
      url.append("&key=");
      url.append(sessionKey);
      url.append("&name=");
      try {
        url.append(URLEncoder.encode(contentFileName, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        throw new MoxtraException("Error encoding file name '" + contentFileName + "'", e);
      }

      RESTResponse resp = restRequest(url.toString(),
                                      OAuth.HttpMethod.POST,
                                      content,
                                      contentLength,
                                      REQUEST_CONTENT_TYPE_BINARY);

      JsonValue json = resp.getValue();
      JsonValue vcode = json.getElement("code");
      if (!isNull(vcode)) {
        String code = vcode.getStringValue();
        if (!code.equals(RESPONSE_SUCCESS)) {
          throw new MoxtraException("Board upload request ended with not success: " + code);
        }
      } else {
        throw new MoxtraException("Board upload request doesn't return an expected body (code)");
      }
    } else {
      throw new MoxtraAuthorizationException("Authorization required");
    }
  }

  /**
   * Current OAuth2 access token.
   * 
   * @return String
   */
  public String getOAuthAccessToken() {
    return accessToken();
  }

  /**
   * Tells if OAuth 2.0 SAML bearer used for SSO in Moxtra.
   * 
   * @return boolean <code>false</code> always (not implemented)
   */
  public boolean isSSOSAMLAuth() {
    return ssoAuthSAML;
  }

  /**
   * Tells if OAuth 2.0 SSO used based on Moxtra organization group and user's Unique ID (eXo user name).
   * 
   * @return boolean <code>true</code> if SSO based on Unique ID used, <code>false</code> otherwise
   */
  public boolean isSSOUniqueIdAuth() {
    return ssoAuthUniqueId;
  }

  /**
   * Tells if SSO used for authentication in Moxtra. It is an aggregator of all possible SSO methods in
   * Moxtra.
   * 
   * @return boolean <code>true</code> if SSO used, <code>false</code> otherwise
   * @see #isSSOUniqueIdAuth()
   * @see #isSSOSAMLAuth()
   */
  public boolean isSSOAuth() {
    return ssoAuth;
  }

  /**
   * Tells if single-team approach used for SSO authentication in Moxtra.
   * 
   * @return boolean <code>true</code> if single-team SSO in use, <code>false</code> otherwise
   */
  public boolean isSingleOrg() {
    return CLIENT_DEFAULT_ORGID.equals(orgId);
  }

  /**
   * Moxtra organization (group/company/team) ID that has been used for SSO authorization.
   * 
   * @return String can be <code>null</code> if no SSO or single group used
   */
  public String getOrgId() {
    return orgId;
  }

  /**
   * Check if given organization id differs to currently used by this client.
   * 
   * @param orgId {@link String}
   * @return <code>true</code> if another org_id, <code>false</code> otherwise
   */
  public boolean isAnotherOrgId(String orgId) {
    return orgId != null && !orgId.equals(this.orgId);
  }

  /**
   * Check if given organization id is same as current in this client.
   * 
   * @param orgId {@link String}
   * @return <code>true</code> if same org_id, <code>false</code> otherwise
   */
  public boolean isCurrentOrgId(String orgId) {
    return orgId != null && orgId.equals(this.orgId);
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
      oAuthToken.merge(newToken);
      resetCurrentUser();
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
      throw new MoxtraUnauthorizedException("Unauthorized, " + e.message);
    } else if (resp.getResponseCode() == HttpStatus.SC_FORBIDDEN) {
      RESTError e = readError(resp);
      throw new MoxtraForbiddenException("Forbidden, " + e.message);
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
    try {
      StringEntity sentity = new StringEntity(body == null ? EMPTY : body, HTTP.UTF_8);
      return restRequest(url, method, REQUEST_CONTENT_TYPE_JSON, sentity);
    } catch (UnsupportedEncodingException e) {
      throw new MoxtraClientException("Error preparing request " + e.getMessage(), e);
    }
  }

  protected RESTResponse restRequest(String url,
                                     String method,
                                     String contentType,
                                     InputStream content,
                                     String contentFileName) throws OAuthSystemException,
                                                            OAuthProblemException,
                                                            MoxtraException,
                                                            MoxtraClientException {
    MultipartEntity mpentity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
    InputStreamBody body = new InputStreamBody(content, contentType, contentFileName);
    mpentity.addPart("file", body);
    return restRequest(url, method, null, mpentity);
  }

  protected RESTResponse restRequest(String url,
                                     String method,
                                     InputStream content,
                                     long contentLength,
                                     String contentType) throws OAuthSystemException,
                                                        OAuthProblemException,
                                                        MoxtraException,
                                                        MoxtraClientException {

    InputStreamEntity isentity = new InputStreamEntity(content, contentLength);
    isentity.setContentType(contentType);
    return restRequest(url, method, contentType, isentity);
  }

  protected RESTResponse restRequest(String url, String method, String contentType, HttpEntity bodyEntity) throws OAuthSystemException,
                                                                                                          OAuthProblemException,
                                                                                                          MoxtraException,
                                                                                                          MoxtraClientException {
    RESTResponse resp = null;
    boolean wasRetry = false;
    retry: while (true) {
      try {
        RESTRequest clientRequest = new RESTRequestBuilder(url).setAccessToken(accessToken())
                                                               .buildQueryMessage();
        if (contentType != null) {
          clientRequest.setHeader(OAuth.HeaderType.CONTENT_TYPE, contentType);
        }

        if (bodyEntity != null) {
          clientRequest.setBodyEntity(bodyEntity);
        }

        resp = oAuthClient.resource(clientRequest, method, RESTResponse.class);

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
            try {
              authorizer().refresh();
            } catch (AuthProblemException ape) {
              // it's expired refresh token or unexpected error: reset user authorization, need re-auth user
              // in UI
              oAuthToken.reset();
              // catch text of OAuthProblemException in initial occurrence:
              // "invalid_token, Invalid refresh token (expired): $REFRESH_TOKEN",
              // or "login error: Not logged in" with ape.getMessage():
              // "invalid_request, Missing parameters: access_token.",
              // or "readRefreshTokenForAccessToken" of next attempts to refresh using expired refresh_token
              String err = ape.getErrorMessage();
              if (err != null) {
                if (err.indexOf("invalid_token") >= 0 || err.indexOf("login error") >= 0
                    || err.indexOf("readRefreshTokenForAccessToken") >= 0) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Assuming expired refresh token: '" + err + "' caused refresh error "
                        + ape.getMessage());
                  }
                  throw new MoxtraAuthorizationException("Re-authorization required", e);
                }
              }
              throw ape; // smth unexpected, throw as is
            }
            wasRetry = true;
            continue retry;
          }
        } else {
          throw e;
        }
      }
    }
  }

  protected boolean isNull(JsonValue json) {
    return json == null || json.isNull();
  }

  protected boolean isNotNull(JsonValue json) {
    return json != null && !json.isNull();
  }

  protected MoxtraMeet readMeet(JsonValue vmeet) throws MoxtraException,
                                                OAuthSystemException,
                                                OAuthProblemException,
                                                ParseException {
    String sessionKey;
    JsonValue vsession = vmeet.getElement("session_key");
    if (isNull(vsession)) {
      throw new MoxtraException("Meet request doesn't return meet session key");
    }
    sessionKey = vsession.getStringValue();
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
      startTime = parseDate(vstarts.getStringValue());
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
      endTime = parseDate(vends.getStringValue());
    }

    // read meet binder for other required fields below
    String binderId = vbid.getStringValue();
    MoxtraBinder meetBinder = getBinder(binderId);

    // gather meet users
    // we need read meet users explicitly as their format depends on how they invited (by email or unique_id)
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
    nextUser: for (JsonValue vu : vusers) {
      String userEmail;
      JsonValue vemail = vu.getElement("email");
      if (isNull(vemail) || (userEmail = vemail.getStringValue()).length() == 0) {
        userEmail = null;
      } else {
        userEmail = vemail.getStringValue();
      }
      String uniqueId;
      JsonValue vuuid = vu.getElement("unique_id");
      if (isNull(vuuid) || (uniqueId = vuuid.getStringValue()).length() == 0) {
        uniqueId = null;
      } else {
        uniqueId = vuuid.getStringValue();
      }
      String orgId;
      JsonValue vuoid = vu.getElement("org_id");
      if (isNotNull(vuoid)) {
        orgId = vuoid.getStringValue();
      } else {
        if (isSingleOrg()) {
          orgId = this.orgId;
        } else {
          orgId = null;
        }
      }
      String userName;
      JsonValue vname = vu.getElement("name");
      if (isNull(vname)) {
        userName = null;
      } else {
        userName = vname.getStringValue();
      }
      boolean isHost;
      JsonValue vhost = vu.getElement("host");
      if (isNull(vhost)) {
        throw new MoxtraException("Meet request doesn't return user host flag");
      } else {
        isHost = vhost.getBooleanValue();
      }

      // find user from associated binder
      MoxtraUser binderUser = null;
      for (MoxtraUser bu : meetBinder.getUsers()) {
        if (uniqueId != null && uniqueId.equals(bu.getUniqueId())) {
          if (isHost || isSingleOrg()) {
            // we don't do strict check for host user or same common org (when single org used),
            // FYI uid+orgid in invitees will not contain orgid for the host user
            binderUser = bu;
            break;
          } else if (bu.isSameOrganization(orgId)) {
            // TODO cleanup: orgId != null && bu.getOrgId() != null && orgId.equals(bu.getOrgId())
            // same org with id
            binderUser = bu;
            break;
          }
        }
        if (userEmail != null && userEmail.equals(bu.getEmail())) {
          binderUser = bu;
          break;
        }
      }

      MoxtraUser user;
      if (binderUser == null) {
        // just invited user by email?
        if (userEmail == null) {
          // well just skip the user
          LOG.warn("Skipped meet participant without email and unique_id. " + vu);
          continue nextUser;
        }
        user = new MoxtraUser(userEmail);
      } else {
        user = new MoxtraUser(binderUser.getId(),
                              binderUser.getUniqueId(),
                              binderUser.getOrgId(),
                              userName != null ? userName : binderUser.getName(),
                              userEmail != null && userEmail.length() > 0 ? userEmail : binderUser.getEmail(),
                              binderUser.getFirstName(),
                              binderUser.getLastName(),
                              binderUser.getPictureUri(),
                              binderUser.getType(),
                              binderUser.getCreatedTime(),
                              binderUser.getUpdatedTime());
        user.setStatus(binderUser.getStatus());
      }

      participants.add(user);
      if (isHost) {
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
      // XXX Moxtra doesn't return start url in this service, thus we assume following URL
      // https://www.moxtra.com/SESSION_KEY
      startMeetUrl = MOXTRA_URL + sessionKey;
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
    MoxtraMeet meet = new MoxtraMeet(sessionKey, null, // sessionId
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

  protected MoxtraBinder readBinder(JsonValue vbinder) throws MoxtraException,
                                                      OAuthSystemException,
                                                      OAuthProblemException {

    JsonValue vbid = vbinder.getElement("id");
    if (isNull(vbid)) {
      throw new MoxtraException("Binder request doesn't return id");
    }
    JsonValue vbname = vbinder.getElement("name");
    if (isNull(vbname)) {
      throw new MoxtraException("Binder request doesn't return name");
    }
    JsonValue vrevision = vbinder.getElement("revision");
    if (isNull(vrevision)) {
      throw new MoxtraException("Binder request doesn't return revision");
    }
    JsonValue vcreated = vbinder.getElement("created_time");
    if (isNull(vcreated)) {
      throw new MoxtraException("Binder request doesn't return created_time");
    }
    JsonValue vupdated = vbinder.getElement("updated_time");
    if (isNull(vupdated)) {
      throw new MoxtraException("Binder request doesn't return updated_time");
    }

    // binder pages (TODO lazy reader)
    JsonValue vpages = vbinder.getElement("pages");
    List<MoxtraPage> pages = new ArrayList<MoxtraPage>();
    if (!isNull(vpages) && vpages.isArray()) {
      for (Iterator<JsonValue> vpiter = vpages.getElements(); vpiter.hasNext();) {
        JsonValue vp = vpiter.next();

        JsonValue vpid = vp.getElement("id");
        if (isNull(vpid)) {
          throw new MoxtraException("Binder request doesn't return page id");
        }
        JsonValue vprevision = vp.getElement("revision");
        if (isNull(vprevision)) {
          throw new MoxtraException("Binder request doesn't return page revision");
        }
        Long index;
        JsonValue vpindex = vp.getElement("page_index");
        if (isNull(vpindex)) {
          // throw new MoxtraException("Binder request doesn't return page index");
          index = 0l;
        } else {
          index = vpindex.getLongValue();
        }
        Long number;
        JsonValue vpnumber = vp.getElement("page_number");
        if (isNull(vpnumber)) {
          // throw new MoxtraException("Binder request doesn't return page number");
          number = 0l;
        } else {
          number = vpnumber.getLongValue();
        }
        JsonValue vpurl = vp.getElement("page_uri");
        if (isNull(vpurl)) {
          throw new MoxtraException("Binder request doesn't return page_uri");
        }
        JsonValue vpturl = vp.getElement("thumbnail_uri");
        if (isNull(vpturl)) {
          throw new MoxtraException("Binder request doesn't return thumbnail_uri");
        }
        JsonValue vpburl = vp.getElement("background_uri");
        if (isNull(vpburl)) {
          throw new MoxtraException("Binder request doesn't return background_uri");
        }
        JsonValue vptype = vp.getElement("type");
        if (isNull(vptype)) {
          throw new MoxtraException("Binder request doesn't return page type");
        }
        String origFileName;
        JsonValue vpfname = vp.getElement("original_file_name");
        if (isNull(vpfname)) {
          origFileName = null;
        } else {
          origFileName = vpfname.getStringValue();
        }

        MoxtraPage page = new MoxtraPage(vpid.getLongValue(),
                                         vprevision.getLongValue(),
                                         index,
                                         number,
                                         vptype.getStringValue(),
                                         origFileName,
                                         vpurl.getStringValue(),
                                         vpturl.getStringValue(),
                                         vpburl.getStringValue());
        pages.add(page);
      }
    }

    // read binder users
    JsonValue vusers = vbinder.getElement("users");
    if (isNull(vusers) || !vusers.isArray()) {
      throw new MoxtraException("Binder request doesn't return users array");
    }
    List<MoxtraUser> users = new ArrayList<MoxtraUser>();
    for (Iterator<JsonValue> vuiter = vusers.getElements(); vuiter.hasNext();) {
      users.add(readBinderUser(vuiter.next()));
    }

    MoxtraBinder binder = new MoxtraBinder(vbid.getStringValue(),
                                           vbname.getStringValue(),
                                           vrevision.getLongValue(),
                                           new Date(vcreated.getLongValue()),
                                           new Date(vupdated.getLongValue()));

    binder.setUsers(users);
    binder.setPages(pages);
    return binder;
  }

  protected MoxtraUser readBinderUser(JsonValue vue) throws MoxtraException, MoxtraClientException {
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

    MoxtraUser user = readUser(vu,
                               vutype.getStringValue(),
                               new Date(vucreated.getLongValue()),
                               new Date(vuupdated.getLongValue()));
    user.setStatus(vustatus.getStringValue());
    return user;
  }

  /**
   * Read Moxtra user.
   * 
   * @param vu {@link JsonValue}
   * @return {@link MoxtraUser}
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  @Deprecated
  protected MoxtraUser readUser(JsonValue vu) throws MoxtraException, MoxtraClientException {
    return readUser(vu, null, null, null);
  }

  /**
   * Read Moxtra user.
   * 
   * @param vu {@link JsonValue}
   * @param type String or <code>null</code> if should be read from given JSON value
   * @return {@link MoxtraUser}
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  protected MoxtraUser readUser(JsonValue vu, String type) throws MoxtraException, MoxtraClientException {
    return readUser(vu, type, null, null);
  }

  /**
   * Read Moxtra user.
   * 
   * @param vu {@link JsonValue}
   * @param type String or <code>null</code> if should be read from given JSON value
   * @param createdTime {@link Date} or <code>null</code> if should be read from given JSON value
   * @param updatedTime {@link Date} or <code>null</code> if should be read from given JSON value
   * @return {@link MoxtraUser}
   * @throws MoxtraException
   * @throws MoxtraClientException
   */
  protected MoxtraUser readUser(JsonValue vu, String type, Date createdTime, Date updatedTime) throws MoxtraException,
                                                                                              MoxtraClientException {
    if (type == null) {
      JsonValue vutype = vu.getElement("type");
      if (isNotNull(vutype)) {
        type = vutype.getStringValue();
      } else {
        // throw new MoxtraException("Request doesn't return user type");
        type = MoxtraUser.USER_TYPE_NORMAL;
      }
    }
    if (createdTime == null) {
      JsonValue vucreated = vu.getElement("created_time");
      if (isNotNull(vucreated)) {
        createdTime = new Date(vucreated.getLongValue());
      } else {
        // throw new MoxtraException("Request doesn't return user created time");
        createdTime = null;
      }
    }
    if (updatedTime == null) {
      JsonValue vuupdated = vu.getElement("updated_time");
      if (isNotNull(vuupdated)) {
        updatedTime = new Date(vuupdated.getLongValue());
      } else {
        // throw new MoxtraException("Request doesn't return user updated time");
        updatedTime = null;
      }
    }

    String userEmail;
    JsonValue vuemail = vu.getElement("email");
    if (isNotNull(vuemail)) {
      userEmail = vuemail.getStringValue();
    } else {
      userEmail = null;
    }
    String name;
    JsonValue vuname = vu.getElement("name");
    if (isNull(vuname)) {
      name = userEmail;
    } else {
      name = vuname.getStringValue();
    }
    String firstName;
    JsonValue vFirstName = vu.getElement("first_name");
    if (isNotNull(vFirstName)) {
      firstName = vFirstName.getStringValue();
    } else {
      firstName = null;
    }
    String lastName;
    JsonValue vLastName = vu.getElement("last_name");
    if (isNotNull(vLastName)) {
      lastName = vLastName.getStringValue();
    } else {
      lastName = null;
    }
    String pictureUri;
    JsonValue vupic = vu.getElement("picture_uri");
    if (isNotNull(vupic)) {
      pictureUri = vupic.getStringValue();
    } else {
      pictureUri = null;
    }

    String uniqueId;
    JsonValue vuuid = vu.getElement("unique_id");
    if (isNotNull(vuuid) && (uniqueId = vuuid.getStringValue()).length() > 0) {
      // find user data in eXo organization and use it
      try {
        User user = orgService.getUserHandler().findUserByName(uniqueId);
        if (user == null && userEmail != null && userEmail.length() > 0) {
          // try by email if available
          Query query = new Query();
          query.setEmail(userEmail);
          ListAccess<User> emailUsers = orgService.getUserHandler().findUsersByQuery(query);
          if (emailUsers.getSize() > 0) {
            // XXX use first occurrence
            user = emailUsers.load(0, 1)[0];
          }
        }
        if (user != null) {
          userEmail = user.getEmail();
          firstName = user.getFirstName();
          lastName = user.getLastName();
          name = fullName(user);
        } else {
          // LOG.warn("User not found in organization service " + uniqueId);
          throw new MoxtraClientException("User not found in organization service '" + uniqueId + "'");
        }
      } catch (Exception e) {
        // LOG.warn("Error searching user in organization service " + uniqueId, e);
        throw new MoxtraClientException("Error searching user in organization service '" + uniqueId + "'", e);
      }
    } else {
      // check if name and email exist (for standard OAuth2 user, not SSO)
      if (userEmail == null) {
        throw new MoxtraException("Request doesn't return user email");
      }
      uniqueId = null;
    }
    String orgId;
    JsonValue vuorgid = vu.getElement("org_id");
    if (isNotNull(vuorgid)) {
      orgId = vuorgid.getStringValue();
    } else {
      if (uniqueId != null && LOG.isDebugEnabled()) {
        LOG.debug("Moxtra org_id not provided for user with unique_id " + uniqueId);
      }
      if (isSingleOrg()) {
        orgId = this.orgId;
      } else {
        orgId = null;
      }
    }
    String userId;
    JsonValue vuid = vu.getElement("id");
    if (isNotNull(vuid)) {
      userId = vuid.getStringValue();
    } else {
      userId = null;
    }

    MoxtraUser user = new MoxtraUser(userId,
                                     uniqueId,
                                     orgId,
                                     name,
                                     userEmail,
                                     firstName,
                                     lastName,
                                     pictureUri,
                                     type,
                                     createdTime,
                                     updatedTime);
    return user;
  }

  /**
   * Reset currently authorized user cache in conversation state.
   * 
   */
  protected void resetCurrentUser() {
    ConversationState currentConvo = ConversationState.getCurrent();
    if (currentConvo != null) {
      currentConvo.removeAttribute(MOXTRA_CURRENT_USER + exoUserName);
      currentConvo.removeAttribute(MOXTRA_CURRENT_USER_EXPIRE + exoUserName);
    }
  }

  /**
   * Return <code>true</code> if client's {@link AccessToken} initialized with access token.
   * 
   * @return <code>true</code> if {@link AccessToken} initialized, <code>false</code> otherwise
   */
  protected boolean isInitialized() {
    return oAuthToken.isInitialized();
  }

  /**
   * URLSafe Base64 encoding with space padding
   * 
   * @param data
   * @return
   */
  protected static String encodeUrlSafe(byte[] data) {
    // method copied from
    // https://github.com/Moxtra/Moxtra-Java-Sample-Code/blob/master/src/main/java/com/moxtra/util/MoxtraAPIUtil.java
    // NOTE: Base64 used from Apache WS instead of org.apache.xml.security.utils.Base64
    String strcode = Base64.encode(data);
    byte[] encode = strcode.getBytes();
    for (int i = 0; i < encode.length; i++) {
      if (encode[i] == '+') {
        encode[i] = '-';
      } else if (encode[i] == '/') {
        encode[i] = '_';
      } else if (encode[i] == '=') {
        encode[i] = ' ';
      }
    }
    return new String(encode).trim();
  }

}
