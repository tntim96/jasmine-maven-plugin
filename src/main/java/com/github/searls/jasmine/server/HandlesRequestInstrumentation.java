package com.github.searls.jasmine.server;

import jscover.ConfigurationCommon;
import jscover.instrument.InstrumenterService;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class HandlesRequestInstrumentation {

  private InstrumenterService instrumenterService = new InstrumenterService();
  private ConfigurationCommon config = new ConfigurationCommon();

  public HandlesRequestInstrumentation() {
    config.setDetectCoalesce(false);
    config.setIncludeUnloadedJS(true);
    config.setLocalStorage(true);
  }

  public void handle(Request baseRequest, HttpServletResponse response, FileResource resource) throws IOException {
    baseRequest.setHandled(true);
    String javascript = instrumenterService.instrumentJSForWebServer(config, resource.getFile(), baseRequest.getRequestURI());
    setHeaders(response, resource, javascript);
    writeResponse(response, javascript);
  }

  private void writeResponse(HttpServletResponse response, String javascript) throws IOException {
    response.getWriter().write(javascript);
  }

  private void setHeaders(HttpServletResponse response, Resource resource, String javascript) {
    response.setCharacterEncoding("UTF-8");
    response.setContentType("text/javascript");
    response.setDateHeader(HttpHeaders.LAST_MODIFIED, resource.lastModified());
    try {
      int contentLength = javascript.getBytes("UTF-8").length;
      response.setHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(contentLength));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(
        "The JVM does not support the compiler's default encoding.", e);
    }
  }
}
