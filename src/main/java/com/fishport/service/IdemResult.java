package com.fishport.service;

/** 幂等结果：重复请求返回原处理结果并标记 duplicate */
public record IdemResult<T>(T payload, boolean duplicate) {

    public static <T> IdemResult<T> first(T payload) {
        return new IdemResult<>(payload, false);
    }

    public static <T> IdemResult<T> duplicated(T payload) {
        return new IdemResult<>(payload, true);
    }
}
