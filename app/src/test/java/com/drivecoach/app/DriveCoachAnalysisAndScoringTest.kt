package com.drivecoach.app

import com.drivecoach.app.data.simulation.DemoScenarioCatalog
import com.drivecoach.app.domain.analysis.DrivingAnalysisEngine
import com.drivecoach.app.domain.analysis.RuleBasedCoachingGenerator
import com.drivecoach.app.domain.model.DataQualityFlag
import com.drivecoach.app.domain.model.DrivingEventType
import com.drivecoach.app.domain.model.ScoringWeights
import com.drivecoach.app.domain.model.TelemetrySample
import com.drivecoach.app.domain.scoring.DrivingScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DriveCoach 데이터 처리, 이벤트 후보 감지, 설명형 점수 산출, 시나리오 일관성 단위 테스트 (섹션 29)
 */
class DriveCoachAnalysisAndScoringTest {

    // ========================================================================
    // 29.1 데이터 처리 테스트
    // ========================================================================

    @Test
    fun `kmhToMps converts speed accurately`() {
        assertEquals(0f, DrivingAnalysisEngine.kmhToMps(0f), 0.001f)
        assertEquals(10f, DrivingAnalysisEngine.kmhToMps(36f), 0.001f)
        assertEquals(20f, DrivingAnalysisEngine.kmhToMps(72f), 0.001f)
        assertEquals(27.7778f, DrivingAnalysisEngine.kmhToMps(100f), 0.01f)
    }

    @Test
    fun `preprocess handles regular and irregular time intervals and estimates acceleration`() {
        val raw = listOf(
            TelemetrySample(timestampMs = 1_000L, speedKph = 0f, longitudinalAccelerationMps2 = null),
            TelemetrySample(timestampMs = 2_000L, speedKph = 18f, longitudinalAccelerationMps2 = null),
            TelemetrySample(timestampMs = 4_000L, speedKph = 36f, longitudinalAccelerationMps2 = null), // 2s interval
            TelemetrySample(timestampMs = 9_000L, speedKph = 54f, longitudinalAccelerationMps2 = null), // 5s gap (> 3.5s threshold)
        )

        val (processed, quality) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(raw)
        assertEquals(4, processed.size)
        assertEquals(1, quality.irregularIntervalCount)
        assertNotNull(processed[1].longitudinalAccelerationMps2)
        assertNotNull(processed[2].longitudinalAccelerationMps2)
        assertTrue(processed[3].qualityFlags.contains(DataQualityFlag.IRREGULAR_INTERVAL))
    }

    @Test
    fun `preprocess safely handles duplicate timestamps without division by zero`() {
        val raw = listOf(
            TelemetrySample(timestampMs = 1_000L, speedKph = 20f),
            TelemetrySample(timestampMs = 1_000L, speedKph = 25f), // duplicate timestamp
            TelemetrySample(timestampMs = 2_000L, speedKph = 30f),
        )

        val (processed, quality) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(raw)
        assertEquals(3, processed.size)
        assertEquals(1, quality.duplicateTimestampCount)
        assertTrue(processed[1].qualityFlags.contains(DataQualityFlag.DUPLICATE_TIMESTAMP))
        val accel = processed[1].longitudinalAccelerationMps2 ?: 0f
        assertFalse(accel.isNaN() || accel.isInfinite())
    }

    @Test
    fun `preprocess handles empty and single sample lists safely`() {
        val (emptyProcessed, emptyQuality) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(emptyList())
        assertTrue(emptyProcessed.isEmpty())
        assertEquals(0, emptyQuality.totalSamples)
        assertFalse(emptyQuality.isSufficientForScoring)

        val single = listOf(TelemetrySample(timestampMs = 5_000L, speedKph = 40f))
        val (singleProcessed, singleQuality) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(single)
        assertEquals(1, singleProcessed.size)
        assertEquals(1, singleQuality.totalSamples)
        assertFalse(singleQuality.isSufficientForScoring)
    }

