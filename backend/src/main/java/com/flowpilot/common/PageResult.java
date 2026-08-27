package com.flowpilot.common;

import java.util.List;

/**
 * 分页响应结构。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {
}
