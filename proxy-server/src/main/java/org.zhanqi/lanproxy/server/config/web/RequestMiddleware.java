package org.zhanqi.qiproxy.server.config.web;

import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 请求拦截器
 *
 * @author zhanqi
 *
 */
public interface RequestMiddleware {

    /**
     * 请求预处理
     *
     * @param request
     */
    void preRequest(FullHttpRequest request);
}
