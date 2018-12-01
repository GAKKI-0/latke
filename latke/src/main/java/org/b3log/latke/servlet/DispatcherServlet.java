/*
 * Copyright (c) 2009-2018, b3log.org & hacpai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.b3log.latke.servlet;

import org.b3log.latke.servlet.converter.ConvertSupport;
import org.b3log.latke.servlet.function.ContextHandler;
import org.b3log.latke.servlet.handler.*;
import org.b3log.latke.servlet.renderer.AbstractHTTPResponseRenderer;
import org.b3log.latke.servlet.renderer.HTTP404Renderer;
import org.b3log.latke.servlet.renderer.HTTP500Renderer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Dispatch-controller for HTTP request dispatching.
 *
 * @author <a href="mailto:wmainlove@gmail.com">Love Yao</a>
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.2.2, Dec 2, 2018
 */
public final class DispatcherServlet extends HttpServlet {

    /**
     * Handlers.
     */
    public static final List<Handler> HANDLERS = new ArrayList<>();

    static {
        HANDLERS.add(new RequestPrepareHandler());
        HANDLERS.add(new RequestDispatchHandler());
        HANDLERS.add(new ArgsHandler());
        HANDLERS.add(new AdviceHandler());
        // here are ext handlers if exist
        HANDLERS.add(new MethodInvokeHandler());
    }

    @Override
    public void init() {
        // static resource handling must be the first one
        HANDLERS.add(0, new StaticResourceHandler(getServletContext()));
    }

    /**
     * Add the specified ext handler into handlers chain.
     *
     * @param handler the specified ext handler
     */
    public static void addHandler(final Handler handler) {
        DispatcherServlet.HANDLERS.add(DispatcherServlet.HANDLERS.size() - 1, handler);
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
        final HTTPRequestContext httpRequestContext = new HTTPRequestContext();

        httpRequestContext.setRequest(req);
        httpRequestContext.setResponse(resp);
        final HttpControl httpControl = new HttpControl(HANDLERS.iterator(), httpRequestContext);

        try {
            httpControl.nextHandler();
        } catch (final Exception e) {
            httpRequestContext.setRenderer(new HTTP500Renderer(e));
        }

        result(httpRequestContext);
    }

    /**
     * Do HTTP response.
     *
     * @param context {@link HTTPRequestContext}
     */
    public static void result(final HTTPRequestContext context) {
        final HttpServletResponse response = context.getResponse();
        if (response.isCommitted()) { // Response sends redirect or error
            return;
        }

        AbstractHTTPResponseRenderer renderer = context.getRenderer();
        if (null == renderer) {
            renderer = new HTTP404Renderer();
        }
        renderer.render(context);
    }

    private static List<Router> routers = new ArrayList<>();

    public static void mapping() {
        for (final Router router : routers) {
            final ProcessorInfo processorInfo = router.toProcessorInfo();
            RequestDispatchHandler.addProcessorInfo(processorInfo);
        }
    }

    public static Router route() {
        final Router ret = new Router();
        routers.add(ret);

        return ret;
    }

    public static class Router {
        private List<String> requestURIPattenrs = new ArrayList<>();
        private URIPatternMode uriPatternMode = URIPatternMode.ANT_PATH;
        private List<HTTPRequestMethod> httpRequestMethods = new ArrayList<>();
        private Class<? extends ConvertSupport> convertSupport = ConvertSupport.class;
        private ContextHandler handler;
        private Method method;

        public Router uri(final String requestURIPattern) {
            requestURIPattenrs.add(requestURIPattern);

            return this;
        }

        public Router uriMode(final URIPatternMode uriPatternMode) {
            this.uriPatternMode = uriPatternMode;

            return this;
        }

        public Router get() {
            httpRequestMethods.add(HTTPRequestMethod.GET);

            return this;
        }

        public Router post() {
            httpRequestMethods.add(HTTPRequestMethod.POST);

            return this;
        }

        public Router delete() {
            httpRequestMethods.add(HTTPRequestMethod.DELETE);

            return this;
        }

        public Router put() {
            httpRequestMethods.add(HTTPRequestMethod.PUT);

            return this;
        }

        public Router head() {
            httpRequestMethods.add(HTTPRequestMethod.HEAD);

            return this;
        }

        public Router options() {
            httpRequestMethods.add(HTTPRequestMethod.OPTIONS);

            return this;
        }

        public Router trace() {
            httpRequestMethods.add(HTTPRequestMethod.TRACE);

            return this;
        }

        public void handler(final ContextHandler handler) {
            this.handler = handler;

            final Class clazz = handler.getClass();
            System.out.println(clazz.hashCode());
            final Method method = clazz.getDeclaredMethods()[0];
            this.method = method;
        }

        ProcessorInfo toProcessorInfo() {
            final ProcessorInfo ret = new ProcessorInfo();
            ret.setPattern(requestURIPattenrs.toArray(new String[0]));
            ret.setUriPatternMode(uriPatternMode);
            ret.setHttpMethod(httpRequestMethods.toArray(new HTTPRequestMethod[0]));
            ret.setConvertClass(convertSupport);
            ret.setInvokeHolder(method);
            ret.setHandler(handler);

            return ret;
        }
    }
}
