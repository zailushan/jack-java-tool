package com.jack.service.lock.redis;

/**
 * <p>
 *  分布式锁常量池
 * </p>
 * @author luffy
 * @since 2019-05-29 01:57:32
 */
@SuppressWarnings("unused")
public interface LockConstants {

    /**
     * <p>
     *  锁默认阈值定义
     * </p>
     * @author luffy
     * @since 2019-05-29 20:54:05
     */
    interface Default{
        /** 默认超时时间 */
        int TIMEOUT = 30000;
        /** 默认重试次数 */
        int RETRY = 2000;
        /** 默认重试间隔时间 */
        long INTERVAL = 100L;
    }
   
    /** 使用RedisAsyncCommands 或 RedisAdvancedClusterAsyncCommands 执行set命令返回成功的标识 */
    Long SET_SUCCESS = 1L;
    /** 释放锁失败 */
    Long UNLOCK_FAIL = -1L;
    /** 释放锁成功 */
    Long UNLOCK_SUCCESS = 1L;
    /** 加锁key后缀 */
    String KEY_SUFFIX = "_sqk_lock";
}
