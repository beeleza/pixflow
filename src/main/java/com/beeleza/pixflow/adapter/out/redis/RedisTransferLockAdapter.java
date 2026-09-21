package com.beeleza.pixflow.adapter.out.redis;

import com.beeleza.pixflow.application.port.out.TransferLockPort;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisTransferLockAdapter implements TransferLockPort {

    private static final String KEY_PREFIX = "pix:lock:idempotency:";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;

    public RedisTransferLockAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${pixflow.transfer-lock.ttl:10s}")
            Duration lockTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.lockTtl = lockTtl;
    }

    @Override
    public Optional<String> acquire(IdempotencyKey idempotencyKey) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(idempotencyKey), token, lockTtl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void release(IdempotencyKey idempotencyKey, String lockToken) {
        redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key(idempotencyKey)), lockToken);
    }

    private String key(IdempotencyKey idempotencyKey) {
        return KEY_PREFIX + idempotencyKey.value();
    }
}
