package com.musclejournal.app.ui

import com.musclejournal.app.domain.*

internal val SessionStatus.label:String get()=when(this){SessionStatus.ACTIVE->"进行中";SessionStatus.COMPLETED->"已结束";SessionStatus.EARLY->"提前结束"}
internal val OccurrenceStatus.label:String get()=when(this){OccurrenceStatus.PLANNED->"待训练";OccurrenceStatus.SKIPPED->"已跳过";OccurrenceStatus.REST->"休息日";OccurrenceStatus.CANCELLED->"已取消";OccurrenceStatus.COMPLETED->"已完成"}
