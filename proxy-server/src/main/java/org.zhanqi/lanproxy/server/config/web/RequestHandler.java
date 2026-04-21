package org.zhanqi.qiproxy.server.config.web;

import io.netty.handler.codec.http.FullHttpRequest;

/**
 * 接口请求处理
 *
 * @author zhanqi
 *
 */
public interface RequestHandler {

    /**
     * 请求处理
     *
     * @param request
     * @return
     */
    ResponseInfo request(FullHttpRequest request);
}