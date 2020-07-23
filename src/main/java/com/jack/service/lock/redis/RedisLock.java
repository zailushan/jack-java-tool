package com.jack.service.lock.redis;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 分布式锁工具类
 * </p>
 *
 * @author luffy
 * @since 2019-11-14 11:27:48
 */
public class RedisLock {
    
    private RedisTemplate redisTemplate;
    public static final RedisSerializer<String> DEFAULT_SERIALIZER = new StringRedisSerializer();
    
    private final static Logger log = LoggerFactory.getLogger(RedisLock.class);
    
    /**
     * 记录枷锁成功的线程id
     */
    private volatile Long pid;
    /**
     * 加锁key
     */
    private String key;
    /**
     * 失效时间
     */
    private int expire;
    /**
     * 时间单位
     */
    private TimeUnit timeUnit;
    /**
     * 重试次数
     */
    private int retry;
    /**
     * 重试间隔时间
     */
    private long interval;
    
    public RedisLock(RedisTemplate redisTemplate, String key) {
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.retry = LockConstants.Default.RETRY;
        this.key = this.buildKey();
    }
    
    public RedisLock(RedisTemplate redisTemplate, String key, int expire) {
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.expire = expire;
        this.key = this.buildKey();
    }
    
    public RedisLock(RedisTemplate redisTemplate, String key, int expire, TimeUnit timeUnit) {
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.expire = expire;
        this.timeUnit = timeUnit;
        this.key = this.buildKey();
    }
    
    public RedisLock(RedisTemplate redisTemplate, String key, int expire, TimeUnit timeUnit, int retry) {
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.expire = expire;
        this.timeUnit = timeUnit;
        this.retry = retry;
        this.key = this.buildKey();
    }
    
    public RedisLock(RedisTemplate redisTemplate, String key, int expire, TimeUnit timeUnit, int retry, long interval) {
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.expire = expire;
        this.timeUnit = timeUnit;
        this.retry = retry;
        this.interval = interval;
        this.key = this.buildKey();
    }
    
