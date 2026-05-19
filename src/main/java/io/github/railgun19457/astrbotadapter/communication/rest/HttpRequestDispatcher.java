package io.github.railgun19457.astrbotadapter.communication.rest;

import io.github.railgun19457.astrbotadapter.communication.auth.AuthManager;
import io.github.railgun19457.astrbotadapter.communication.auth.AuthResult;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.ProtocolInfo;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * HTTP请求路由分发器
 */
public class HttpRequestDispatcher extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final AuthManager authManager;
    private final Logger logger;
    private final Map<String, RouteHandler> routes;
    private final RateLimiter rateLimiter;

    public HttpRequestDispatcher(AuthManager authManager, Logger logger, int rateLimit) {
        this.authManager = authManager;
        this.logger = logger;
        this.routes = new ConcurrentHashMap<>();
        this.rateLimiter = rateLimit > 0 ? new RateLimiter(rateLimit) : null;
    }

    /**
     * 注册路由
     */
    public void registerRoute(String path, RouteHandler handler) {
        routes.put(path, handler);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // CORS预检请求
        if (request.method() == HttpMethod.OPTIONS) {
            handleCors(ctx, request);
            return;
        }

        String path = getPath(request.uri());

        String clientIp = getClientIp(ctx, request);
        
        // 频率限制
        if (rateLimiter != null && !rateLimiter.allowRequest(clientIp)) {
            sendResponse(ctx, request, Response.error(ErrorCode.SERVER_UNAVAILABLE, "请求过于频繁"));
            return;
        }

        // Health check is intentionally public so clients can distinguish
        // network reachability from token/configuration failures.
        if ("/api/v1/health".equals(path)) {
            sendResponse(ctx, request, buildHealthResponse());
            return;
        }

        // 认证
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        AuthResult authResult = authManager.authenticateHeader(authHeader);
        
        if (!authResult.isSuccess()) {
            sendResponse(ctx, request, Response.error(
                    ErrorCode.fromCode(authResult.getErrorCode()), authResult.getMessage()));
            return;
        }

        // 路由分发
        RouteHandler handler = routes.get(path);
        
        if (handler == null) {
            // 尝试匹配通配符路由
            handler = findMatchingRoute(path);
        }

        if (handler == null) {
            sendResponse(ctx, request, Response.error(ErrorCode.RESOURCE_NOT_FOUND));
            return;
        }

        try {
            Response response = handler.handle(request);
            sendResponse(ctx, request, response);
        } catch (Exception e) {
            logger.warning("处理REST请求异常: " + e.getMessage());
            sendResponse(ctx, request, Response.error(ErrorCode.SERVER_INTERNAL_ERROR, e.getMessage()));
        }
    }

    private RouteHandler findMatchingRoute(String path) {
        for (Map.Entry<String, RouteHandler> entry : routes.entrySet()) {
            String routePath = entry.getKey();
            if (routePath.endsWith("/*")) {
                String prefix = routePath.substring(0, routePath.length() - 2);
                if (path.startsWith(prefix)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String getPath(String uri) {
        int queryIndex = uri.indexOf('?');
        return queryIndex > 0 ? uri.substring(0, queryIndex) : uri;
    }

    private String getClientIp(ChannelHandlerContext ctx, FullHttpRequest request) {
        String forwardedFor = request.headers().get("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return ctx.channel().remoteAddress().toString();
    }

    private void handleCors(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "86400");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, Response response) {
        String json = response.toJson();
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                toHttpStatus(response),
                Unpooled.wrappedBuffer(content));
        
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        httpResponse.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(httpResponse);
        } else {
            ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private Response buildHealthResponse() {
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("status", "ok");
        ProtocolInfo.addTo(data);
        return Response.success(data);
    }

    private HttpResponseStatus toHttpStatus(Response response) {
        if (response.isSuccess()) {
            return HttpResponseStatus.OK;
        }

        int code = response.getCode();
        if (code >= 1001 && code <= 1003) {
            return HttpResponseStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.RESOURCE_NOT_FOUND.getCode()
                || code == ErrorCode.PLAYER_NOT_ONLINE.getCode()) {
            return HttpResponseStatus.NOT_FOUND;
        }
        if (code == ErrorCode.FEATURE_DISABLED.getCode()
                || code == ErrorCode.COMMAND_FILTERED.getCode()
                || code == ErrorCode.COMMAND_NO_PERMISSION.getCode()) {
            return HttpResponseStatus.FORBIDDEN;
        }
        if (code == ErrorCode.SERVER_UNAVAILABLE.getCode()) {
            String message = response.getMessage();
            if (message != null && message.contains("频繁")) {
                return HttpResponseStatus.TOO_MANY_REQUESTS;
            }
            return HttpResponseStatus.SERVICE_UNAVAILABLE;
        }
        if (code == ErrorCode.SERVER_INTERNAL_ERROR.getCode()) {
            return HttpResponseStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpResponseStatus.BAD_REQUEST;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warning("REST API异常: " + cause.getMessage());
        ctx.close();
    }

    /**
     * 路由处理器接口
     */
    @FunctionalInterface
    public interface RouteHandler {
        Response handle(FullHttpRequest request);
    }

    /**
     * 简单的频率限制器
     */
    private static class RateLimiter {
        private final int maxRequests;
        private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();

        public RateLimiter(int maxRequestsPerMinute) {
            this.maxRequests = maxRequestsPerMinute;
        }

        public boolean allowRequest(String clientIp) {
            long now = System.currentTimeMillis();
            RequestCounter counter = counters.computeIfAbsent(clientIp, k -> new RequestCounter());
            
            synchronized (counter) {
                // 每分钟重置
                if (now - counter.windowStart > 60000) {
                    counter.count = 0;
                    counter.windowStart = now;
                }
                
                if (counter.count >= maxRequests) {
                    return false;
                }
                
                counter.count++;
                return true;
            }
        }

        private static class RequestCounter {
            long windowStart = System.currentTimeMillis();
            int count = 0;
        }
    }
}
