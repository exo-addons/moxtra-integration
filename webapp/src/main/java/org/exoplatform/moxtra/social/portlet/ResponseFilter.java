package org.exoplatform.moxtra.social.portlet;

import juzu.Scope;
import juzu.impl.request.Request;

import java.io.IOException;

import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.filter.FilterChain;
import javax.portlet.filter.FilterConfig;
import javax.portlet.filter.RenderFilter;
import javax.portlet.filter.ResourceFilter;

// TODO not used
@Deprecated
public class ResponseFilter implements RenderFilter, ResourceFilter {

  public void init(FilterConfig filterConfig) throws PortletException {
  }

  public void doFilter(RenderRequest request, RenderResponse response, FilterChain chain) throws IOException,
                                                                                         PortletException {

    initRequest(request, response);
    chain.doFilter(request, response);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void doFilter(ResourceRequest request, ResourceResponse response, FilterChain chain) throws IOException,
                                                                                             PortletException {
    // TODO Auto-generated method stub

  }

  public void destroy() {
  }

  // ******* internals *******

  protected void initRequest(PortletRequest request, PortletResponse response) {
    // TODO Utils.
    Request juzuRequest = Request.getCurrent();
    juzuRequest.setContextualValue(Scope.REQUEST, null, null);
  }
}