    @Test
    fun `preprocess flags unrealistic values while preserving original raw sample values`() {
        val raw = listOf(
            TelemetrySample(timestampMs = 1_000L, speedKph = -15f, roadWheelAngleDeg = 95f),
            TelemetrySample(timestampMs = 2_000L, speedKph = 350f, longitudinalAccelerationMps2 = 25f),
        )

        val (processed, quality) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(raw)
        assertTrue(quality.unrealisticValueCount >= 2)
        assertTrue(processed[0].qualityFlags.contains(DataQualityFlag.UNREALISTIC_VALUE))
        assertTrue(processed[1].qualityFlags.contains(DataQualityFlag.UNREALISTIC_VALUE))
        // 원본 샘플 값은 보존하되 비정상 가속도 추정치는 null로 제외됨
        assertEquals(-15f, processed[0].speedKph ?: 0f, 0.01f)
        assertNull(processed[1].longitudinalAccelerationMps2)
    }

    // ========================================================================
    // 29.2 이벤트 후보 감지 테스트
    // ========================================================================

    @Test
    fun `smooth driving does not trigger abrupt acceleration or hard braking`() {
        val samples = DemoScenarioCatalog.generateSamples("smooth_driver", baseTimestampMs = 100_000L)
        val (preprocessed, _) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(samples)
        val events = DrivingAnalysisEngine.detectEvents("test_smooth", preprocessed)

        assertTrue(events.none { it.eventType == DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE })
        assertTrue(events.none { it.eventType == DrivingEventType.LATE_HARD_BRAKING_CANDIDATE })
        assertTrue(events.none { it.eventType == DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE })
    }

    @Test
    fun `abrupt acceleration scenario detects ABRUPT_ACCELERATION_CANDIDATE`() {
        val samples = DemoScenarioCatalog.generateSamples("abrupt_acceleration", baseTimestampMs = 100_000L)
        val (preprocessed, _) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(samples)
        val events = DrivingAnalysisEngine.detectEvents("test_accel", preprocessed)

        assertTrue(events.any { it.eventType == DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE })
    }

    @Test
    fun `late braking scenario detects LATE_HARD_BRAKING_CANDIDATE`() {
        val samples = DemoScenarioCatalog.generateSamples("late_braking", baseTimestampMs = 100_000L)
        val (preprocessed, _) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(samples)
        val events = DrivingAnalysisEngine.detectEvents("test_brake", preprocessed)

        assertTrue(events.any { it.eventType == DrivingEventType.LATE_HARD_BRAKING_CANDIDATE })
    }

    @Test
    fun `low speed steering correction scenario detects LOW_SPEED_STEERING_CORRECTION_CANDIDATE`() {
        val samples = DemoScenarioCatalog.generateSamples("steering_correction", baseTimestampMs = 100_000L)
        val (preprocessed, _) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(samples)
        val events = DrivingAnalysisEngine.detectEvents("test_steer", preprocessed)

        assertTrue(events.any { it.eventType == DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE })
    }

    @Test
    fun `short single sample spike noise is ignored by duration threshold`() {
        val samples = (0..15).map { sec ->
            val accel = if (sec == 7) 3.1f else 0.1f
            TelemetrySample(
                timestampMs = sec * 1_000L,
                speedKph = 30f,
                longitudinalAccelerationMps2 = accel,
                isAccelerationEstimated = false,
                roadWheelAngleDeg = 0f,
            )
        }
        val events = DrivingAnalysisEngine.detectEvents("noise_test", samples)
        assertTrue(events.none { it.eventType == DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE })
    }

    // ========================================================================
    // 29.3 점수 계산 및 데이터 부족 처리 테스트
    // ========================================================================

