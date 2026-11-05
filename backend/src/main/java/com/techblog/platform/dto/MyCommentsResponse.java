package com.techblog.platform.dto;

import java.util.List;

/**
 * 「评论」页：我写过的评论 + 别人对我文章的评论。
 */
public record MyCommentsResponse(List<CommentResponse> written, List<CommentResponse> received) {
}
