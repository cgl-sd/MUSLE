package com.musclejournal.app.domain

/** Stable identifiers shared by exercise mappings and future 3D mesh bindings. */
enum class MuscleId(val stableId: String, val displayName: String) {
    CHEST("chest", "胸肌"),
    SHOULDERS("shoulders", "三角肌"),
    TRAPEZIUS("trapezius", "斜方肌"),
    LATS("lats", "背阔肌"),
    LOWER_BACK("lower_back", "下背部"),
    BICEPS("biceps", "肱二头肌"),
    TRICEPS("triceps", "肱三头肌"),
    FOREARMS("forearms", "前臂"),
    ABS("abs", "腹直肌"),
    OBLIQUES("obliques", "腹斜肌"),
    GLUTES("glutes", "臀肌"),
    QUADRICEPS("quadriceps", "股四头肌"),
    HAMSTRINGS("hamstrings", "腘绳肌"),
    CALVES("calves", "小腿肌群"),
}
