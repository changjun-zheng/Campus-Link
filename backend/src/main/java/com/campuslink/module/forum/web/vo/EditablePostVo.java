package com.campuslink.module.forum.web.vo;

import com.campuslink.module.forum.application.cmd.ForumResults.EditablePost;

import java.util.List;

/**
 * 编辑原文视图（F-FORUM-007 / CR-128）：受保护端点，**仅作者本人可取得**。
 * 含原始 Markdown（公开详情只下发渲染后的 contentHtml），供编辑页预填；不复制详情里的互动计数与昵称。
 */
public record EditablePostVo(Long id, String boardCode, String boardName, String title,
                             String contentMd, List<String> tags, boolean anonymous) {

    public static EditablePostVo from(EditablePost post) {
        return new EditablePostVo(post.id(), post.boardCode(), post.boardName(), post.title(),
                post.contentMd(), post.tags(), post.anonymous());
    }
}