    @Test
    fun `insufficient data returns null score instead of fake 85`() {
        val shortSamples = (0..4).map { sec ->
            TelemetrySample(
                timestampMs = sec * 1_000L,
                speedKph = 25f,
                longitudinalAccelerationMps2 = 0.2f,
                roadWheelAngleDeg = 0f,
            )
        }
        val (preprocessed, quality) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(shortSamples)
        val result = DrivingScoreCalculator.calculateScores(
            sessionId = "short_session",
            samples = preprocessed,
            events = emptyList(),
            qualityReport = quality,
            durationSec = 4,
        )

        assertNull("데이터 부족 시 임의의 85점을 반환하면 안 됩니다", result.overallScore)
        assertEquals("분석 데이터 부족", result.scoreGradeLabel)
    }

    @Test
    fun `missing steering sensor normalizes remaining weights and computes valid score`() {
        val noSteeringSamples = (0..25).map { sec ->
            TelemetrySample(
                timestampMs = sec * 1_000L,
                speedKph = 40f,
                longitudinalAccelerationMps2 = 0.2f,
                isAccelerationEstimated = false,
                roadWheelAngleDeg = null,
                steeringWheelAngleDeg = null,
            )
        }

        val (preprocessed, quality) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(noSteeringSamples)
        val result = DrivingScoreCalculator.calculateScores(
            sessionId = "no_steering",
            samples = preprocessed,
            events = emptyList(),
            qualityReport = quality,
            durationSec = 25,
            weights = ScoringWeights(),
        )

        assertNotNull(result.overallScore)
        assertFalse(result.steeringEval.isAvailable)
        assertNull(result.steeringEval.score)
        assertTrue((result.overallScore ?: 0) in 0..100)
    }

    @Test
    fun `score calculation is deterministic and bounded between 0 and 100`() {
        val sessionsFirstRun = DemoScenarioCatalog.buildInitialDemoSessions(nowMs = 1_700_000_000_000L)
        val sessionsSecondRun = DemoScenarioCatalog.buildInitialDemoSessions(nowMs = 1_700_000_000_000L)

        assertEquals(5, sessionsFirstRun.size)
        for (i in sessionsFirstRun.indices) {
            val s1 = sessionsFirstRun[i].analysisResult
            val s2 = sessionsSecondRun[i].analysisResult
            assertNotNull(s1)
            assertNotNull(s2)
            assertEquals(s1?.overallScore, s2?.overallScore)
            val score = s1?.overallScore ?: -1
            assertTrue("Score must be in 0..100 but was $score", score in 0..100)
        }

        val smoothScore = sessionsFirstRun.first { it.scenarioId == "smooth_driver" }.analysisResult?.overallScore ?: 0
        val lateBrakeScore = sessionsFirstRun.first { it.scenarioId == "late_braking" }.analysisResult?.overallScore ?: 0
        val mixedScore = sessionsFirstRun.first { it.scenarioId == "mixed_driving" }.analysisResult?.overallScore ?: 0

        assertTrue("Smooth driver ($smoothScore) should be higher than late braking ($lateBrakeScore)", smoothScore > lateBrakeScore)
        assertTrue("Smooth driver ($smoothScore) should be higher than mixed driving ($mixedScore)", smoothScore > mixedScore)
    }

    // ========================================================================
    // 29.4 코칭 생성 및 추세 요약 테스트
    // ========================================================================

    @Test
    fun `coaching insights populate all 7 required explainable fields`() {
        val sessions = DemoScenarioCatalog.buildInitialDemoSessions(nowMs = 1_700_000_000_000L)
        sessions.forEach { session ->
            val insights = session.analysisResult?.coachingInsights.orEmpty()
            assertFalse("Each scenario session should produce at least 1 coaching card", insights.isEmpty())
            insights.forEach { card ->
                assertTrue(card.title.isNotBlank())
                assertTrue(card.observedPattern.isNotBlank())
                assertTrue(card.evidenceData.isNotBlank())
                assertTrue(card.judgmentRule.isNotBlank())
                assertTrue(card.cautionNote.isNotBlank())
                assertTrue(card.improvementDirection.isNotBlank())
                assertFalse(card.nextDriveChecklist.isEmpty())
            }
        }

        val trends = RuleBasedCoachingGenerator.computeTrendSummaries(sessions)
        assertEquals(3, trends.size)
    }
}
