package com.jack.service.lock.redis;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * <p>
 *  Lua 脚本命令集
 * </p>
 * @author luffy
 * @since 2019-05-29 00:22:37
 */
public interface LuaCommands {

    /**
     *  execute最终调用的RedisConnection的eval方法将LUA脚本交给Redis服务端执行
     *  这个操作通过Lua 脚本执行setnx 设置锁key
     */
    DefaultRedisScript<Long> LOCK_LUA_SCRIPT = new DefaultRedisScript<>(
            "if redis.call(\"setnx\", KEYS[1], KEYS[2]) == 1 then return redis.call(\"pexpire\", KEYS[1], KEYS[3]) else return 0 end"
            , Long.class
    );

    /**
     * 先判断key值是否与传入的requestId一致，如果一致则删除key，如果不一致返回-1表示key可能已经过期或被其他客户端占用，避免误删,redis.call("del",keys);如果删除成功返回1
     */
    String UNLOCK_LUA_SCRIPT = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then return redis.call(\"del\",KEYS[1]) else return -1 end";

    /**
     * NX： 表示只有当锁定资源不存在的时候才能 SET 成功。利用 Redis 的原子性，保证了只有第一个请求的线程才能获得锁，而之后的所有线程在锁定资源被释放之前都不能获得锁。
     */
    String NX = "NX";

    /**
     * PX： expire 表示锁定的资源的自动过期时间，单位是毫秒。具体过期时间根据实际场景而定
     */
    String PX = "PX";

}
