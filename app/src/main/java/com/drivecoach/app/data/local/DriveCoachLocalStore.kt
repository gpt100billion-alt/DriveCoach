package com.drivecoach.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.drivecoach.app.data.simulation.DemoScenarioCatalog
import com.drivecoach.app.domain.analysis.DrivingAnalysisEngine
import com.drivecoach.app.domain.model.AnalysisStatus
import com.drivecoach.app.domain.model.DataQualityFlag
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.domain.model.PersonalHabitGoal
import com.drivecoach.app.domain.model.ScoringWeights
import com.drivecoach.app.domain.model.TelemetrySample
import com.drivecoach.app.domain.model.TurnSignalState
import org.json.JSONArray
import org.json.JSONObject

/**
 * 로컬 데이터베이스 및 설정 저장소 (섹션 23, 24)
 * - 모든 주행 기록, 텔레메트리 샘플, 분석 결과, 사용자 설정, 학습 완료 상태를 기기 내부(SQLite + SharedPreferences)에만 저장합니다.
 * - 개인정보 보호 원칙에 따라 외부 서버로 자동 전송하지 않으며, 개별/전체 삭제 및 데모 데이터 복원 기능을 제공합니다.
 */
class DriveCoachLocalStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dbHelper = SessionSqliteHelper(appContext)

    fun loadScoringWeights(): ScoringWeights {
        val accel = prefs.getFloat(KEY_WEIGHT_ACCEL, 0.25f)
        val brake = prefs.getFloat(KEY_WEIGHT_BRAKE, 0.35f)
        val steer = prefs.getFloat(KEY_WEIGHT_STEER, 0.30f)
        val cons = prefs.getFloat(KEY_WEIGHT_CONS, 0.10f)
        return ScoringWeights(
            accelerationWeight = accel,
            brakingWeight = brake,
            steeringWeight = steer,
            consistencyWeight = cons,
        )
    }

    fun saveScoringWeights(weights: ScoringWeights) {
        prefs.edit()
            .putFloat(KEY_WEIGHT_ACCEL, weights.accelerationWeight)
            .putFloat(KEY_WEIGHT_BRAKE, weights.brakingWeight)
            .putFloat(KEY_WEIGHT_STEER, weights.steeringWeight)
            .putFloat(KEY_WEIGHT_CONS, weights.consistencyWeight)
            .apply()
    }

    fun loadPreferredDataSourceMode(): DataSourceType {
        val raw = prefs.getString(KEY_PREFERRED_SOURCE, DataSourceType.DEMO.name)
        return runCatching { DataSourceType.valueOf(raw ?: DataSourceType.DEMO.name) }
            .getOrDefault(DataSourceType.DEMO)
    }

    fun savePreferredDataSourceMode(mode: DataSourceType) {
        prefs.edit().putString(KEY_PREFERRED_SOURCE, mode.name).apply()
    }

    fun loadCompletedEducationIds(): Set<String> {
        return prefs.getStringSet(KEY_COMPLETED_EDUCATION, emptySet())?.toSet() ?: emptySet()
    }

    fun toggleEducationCompleted(topicId: String): Set<String> {
        val current = loadCompletedEducationIds().toMutableSet()
        if (topicId in current) {
            current.remove(topicId)
        } else {
            current.add(topicId)
        }
        prefs.edit().putStringSet(KEY_COMPLETED_EDUCATION, current).apply()
        return current
    }

    fun loadPersonalGoals(): List<PersonalHabitGoal> {
        val enabledIds = prefs.getStringSet(
            KEY_ENABLED_GOALS,
            setOf("goal_smooth_brake", "goal_gentle_start", "goal_steady_turn"),
        ) ?: emptySet()

        return defaultGoals().map { goal ->
            goal.copy(isEnabled = goal.id in enabledIds)
        }
    }

    fun togglePersonalGoal(goalId: String): List<PersonalHabitGoal> {
        val currentGoals = loadPersonalGoals()
        val updatedIds = currentGoals.mapNotNull { g ->
            val nextEnabled = if (g.id == goalId) !g.isEnabled else g.isEnabled
            g.id.takeIf { nextEnabled }
        }.toSet()
        prefs.edit().putStringSet(KEY_ENABLED_GOALS, updatedIds).apply()
        return loadPersonalGoals()
    }

    fun resetSettingsToDefault() {
        prefs.edit()
            .remove(KEY_WEIGHT_ACCEL)
            .remove(KEY_WEIGHT_BRAKE)
            .remove(KEY_WEIGHT_STEER)
            .remove(KEY_WEIGHT_CONS)
            .remove(KEY_PREFERRED_SOURCE)
            .remove(KEY_ENABLED_GOALS)
            .apply()
    }

    /**
     * 저장된 모든 주행 세션을 최신순으로 불러옵니다.
     * 최초 실행 시에만 기본 5개 데모 시나리오 세션을 자동 시딩합니다.
     */
    fun loadAllSessions(weights: ScoringWeights = loadScoringWeights()): List<DrivingSession> {
        val isInitialized = prefs.getBoolean(KEY_INITIAL_DEMO_SEEDED, false)
        if (!isInitialized) {
            val initialDemos = DemoScenarioCatalog.buildInitialDemoSessions(weights = weights)
            initialDemos.forEach { saveSession(it) }
            prefs.edit().putBoolean(KEY_INITIAL_DEMO_SEEDED, true).apply()
            return initialDemos.sortedByDescending { it.startTimeMs }
        }
        return dbHelper.queryAllSessions(weights)
    }

    fun saveSession(session: DrivingSession) {
        dbHelper.upsertSession(session)
    }

    fun deleteSession(sessionId: String) {
        dbHelper.deleteById(sessionId)
    }

    fun deleteAllSessions() {
        dbHelper.deleteAll()
        // 사용자가 전체 삭제를 수행한 경우 빈 상태를 확인할 수 있도록 시딩 완료 플래그 유지
        prefs.edit().putBoolean(KEY_INITIAL_DEMO_SEEDED, true).apply()
    }

    fun resetDemoSessions(weights: ScoringWeights = loadScoringWeights()): List<DrivingSession> {
        dbHelper.deleteAll()
        val demos = DemoScenarioCatalog.buildInitialDemoSessions(weights = weights)
        demos.forEach { saveSession(it) }
        prefs.edit().putBoolean(KEY_INITIAL_DEMO_SEEDED, true).apply()
        return demos.sortedByDescending { it.startTimeMs }
    }

    companion object {
        private const val PREFS_NAME = "drivecoach_local_prefs"
        private const val KEY_INITIAL_DEMO_SEEDED = "initial_demo_seeded_v1"
        private const val KEY_WEIGHT_ACCEL = "weight_accel"
        private const val KEY_WEIGHT_BRAKE = "weight_brake"
        private const val KEY_WEIGHT_STEER = "weight_steer"
        private const val KEY_WEIGHT_CONS = "weight_cons"
        private const val KEY_PREFERRED_SOURCE = "preferred_data_source"
        private const val KEY_COMPLETED_EDUCATION = "completed_education_ids"
        private const val KEY_ENABLED_GOALS = "enabled_personal_goal_ids"

        fun defaultGoals(): List<PersonalHabitGoal> = listOf(
            PersonalHabitGoal(
                id = "goal_smooth_brake",
                title = "일반 감속 구간에서 후반 급감속 줄이기",
                description = "정지 예측 구간에서 미리 가속 페달을 떼어 제동 안정성 86점 이상 달성하기",
                targetMetricKey = "BRAKING",
                targetScore = 86,
                isEnabled = true,
            ),
            PersonalHabitGoal(
                id = "goal_gentle_start",
                title = "출발 초기 2초간 부드러운 가속 유지하기",
                description = "급가속 후보 없이 가속 안정성 86점 이상 달성하기",
                targetMetricKey = "ACCELERATION",
                targetScore = 86,
                isEnabled = true,
            ),
            PersonalHabitGoal(
                id = "goal_steady_turn",
                title = "저속 교차로 회전 전 감속으로 수정 조향 줄이기",
                description = "회전 진입 전 감속을 마쳐 조향 안정성 86점 이상 달성하기",
                targetMetricKey = "STEERING",
                targetScore = 86,
                isEnabled = true,
            ),
        )
    }
}

