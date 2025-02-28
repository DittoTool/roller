/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.ui.rendering.servlets;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspFactory;
import javax.servlet.jsp.PageContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.RollerConstants;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.themes.ThemeManager;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfig;
import org.apache.roller.weblogger.pojos.ThemeTemplate;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogTheme;
import org.apache.roller.weblogger.ui.rendering.Renderer;
import org.apache.roller.weblogger.ui.rendering.RendererManager;
import org.apache.roller.weblogger.ui.rendering.mobile.MobileDeviceRepository;
import org.apache.roller.weblogger.ui.rendering.model.ModelLoader;
import org.apache.roller.weblogger.ui.rendering.util.WeblogPageRequest;
import org.apache.roller.weblogger.ui.rendering.util.WeblogSearchRequest;
import org.apache.roller.weblogger.ui.rendering.util.cache.SiteWideCache;
import org.apache.roller.weblogger.ui.rendering.util.cache.WeblogPageCache;
import org.apache.roller.weblogger.util.I18nMessages;
import org.apache.roller.weblogger.util.cache.CachedContent;

/**
 * Handles search queries for weblogs.
 */
public class SearchServlet extends HttpServlet {

    private static final long serialVersionUID = 6246730804167411636L;

    private static final Log log = LogFactory.getLog(SearchServlet.class);

    // Development theme reloading
    Boolean themeReload = false;

    /**
     * Init method for this servlet
     */
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {

        super.init(servletConfig);

        log.info("Initializing SearchServlet");

        // Development theme reloading
        themeReload = WebloggerConfig.getBooleanProperty("themes.reload.mode");
    }

    /**
     * Handle GET requests for weblog pages.
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        log.debug("Entering");

        Weblog weblog;
        WeblogSearchRequest searchRequest;

        // first off lets parse the incoming request and validate it
        try {
            searchRequest = new WeblogSearchRequest(request);

            // now make sure the specified weblog really exists
            weblog = searchRequest.getWeblog();
            if (weblog == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Weblog not found");
                return;
            }
        } catch (Exception e) {
            // invalid search request format or weblog doesn't exist
            log.debug("error creating weblog search request", e);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Development only. Reload if theme has been modified
        if (themeReload
                && !weblog.getEditorTheme().equals(WeblogTheme.CUSTOM)) {

            try {
                ThemeManager manager = WebloggerFactory.getWeblogger()
                        .getThemeManager();
                boolean reloaded = manager.reLoadThemeFromDisk(weblog
                        .getEditorTheme());
                if (reloaded) {
                    if (WebloggerRuntimeConfig.isSiteWideWeblog(searchRequest
                            .getWeblogHandle())) {
                        SiteWideCache.getInstance().clear();
                    } else {
                        WeblogPageCache.getInstance().clear();
                    }
                    I18nMessages.reloadBundle(weblog.getLocaleInstance());
                }

            } catch (Exception ex) {
                log.error("ERROR - reloading theme " + ex);
            }
        }

        // Get the deviceType from user agent
        MobileDeviceRepository.DeviceType deviceType = MobileDeviceRepository
                .getRequestType(request);

        // for previews we explicitly set the deviceType attribute
        if (request.getParameter("type") != null) {
            deviceType = request.getParameter("type").equals("standard") ? MobileDeviceRepository.DeviceType.standard
                    : MobileDeviceRepository.DeviceType.mobile;
        }

        // do we need to force a specific locale for the request?
        if (searchRequest.getLocale() == null && !weblog.isShowAllLangs()) {
            searchRequest.setLocale(weblog.getLocale());
        }

        // lookup template to use for rendering
        ThemeTemplate page = null;
        try {

            // try looking for a specific search page
            page = weblog.getTheme().getTemplateByAction(
                    ThemeTemplate.ComponentType.SEARCH);

            // if not found then fall back on default page
            if (page == null) {
                page = weblog.getTheme().getDefaultTemplate();
            }

            // if still null then that's a problem
            if (page == null) {
                throw new WebloggerException("Could not lookup default page "
                        + "for weblog " + weblog.getHandle());
            }
        } catch (Exception e) {
            log.error(
                    "Error getting default page for weblog "
                            + weblog.getHandle(), e);
        }

        // set the content type
        response.setContentType("text/html; charset=utf-8");

        // looks like we need to render content
        Map<String, Object> model = new HashMap<>();
        try {
            PageContext pageContext = JspFactory.getDefaultFactory()
                    .getPageContext(this, request, response, "", false, RollerConstants.EIGHT_KB_IN_BYTES,
                            true);

            // populate the rendering model
            Map<String, Object> initData = new HashMap<>();
            initData.put("request", request);
            initData.put("pageContext", pageContext);

            // this is a little hacky, but nothing we can do about it
            // we need the 'weblogRequest' to be a pageRequest so other models
            // are properly loaded, which means that searchRequest needs its
            // own custom initData property aside from the standard
            // weblogRequest.
            // possible better approach is make searchRequest extend
            // pageRequest.
            WeblogPageRequest pageRequest = new WeblogPageRequest();
            pageRequest.setWeblogHandle(searchRequest.getWeblogHandle());
            pageRequest.setWeblogCategoryName(searchRequest
                    .getWeblogCategoryName());
            pageRequest.setLocale(searchRequest.getLocale());
            pageRequest.setDeviceType(searchRequest.getDeviceType());
            initData.put("parsedRequest", pageRequest);
            initData.put("searchRequest", searchRequest);

            // define url strategy
            initData.put("urlStrategy", WebloggerFactory.getWeblogger()
                    .getUrlStrategy());

            // Load models for pages
            String searchModels = WebloggerConfig
                    .getProperty("rendering.searchModels");
            ModelLoader.loadModels(searchModels, model, initData, true);

            // Load special models for site-wide blog
            if (WebloggerRuntimeConfig.isSiteWideWeblog(weblog.getHandle())) {
                String siteModels = WebloggerConfig
                        .getProperty("rendering.siteModels");
                ModelLoader.loadModels(siteModels, model, initData, true);
            }

        } catch (WebloggerException ex) {
            log.error("Error loading model objects for page", ex);

            if (!response.isCommitted()) {
                response.reset();
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // lookup Renderer we are going to use
        Renderer renderer;
        try {
            log.debug("Looking up renderer");
            renderer = RendererManager.getRenderer(page, deviceType);
        } catch (Exception e) {
            // nobody wants to render my content :(
            log.error("Couldn't find renderer for rsd template", e);

            if (!response.isCommitted()) {
                response.reset();
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // render content
        CachedContent rendererOutput = new CachedContent(RollerConstants.FOUR_KB_IN_BYTES);
        try {
            log.debug("Doing rendering");
            renderer.render(model, rendererOutput.getCachedWriter());

            // flush rendered output and close
            rendererOutput.flush();
            rendererOutput.close();
        } catch (Exception e) {
            // bummer, error during rendering
            log.error("Error during rendering for rsd template", e);

            if (!response.isCommitted()) {
                response.reset();
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // post rendering process

        // flush rendered content to response
        log.debug("Flushing response output");
        response.setContentLength(rendererOutput.getContent().length);
        response.getOutputStream().write(rendererOutput.getContent());

        log.debug("Exiting");
    }

}
