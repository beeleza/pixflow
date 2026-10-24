package com.beeleza.pixflow.adapter.out.redis;

import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTransferLockAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private RedisTransferLockAdapter lock;

    @Autowired
    private StringRedisTemplate redis;

    private final IdempotencyKey key = IdempotencyKey.of(UUID.randomUUID().toString());

    @Test
    void grantsTheLockToOneHolderAtATime() {
        Optional<String> first = lock.acquire(key);
        Optional<String> second = lock.acquire(key);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    @Test
    void releasesTheLockForItsOwner() {
        String token = lock.acquire(key).orElseThrow();

        lock.release(key, token);

        assertThat(lock.acquire(key)).isPresent();
    }

    // A holder whose lock expired must not delete the lock someone else acquired afterwards.
    @Test
    void doesNotReleaseALockOwnedBySomeoneElse() {
        String token = lock.acquire(key).orElseThrow();

        lock.release(key, "stale-token");

        assertThat(lock.acquire(key)).isEmpty();
        assertThat(redis.opsForValue().get(redisKey())).isEqualTo(token);
    }

    @Test
    void locksExpireAfterTheConfiguredTtl() {
        lock.acquire(key);

        Long ttlSeconds = redis.getExpire(redisKey(), TimeUnit.SECONDS);

        assertThat(ttlSeconds).isBetween(1L, 10L);
    }

    @Test
    void locksAreIndependentPerIdempotencyKey() {
        lock.acquire(key);

        assertThat(lock.acquire(IdempotencyKey.of(UUID.randomUUID().toString()))).isPresent();
    }

    private String redisKey() {
        return "pix:lock:idempotency:" + key.value();
    }
}
