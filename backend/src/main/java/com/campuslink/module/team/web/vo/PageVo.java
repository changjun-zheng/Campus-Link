package com.campuslink.module.team.web.vo;

import java.util.List;

/** 组队分页载体（CR-137）：与 forum / messaging 同形状（list / total / page / size） */
public record PageVo<T>(List<T> list, long total, int page, int size) {
}
