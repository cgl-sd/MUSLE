package com.musclejournal.app.domain

/** Selection stays a draft until one atomic save records the performed actions. */
object TrainingRecord {
    fun save(j:Journal,ids:List<String>,now:Long=System.currentTimeMillis()):Journal {
        require(ids.isNotEmpty() && ids.distinct().size==ids.size)
        ids.forEach {j.exercise(it)}
        val source=if(j.active()==null)Actions.start(j,Course(name="今日训练",exercises=ids.map {CourseExercise(it)}),now=now)else j
        val active=source.active()!!
        val existing=ExerciseProgress.completed(source,active).associateBy {it.exerciseId}
        val selected=active.copy(exerciseIds=ids,completedActions=ids.map {existing[it]?:CompletedAction(it,now)},modifiedAt=now)
        return Actions.finish(source.copy(sessions=source.sessions.map {if(it.id==active.id)selected else it}),now=now)
    }
}
