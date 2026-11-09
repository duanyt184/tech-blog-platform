package com.techblog.platform.dto;

import java.util.List;

/**
 * 技术分类的文章数量统计。
 *
 * <p>只统计社区已发布（PUBLISHED）且未逻辑删除的文章：草稿、待审核、已退回都不应出现在公开侧边栏里。</p>
 *
 * @param total      已发布文章总数
 * @param categories 每个分类对应的已发布文章数，按数据库分类 ID 排序
 */
public record CategoryStatsResponse(long total, List<CategoryCount> categories) {

    public record CategoryCount(String name, long count) {
    }
}