    /**
     * <p>
     * 获取加锁Key
     * </p>
     *
     * @return java.lang.String
     * @author Luffy
     * @since 2019-07-23 16:25:12
     */
    private String buildKey() {
        return Optional
                .ofNullable(this.key)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new NullPointerException("lock key can not be empty..."))
                .concat(LockConstants.KEY_SUFFIX);
    }
    
    /**
     * <p>
     * 获取加锁Key
     * </p>
     *
     * @param origin 原始key
     * @return java.lang.String
     * @author Luffy
     * @since 2019-07-23 16:25:12
     */
    private String buildKey(String origin) {
        return Optional
                .ofNullable(origin)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new NullPointerException("lock key can not be empty..."))
                .concat(LockConstants.KEY_SUFFIX);
    }
    
    
    /**
     * <p>
     * 等待锁,会重试直到重试次数结束,或者拿到锁
     * </p>
     *
     * @return boolean
     * @author luffy
     * @since 2019-11-14 17:38:22
     */
    public boolean lock() {
        if (this.expire <= 0) {
            this.expire = LockConstants.Default.TIMEOUT;
        }
        if (null == this.timeUnit) {
            this.timeUnit = TimeUnit.MILLISECONDS;
        }
        if (this.retry <= 0) {
            this.retry = LockConstants.Default.RETRY;
        }
        if (this.interval <= 0L) {
            this.interval = LockConstants.Default.INTERVAL;
        }
        return this.lock(this.key, this.expire, this.timeUnit, this.retry, this.interval);
    }

    /**
     * <p>
     * 不等待锁,获取锁且不重试,如果未获取到锁,不等待和重试
     * </p>
     *
     * @return boolean
     * @author luffy
     * @since 2019-11-14 17:35:16
     */
    public boolean trylock() {
        if (this.expire <= 0) {
            this.expire = LockConstants.Default.TIMEOUT;
        }
        if (null == this.timeUnit) {
            this.timeUnit = TimeUnit.MILLISECONDS;
        }
        return this.lockFun(this.key, this.expire, this.timeUnit);
    }
    
    
    /**
     * <p>
     * 等待锁,会重试直到重试次数结束,或者拿到锁
     * </p>
     *
     * @param key      加锁Key
     * @param expire   失效时间
     * @param retry    重试次数
     * @param interval 重试间隔时间
     * @return boolean
     * @author luffy
     * @since 2019-05-29 01:31:26
     */
    private boolean lock(final String key, final int expire, final TimeUnit timeUnit, int retry, final long interval) {
        boolean lock;
        // 如果获取锁失败，按照传入的重试次数进行重试,并递减重试次数
        while (!(lock = this.lockFun(key, expire, timeUnit)) && retry-- > 0) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return lock;
    }
    
    
    /**
     * <p>
     * 加锁函数
     * 注意一定要对key和value序列化,否则会报错
     * io.lettuce.core.RedisException: io.netty.handler.codec.EncoderException: Cannot encode command. Please release the connection as the connection state may be out of sync.
     * </p>
     *
     * @param key    锁Key
     * @param expire 超时时间
     * @return boolean
     * @author luffy
     * @since 2019-05-29 12:42:43
     */
    private synchronized boolean lockFun(final String key, final long expire, final TimeUnit timeUnit) {
        //获取超时时间
        final long time = this.getTime(expire, timeUnit);
        String script = "if redis.call('setNx', KEYS[1], ARGV[1]) == 1 " +
                        "then return redis.call('expire', KEYS[1], ARGV[2]) " +
                        "else return 0 end";
        String val = String.valueOf(System.currentTimeMillis() + expire + 1);
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Object result = redisTemplate.execute(redisScript,
                                                DEFAULT_SERIALIZER,
                                                DEFAULT_SERIALIZER,
                                                Collections.singletonList(key),
                                            val, time + "");
        if (LockConstants.SET_SUCCESS.equals(result)) {
            this.pid = Thread.currentThread().getId();
            return true;
        }
        return false;
    }
    
    /**
     * <p>
     * 释放锁
     * 注意一定要对key和value序列化,否则会报错
     * io.lettuce.core.RedisException: io.netty.handler.codec.EncoderException: Cannot encode command. Please release the connection as the connection state may be out of sync.
     * </p>
     *
     * @return boolean
     * @author luffy
     * @since 2019-05-29 01:32:24
     */
    public synchronized boolean unlock() {
        if (StringUtils.isBlank(this.key)) {
            log.debug("lock key can not be empty");
            return false;
        }
        if (!this.pid.equals(Thread.currentThread().getId())) {
            log.debug("current lock is no locked");
            return false;
        }
        this.redisTemplate.delete(this.key);
        this.pid = null;
        // 返回结果不是OK,则加锁失败
        return true;
    }
    
    
    /**
     * <p>
     * 释放资源,异步操作Redis,连接不能保持同步,必须释放
     * </p>
     *
     * @param connection Redis 连接对象
     * @return void
     * @author luffy
     * @since 2019-05-29 17:24:45
     */
    private void release(RedisConnection connection) {
        try {
            Optional
                    .ofNullable(connection)
                    .filter(c -> !c.isClosed())
                    .ifPresent(RedisConnection::close);
        } catch (Exception e) {
            //log.error("release connection fail.", e);
        }
    }
    
    /**
     * <p>
     * 根据时间单位取时间毫秒
     * </p>
     *
     * @param time     时间
     * @param timeUnit 时间单位
     * @return long 毫秒
     * @author Luffy
     * @since 2019-07-23 16:58:27
     */
    private long getTime(final long time, final TimeUnit timeUnit) {
        switch (timeUnit) {
            case DAYS:
                return TimeUnit.DAYS.toMillis(time);
            case HOURS:
                return TimeUnit.HOURS.toMillis(time);
            case MINUTES:
                return TimeUnit.MINUTES.toMillis(time);
            default:
                return time;
        }
    }
    
    /**
     * <p>
     * 重新设置现有锁的时间
     * </p>
     *
     * @param time     延长时间
     * @param timeUnit 时间单位
     * @return boolean
     * @author Tank
     * @since 2019-10-23 14:46:38
     */
    public synchronized boolean resetTime(final int time, final TimeUnit timeUnit) {
        Boolean expire = this.redisTemplate.expire(this.key, time, timeUnit);
        return null == expire ? false : expire;
    }
    
    /**
     * <p>
     * 取得锁的剩余时间
     * </p>
     * @return long 毫秒
     * @author Tank
     * @since 2019-10-23 15:52:12
     */
    public synchronized long getExpire() {
        Long expire = this.redisTemplate.getExpire(this.key);
        return null == expire ? 0L : expire;
    }
}
