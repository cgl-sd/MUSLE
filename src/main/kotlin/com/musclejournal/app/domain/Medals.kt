package com.musclejournal.app.domain

import java.time.DayOfWeek
import java.time.YearMonth

/** Version 1: read-only milestones derived from saved facts; opening the wall never awards records. */
data class MedalDefinition(val id:String,val title:String,val condition:String,val family:String,val threshold:Int)
data class MedalState(val definition:MedalDefinition,val acquiredAt:Long?)
object MedalCatalog {
    val definitions:List<MedalDefinition> = listOf(
        MedalDefinition("training-1","初次启程","完成1场训练","training",1),
        MedalDefinition("training-3","渐入节奏","累计完成\n3场训练","training",3),
        MedalDefinition("training-5","五次坚持","累计完成\n5场训练","training",5),
        MedalDefinition("training-10","十次积累","累计完成\n10场训练","training",10),
        MedalDefinition("training-20","二十次旅程","累计完成\n20场训练","training",20),
        MedalDefinition("training-30","三十次记录","累计完成\n30场训练","training",30),
        MedalDefinition("training-50","五十次足迹","累计完成\n50场训练","training",50),
        MedalDefinition("training-100","百次里程","累计完成\n100场训练","training",100),
        MedalDefinition("course-1","课程初体验","完成1次\n课程训练","course",1),
        MedalDefinition("course-5","课程同行","累计完成5次\n课程训练","course",5),
        MedalDefinition("course-10","课程熟悉","累计完成10次\n课程训练","course",10),
        MedalDefinition("exercise-3","动作探索","记录3种\n不同动作","exercise",3),
        MedalDefinition("exercise-5","动作拓展","记录5种\n不同动作","exercise",5),
        MedalDefinition("exercise-10","动作图鉴","记录10种\n不同动作","exercise",10),
        MedalDefinition("equipment-2","器械尝鲜","记录2类\n不同器械","equipment",2),
        MedalDefinition("equipment-3","多元训练","记录3类\n不同器械","equipment",3),
        MedalDefinition("week-days-3","规律一周","同一周3天\n完成训练","week",3),
        MedalDefinition("months-2","跨月足迹","在2个月份\n完成训练","month",2),
        MedalDefinition("months-3","习惯延续","在3个月份\n完成训练","month",3),
        MedalDefinition("record-types-2","多样记录","使用2种\n记录类型","recordType",2),
    )
    fun states(j:Journal):List<MedalState>{
        val actionIndex=ExerciseProgress.index(j)
        val actions=j.sessions.associate {it.id to actionIndex.forSession(it)}
        val sessions=j.sessions.filter {it.status!=SessionStatus.ACTIVE && it.endedAt!=null && actions[it.id].orEmpty().isNotEmpty()}.sortedWith(compareBy<Session> {it.endedAt}.thenBy {it.id})
        fun firstDistinct(n:Int,key:(Session)->List<String>):Long? {val seen=mutableSetOf<String>();return sessions.firstOrNull {seen.addAll(key(it));seen.size>=n}?.endedAt}
        return definitions.map {m->
            val at=when(m.family){
                "training"->sessions.getOrNull(m.threshold-1)?.endedAt
                "course"->sessions.filter {it.status==SessionStatus.COMPLETED && it.snapshot!=null}.getOrNull(m.threshold-1)?.endedAt
                "exercise"->firstDistinct(m.threshold){actions[it.id].orEmpty().map {r->r.exerciseId}}
                "equipment"->firstDistinct(m.threshold){actions[it.id].orEmpty().map {r->j.exercise(r.exerciseId).equipment.trim()}.filter {it.isNotBlank()}}
                "recordType"->firstDistinct(m.threshold){actions[it.id].orEmpty().map {r->j.exercise(r.exerciseId).type.name}}
                "month"->firstDistinct(m.threshold){listOf(YearMonth.from(it.date()).toString())}
                "week"->{val weeks=mutableMapOf<String,MutableSet<String>>();sessions.firstOrNull {s->val date=s.date();val key="${s.zone}:${date.with(DayOfWeek.MONDAY)}";weeks.getOrPut(key){mutableSetOf()}.add(date.toString());weeks.getValue(key).size>=m.threshold}?.endedAt}
                else->null
            }
            MedalState(m,at)
        }
    }
}
