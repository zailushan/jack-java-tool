package com.jack.service.lock.redis;

import java.util.concurrent.TimeUnit;

/**
 * 缓存常量池
 * @author luffy
 * @since 2019-05-30 01:42:41
 */
@SuppressWarnings("unused")
public interface CacheConstants {

    /**
     * 缓存默认阈值定义
     * @author luffy
     * @since 2019-05-30 01:49:00
     */
    interface Default{
        /** 默认的失效时间,默认不失效 */
        Long TIME_OUT = -1L;
        /** 默认的时间单位,分钟 */
        TimeUnit TIME_UNIT = TimeUnit.MINUTES;
    }
}
