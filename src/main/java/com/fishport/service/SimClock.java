package com.fishport.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 可控时钟：默认系统时钟；模拟端可固定到指定时刻，以便复现
 * “09:00 差两小时休息 / 09:30 替补完成重签 / 11:00 窗口关闭”等时间敏感场景。
 * 固定时钟统一使用 UTC 偏移，存取的都是墙钟 LocalDateTime，比较口径一致。
 */
@Component
public class SimClock extends Clock {

    private volatile Clock delegate = Clock.systemDefaultZone();

    public void setAt(LocalDateTime at) {
        this.delegate = Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    public void reset() {
        this.delegate = Clock.systemDefaultZone();
    }

    public LocalDateTime now() {
        return LocalDateTime.now(this);
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return delegate.withZone(zone);
    }

    @Override
    public Instant instant() {
        return delegate.instant();
    }
}