private class SessionSqliteHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                scenario_id TEXT,
                start_time_ms INTEGER NOT NULL,
                end_time_ms INTEGER NOT NULL,
                duration_sec INTEGER NOT NULL,
                distance_km REAL NOT NULL,
                data_source TEXT NOT NULL,
                samples_json TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun upsertSession(session: DrivingSession) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", session.id)
            put("title", session.title)
            put("subtitle", session.subtitle)
            put("scenario_id", session.scenarioId)
            put("start_time_ms", session.startTimeMs)
            put("end_time_ms", session.endTimeMs)
            put("duration_sec", session.durationSec)
            put("distance_km", session.distanceKm.toDouble())
            put("data_source", session.dataSource.name)
            put("samples_json", serializeSamples(session.telemetrySamples))
        }
        db.insertWithOnConflict(TABLE_SESSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun queryAllSessions(weights: ScoringWeights): List<DrivingSession> {
        val db = readableDatabase
        val sessions = mutableListOf<DrivingSession>()
        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "start_time_ms DESC",
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val titleIdx = c.getColumnIndexOrThrow("title")
            val subIdx = c.getColumnIndexOrThrow("subtitle")
            val scenIdx = c.getColumnIndexOrThrow("scenario_id")
            val startIdx = c.getColumnIndexOrThrow("start_time_ms")
            val endIdx = c.getColumnIndexOrThrow("end_time_ms")
            val durIdx = c.getColumnIndexOrThrow("duration_sec")
            val distIdx = c.getColumnIndexOrThrow("distance_km")
            val sourceIdx = c.getColumnIndexOrThrow("data_source")
            val samplesIdx = c.getColumnIndexOrThrow("samples_json")

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val title = c.getString(titleIdx)
                val subtitle = c.getString(subIdx)
                val scenarioId = if (c.isNull(scenIdx)) null else c.getString(scenIdx)
                val startTimeMs = c.getLong(startIdx)
                val endTimeMs = c.getLong(endIdx)
                val durationSec = c.getInt(durIdx)
                val distanceKm = c.getDouble(distIdx).toFloat()
                val source = runCatching { DataSourceType.valueOf(c.getString(sourceIdx)) }
                    .getOrDefault(DataSourceType.DEMO)
                val samples = deserializeSamples(c.getString(samplesIdx), source)

                val rawSession = DrivingSession(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    scenarioId = scenarioId,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    durationSec = durationSec,
                    distanceKm = distanceKm,
                    averageSpeedKph = 0f,
                    maximumSpeedKph = 0f,
                    telemetrySamples = samples,
                    detectedEvents = emptyList(),
                    analysisStatus = AnalysisStatus.PENDING,
                    analysisResult = null,
                    dataSource = source,
                )
                sessions.add(DrivingAnalysisEngine.analyzeSession(rawSession, weights))
            }
        }
        return sessions
    }

    fun deleteById(id: String) {
        writableDatabase.delete(TABLE_SESSIONS, "id = ?", arrayOf(id))
    }

    fun deleteAll() {
        writableDatabase.delete(TABLE_SESSIONS, null, null)
    }

    private fun serializeSamples(samples: List<TelemetrySample>): String {
        val arr = JSONArray()
        for (s in samples) {
            val obj = JSONObject()
            obj.put("ts", s.timestampMs)
            s.speedKph?.let { obj.put("spd", it.toDouble()) }
            s.longitudinalAccelerationMps2?.let { obj.put("acc", it.toDouble()) }
            obj.put("est", s.isAccelerationEstimated)
            s.brakePedalPercent?.let { obj.put("brkP", it.toDouble()) }
            s.brakePedalPressed?.let { obj.put("brkB", it) }
            s.acceleratorPedalPercent?.let { obj.put("accP", it.toDouble()) }
            s.steeringWheelAngleDeg?.let { obj.put("stw", it.toDouble()) }
            s.roadWheelAngleDeg?.let { obj.put("rdw", it.toDouble()) }
            s.odometerKm?.let { obj.put("odo", it.toDouble()) }
            s.tripDistanceMeters?.let { obj.put("trp", it.toDouble()) }
            obj.put("gear", s.gearPosition.name)
            obj.put("sig", s.turnSignalState.name)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeSamples(json: String, defaultSource: DataSourceType): List<TelemetrySample> {
        return runCatching {
            val arr = JSONArray(json)
            val list = ArrayList<TelemetrySample>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TelemetrySample(
                        timestampMs = obj.getLong("ts"),
                        speedKph = if (obj.has("spd")) obj.getDouble("spd").toFloat() else null,
                        longitudinalAccelerationMps2 = if (obj.has("acc")) obj.getDouble("acc").toFloat() else null,
                        isAccelerationEstimated = obj.optBoolean("est", true),
                        brakePedalPercent = if (obj.has("brkP")) obj.getDouble("brkP").toFloat() else null,
                        brakePedalPressed = if (obj.has("brkB")) obj.getBoolean("brkB") else null,
                        acceleratorPedalPercent = if (obj.has("accP")) obj.getDouble("accP").toFloat() else null,
                        steeringWheelAngleDeg = if (obj.has("stw")) obj.getDouble("stw").toFloat() else null,
                        roadWheelAngleDeg = if (obj.has("rdw")) obj.getDouble("rdw").toFloat() else null,
                        odometerKm = if (obj.has("odo")) obj.getDouble("odo").toFloat() else null,
                        tripDistanceMeters = if (obj.has("trp")) obj.getDouble("trp").toFloat() else null,
                        gearPosition = runCatching { GearPosition.valueOf(obj.optString("gear", "UNKNOWN")) }
                            .getOrDefault(GearPosition.UNKNOWN),
                        turnSignalState = runCatching { TurnSignalState.valueOf(obj.optString("sig", "UNKNOWN")) }
                            .getOrDefault(TurnSignalState.UNKNOWN),
                        dataSource = defaultSource,
                        qualityFlags = setOf(DataQualityFlag.VALID),
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val DB_NAME = "drivecoach_sessions.db"
        private const val DB_VERSION = 1
        private const val TABLE_SESSIONS = "driving_sessions"
    }
}
