package ai.getmaxim.sdk.models

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestRunModelsTest {

    // ─── RunStatus ──────────────────────────────────────────────────────

    @Nested
    inner class RunStatusTest {
        @Test
        fun `fromValue returns correct enum for all values`() {
            assertEquals(RunStatus.QUEUED, RunStatus.fromValue("QUEUED"))
            assertEquals(RunStatus.RUNNING, RunStatus.fromValue("RUNNING"))
            assertEquals(RunStatus.FAILED, RunStatus.fromValue("FAILED"))
            assertEquals(RunStatus.COMPLETE, RunStatus.fromValue("COMPLETE"))
            assertEquals(RunStatus.STOPPED, RunStatus.fromValue("STOPPED"))
        }

        @Test
        fun `fromValue throws on unknown status`() {
            assertThrows<IllegalArgumentException> {
                RunStatus.fromValue("INVALID")
            }
        }
    }

    // ─── TestRun ────────────────────────────────────────────────────────

    @Nested
    inner class TestRunTest {
        @Test
        fun `toJsonObject includes all fields`() {
            val config = HumanEvaluationConfig(
                emails = listOf("test@example.com"),
                instructions = "Review carefully",
                requester = "tester"
            )
            val testRun = TestRun(
                id = "tr-123",
                workspaceId = "ws-456",
                evalConfig = buildJsonObject { put("evals", buildJsonArray {}) },
                humanEvaluationConfig = config,
                parentTestRunId = "parent-789",
                environmentName = "prod",
                connectedRepoId = "repo-abc"
            )
            val json = testRun.toJsonObject()

            assertEquals("tr-123", json["id"]!!.jsonPrimitive.content)
            assertEquals("ws-456", json["workspaceId"]!!.jsonPrimitive.content)
            assertNotNull(json["evalConfig"])
            assertNotNull(json["humanEvaluationConfig"])
            assertEquals("parent-789", json["parentTestRunId"]!!.jsonPrimitive.content)
            assertEquals("prod", json["environmentName"]!!.jsonPrimitive.content)
            assertEquals("repo-abc", json["connectedRepoId"]!!.jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject omits null fields`() {
            val testRun = TestRun(
                id = "tr-123",
                workspaceId = "ws-456",
                evalConfig = buildJsonObject {}
            )
            val json = testRun.toJsonObject()

            assertNull(json["humanEvaluationConfig"])
            assertNull(json["parentTestRunId"])
            assertNull(json["environmentName"])
            assertNull(json["connectedRepoId"])
        }

        @Test
        fun `fromJsonObject parses complete JSON`() {
            val json = buildJsonObject {
                put("id", "tr-123")
                put("workspaceId", "ws-456")
                put("evalConfig", buildJsonObject { put("evals", buildJsonArray {}) })
                put("humanEvaluationConfig", buildJsonObject {
                    put("emails", buildJsonArray { add("a@b.com") })
                    put("instructions", "test")
                })
                put("parentTestRunId", "parent-1")
                put("environmentName", "staging")
                put("connectedRepoId", "repo-1")
            }
            val testRun = TestRun.fromJsonObject(json)

            assertEquals("tr-123", testRun.id)
            assertEquals("ws-456", testRun.workspaceId)
            assertNotNull(testRun.humanEvaluationConfig)
            assertEquals(listOf("a@b.com"), testRun.humanEvaluationConfig!!.emails)
            assertEquals("parent-1", testRun.parentTestRunId)
            assertEquals("staging", testRun.environmentName)
        }

        @Test
        fun `fromJsonObject handles missing optional fields`() {
            val json = buildJsonObject {
                put("id", "tr-123")
                put("workspaceId", "ws-456")
                put("evalConfig", buildJsonObject {})
            }
            val testRun = TestRun.fromJsonObject(json)

            assertNull(testRun.humanEvaluationConfig)
            assertNull(testRun.parentTestRunId)
            assertNull(testRun.environmentName)
        }
    }

    // ─── TestRunEntry ───────────────────────────────────────────────────

    @Nested
    inner class TestRunEntryTest {
        @Test
        fun `toJsonObject with all fields`() {
            val entry = TestRunEntry(
                id = "entry-1",
                variables = mapOf(
                    "question" to TestRunVariable("text", "What is AI?")
                ),
                output = "AI is...",
                input = "What is AI?",
                expectedOutput = "Artificial Intelligence",
                contextToEvaluate = "some context",
                scenario = "test scenario",
                expectedSteps = "step1,step2"
            )
            val json = entry.toJsonObject()

            assertEquals("entry-1", json["id"]!!.jsonPrimitive.content)
            assertEquals("AI is...", json["output"]!!.jsonPrimitive.content)
            assertEquals("What is AI?", json["input"]!!.jsonPrimitive.content)
            assertEquals("Artificial Intelligence", json["expectedOutput"]!!.jsonPrimitive.content)
            assertEquals("some context", json["contextToEvaluate"]!!.jsonPrimitive.content)
            assertEquals("test scenario", json["scenario"]!!.jsonPrimitive.content)
            assertEquals("step1,step2", json["expectedSteps"]!!.jsonPrimitive.content)

            // Check dataEntry
            val dataEntry = json["dataEntry"]!!.jsonObject
            assertEquals("text", dataEntry["question"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("What is AI?", dataEntry["question"]!!.jsonObject["payload"]!!.jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject with contextToEvaluate as list`() {
            val entry = TestRunEntry(
                contextToEvaluate = listOf("ctx1", "ctx2", "ctx3")
            )
            val json = entry.toJsonObject()
            val ctx = json["contextToEvaluate"]!!.jsonArray
            assertEquals(3, ctx.size)
            assertEquals("ctx1", ctx[0].jsonPrimitive.content)
            assertEquals("ctx2", ctx[1].jsonPrimitive.content)
            assertEquals("ctx3", ctx[2].jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject with sdkVariables nests in meta`() {
            val entry = TestRunEntry(
                output = "test",
                sdkVariables = mapOf(
                    "eval-123" to mapOf("input" to "hello", "output" to "world")
                )
            )
            val json = entry.toJsonObject()

            val meta = json["meta"]!!.jsonObject
            val sdkVars = meta["sdkVariables"]!!.jsonObject
            val evalVars = sdkVars["eval-123"]!!.jsonObject
            assertEquals("json", evalVars["type"]!!.jsonPrimitive.content)
            // payload should be a JSON string containing the mapping
            val payload = evalVars["payload"]!!.jsonPrimitive.content
            assertTrue(payload.contains("hello"))
            assertTrue(payload.contains("world"))
        }

        @Test
        fun `toJsonObject with connectedTraceId nests in meta`() {
            val entry = TestRunEntry(
                output = "test",
                connectedTraceId = "trace-abc"
            )
            val json = entry.toJsonObject()

            val meta = json["meta"]!!.jsonObject
            assertEquals("trace-abc", meta["connectedTraceId"]!!.jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject with both sdkVariables and connectedTraceId in same meta`() {
            val entry = TestRunEntry(
                output = "test",
                sdkVariables = mapOf("eval-1" to mapOf("k" to "v")),
                connectedTraceId = "trace-123"
            )
            val json = entry.toJsonObject()

            val meta = json["meta"]!!.jsonObject
            assertNotNull(meta["sdkVariables"])
            assertEquals("trace-123", meta["connectedTraceId"]!!.jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject omits null fields`() {
            val entry = TestRunEntry()
            val json = entry.toJsonObject()

            assertNull(json["id"])
            assertNull(json["output"])
            assertNull(json["input"])
            assertNull(json["expectedOutput"])
            assertNull(json["contextToEvaluate"])
            assertNull(json["scenario"])
            assertNull(json["expectedSteps"])
            assertNull(json["meta"])
            assertNull(json["localEvaluationResults"])
        }

        @Test
        fun `toJsonObject with localEvaluationResults`() {
            val entry = TestRunEntry(
                localEvaluationResults = listOf(
                    LocalEvaluationResultWithId(
                        id = "eval-1",
                        result = LocalEvaluatorReturn(score = 0.85, reasoning = "Good quality"),
                        name = "quality",
                        passFailCriteria = PassFailCriteria(
                            onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.5),
                            forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 70, "average")
                        ),
                        output = "test output"
                    )
                )
            )
            val json = entry.toJsonObject()
            val results = json["localEvaluationResults"]!!.jsonArray
            assertEquals(1, results.size)
            assertEquals("eval-1", results[0].jsonObject["id"]!!.jsonPrimitive.content)
            assertEquals("quality", results[0].jsonObject["name"]!!.jsonPrimitive.content)
        }
    }

    // ─── TestRunWithDatasetEntry ────────────────────────────────────────

    @Nested
    inner class TestRunWithDatasetEntryTest {
        @Test
        fun `toJsonObject includes testRun fields plus dataset info`() {
            val testRun = TestRun(
                id = "tr-1",
                workspaceId = "ws-1",
                evalConfig = buildJsonObject {}
            )
            val withDataset = TestRunWithDatasetEntry(
                testRun = testRun,
                datasetEntryId = "de-1",
                datasetId = "ds-1"
            )
            val json = withDataset.toJsonObject()

            assertEquals("tr-1", json["id"]!!.jsonPrimitive.content)
            assertEquals("ws-1", json["workspaceId"]!!.jsonPrimitive.content)
            assertEquals("de-1", json["datasetEntryId"]!!.jsonPrimitive.content)
            assertEquals("ds-1", json["datasetId"]!!.jsonPrimitive.content)
        }
    }

    // ─── TestRunVariable ────────────────────────────────────────────────

    @Nested
    inner class TestRunVariableTest {
        @Test
        fun `text variable serializes correctly`() {
            val variable = TestRunVariable("text", "hello world")
            val json = variable.toJsonObject()
            assertEquals("text", json["type"]!!.jsonPrimitive.content)
            assertEquals("hello world", json["payload"]!!.jsonPrimitive.content)
        }

        @Test
        fun `file variable serializes correctly`() {
            val variable = TestRunVariable(
                "file",
                mapOf("files" to listOf(mapOf("url" to "https://example.com/file.pdf", "type" to "url")))
            )
            val json = variable.toJsonObject()
            assertEquals("file", json["type"]!!.jsonPrimitive.content)
            val payload = json["payload"]!!.jsonObject
            val files = payload["files"]!!.jsonArray
            assertEquals(1, files.size)
            assertEquals("https://example.com/file.pdf", files[0].jsonObject["url"]!!.jsonPrimitive.content)
        }
    }

    // ─── HumanEvaluationConfig ──────────────────────────────────────────

    @Nested
    inner class HumanEvaluationConfigTest {
        @Test
        fun `toJsonObject with all fields`() {
            val config = HumanEvaluationConfig(
                emails = listOf("a@b.com", "c@d.com"),
                instructions = "Be thorough",
                requester = "admin"
            )
            val json = config.toJsonObject()
            assertEquals(2, json["emails"]!!.jsonArray.size)
            assertEquals("Be thorough", json["instructions"]!!.jsonPrimitive.content)
            assertEquals("admin", json["requester"]!!.jsonPrimitive.content)
        }

        @Test
        fun `toJsonObject omits null optional fields`() {
            val config = HumanEvaluationConfig(emails = listOf("x@y.com"))
            val json = config.toJsonObject()
            assertEquals(1, json["emails"]!!.jsonArray.size)
            assertNull(json["instructions"])
            assertNull(json["requester"])
        }

        @Test
        fun `fromJsonObject roundtrip`() {
            val original = HumanEvaluationConfig(
                emails = listOf("test@test.com"),
                instructions = "Check quality",
                requester = "user1"
            )
            val parsed = HumanEvaluationConfig.fromJsonObject(original.toJsonObject())
            assertEquals(original.emails, parsed.emails)
            assertEquals(original.instructions, parsed.instructions)
            assertEquals(original.requester, parsed.requester)
        }
    }

    // ─── TestRunStatus ──────────────────────────────────────────────────

    @Nested
    inner class TestRunStatusTest {
        @Test
        fun `fromJsonObject parses all fields correctly`() {
            val json = buildJsonObject {
                put("total", 100)
                put("running", 20)
                put("queued", 30)
                put("failed", 5)
                put("completed", 40)
                put("stopped", 5)
                put("testRunStatus", "RUNNING")
            }
            val status = TestRunStatus.fromJsonObject(json)

            assertEquals(100, status.totalEntries)
            assertEquals(20, status.runningEntries)
            assertEquals(30, status.queuedEntries)
            assertEquals(5, status.failedEntries)
            assertEquals(40, status.completedEntries)
            assertEquals(5, status.stoppedEntries)
            assertEquals(RunStatus.RUNNING, status.testRunStatus)
        }

        @Test
        fun `toDisplayMap returns all entries`() {
            val status = TestRunStatus(10, 2, 3, 1, 4, 0, RunStatus.RUNNING)
            val map = status.toDisplayMap()
            assertEquals(10, map["totalEntries"])
            assertEquals(RunStatus.RUNNING.value, map["testRunStatus"])
        }
    }

    // ─── TestRunResult ──────────────────────────────────────────────────

    @Nested
    inner class TestRunResultTest {
        @Test
        fun `fromJsonObject parses complete result`() {
            val json = buildJsonObject {
                put("link", "/workspace/ws-1/testrun/tr-1")
                put("result", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "Test Run 1")
                        put("individualEvaluatorMeanScore", buildJsonObject {
                            put("Bias", buildJsonObject {
                                put("score", 0.95)
                                put("outOf", 1.0)
                                put("pass", true)
                            })
                        })
                        put("usage", buildJsonObject {
                            put("total", 1000)
                            put("input", 500)
                            put("completion", 500)
                        })
                        put("cost", buildJsonObject {
                            put("total", 0.05)
                            put("input", 0.02)
                            put("completion", 0.03)
                        })
                        put("latency", buildJsonObject {
                            put("min", 100.0)
                            put("max", 500.0)
                            put("p50", 200.0)
                            put("p90", 400.0)
                            put("p95", 450.0)
                            put("p99", 490.0)
                            put("mean", 250.0)
                            put("standardDeviation", 50.0)
                            put("total", 5000.0)
                        })
                    })
                })
            }
            val result = TestRunResult.fromJsonObject(json)

            assertEquals("/workspace/ws-1/testrun/tr-1", result.link)
            assertEquals(1, result.result.size)
            assertEquals("Test Run 1", result.result[0].name)

            val biasScore = result.result[0].individualEvaluatorMeanScore["Bias"]!!
            assertEquals(0.95, biasScore.score)
            assertEquals(1.0, biasScore.outOf)
            assertEquals(true, biasScore.isPass)

            assertNotNull(result.result[0].usage)
            assertEquals(1000, result.result[0].usage!!.total)
            assertNotNull(result.result[0].cost)
            assertEquals(0.05, result.result[0].cost!!.total)
            assertNotNull(result.result[0].latency)
            assertEquals(200.0, result.result[0].latency!!.p50)
        }

        @Test
        fun `fromJsonObject handles missing optional fields in result`() {
            val json = buildJsonObject {
                put("link", "/link")
                put("result", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "Test")
                        put("individualEvaluatorMeanScore", buildJsonObject {})
                    })
                })
            }
            val result = TestRunResult.fromJsonObject(json)
            assertNull(result.result[0].usage)
            assertNull(result.result[0].cost)
            assertNull(result.result[0].latency)
        }
    }

    // ─── YieldedOutputTokenUsage ────────────────────────────────────────

    @Nested
    inner class YieldedOutputTokenUsageTest {
        @Test
        fun `fromJsonObject handles camelCase keys`() {
            val json = buildJsonObject {
                put("promptTokens", 100)
                put("completionTokens", 200)
                put("totalTokens", 300)
                put("latency", 1.5)
            }
            val usage = YieldedOutputTokenUsage.fromJsonObject(json)
            assertEquals(100, usage.promptTokens)
            assertEquals(200, usage.completionTokens)
            assertEquals(300, usage.totalTokens)
            assertEquals(1.5, usage.latency)
        }

        @Test
        fun `fromJsonObject handles snake_case keys`() {
            val json = buildJsonObject {
                put("prompt_tokens", 50)
                put("completion_tokens", 60)
                put("total_tokens", 110)
            }
            val usage = YieldedOutputTokenUsage.fromJsonObject(json)
            assertEquals(50, usage.promptTokens)
            assertEquals(60, usage.completionTokens)
            assertEquals(110, usage.totalTokens)
            assertNull(usage.latency)
        }

        @Test
        fun `fromJsonObject defaults to 0 for missing fields`() {
            val json = buildJsonObject {}
            val usage = YieldedOutputTokenUsage.fromJsonObject(json)
            assertEquals(0, usage.promptTokens)
            assertEquals(0, usage.completionTokens)
            assertEquals(0, usage.totalTokens)
        }

        @Test
        fun `toJsonObject uses snake_case keys`() {
            val usage = YieldedOutputTokenUsage(10, 20, 30, 1.0)
            val json = usage.toJsonObject()
            assertEquals(10, json["prompt_tokens"]!!.jsonPrimitive.int)
            assertEquals(20, json["completion_tokens"]!!.jsonPrimitive.int)
            assertEquals(30, json["total_tokens"]!!.jsonPrimitive.int)
            assertEquals(1.0, json["latency"]!!.jsonPrimitive.double)
        }
    }

    // ─── YieldedOutputCost ──────────────────────────────────────────────

    @Nested
    inner class YieldedOutputCostTest {
        @Test
        fun `roundtrip serialization`() {
            val original = YieldedOutputCost(0.01, 0.02, 0.03)
            val json = original.toJsonObject()
            val parsed = YieldedOutputCost.fromJsonObject(json)
            assertEquals(original.inputCost, parsed.inputCost)
            assertEquals(original.outputCost, parsed.outputCost)
            assertEquals(original.totalCost, parsed.totalCost)
        }
    }

    // ─── Preset ─────────────────────────────────────────────────────────

    @Nested
    inner class PresetTest {
        @Test
        fun `fromJsonObject parses complete preset`() {
            val json = buildJsonObject {
                put("id", "preset-1")
                put("name", "My Preset")
                put("description", "A test preset")
                put("datasets", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "ds-1")
                        put("name", "Dataset 1")
                        put("splitId", "split-1")
                        put("splitName", "Split 1")
                    })
                })
                put("evaluators", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "eval-1")
                        put("name", "Bias")
                    })
                })
                put("contextToEvaluate", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "DATASET_COLUMN")
                        put("payload", "context_col")
                    })
                })
            }
            val preset = Preset.fromJsonObject(json)

            assertEquals("preset-1", preset.id)
            assertEquals("My Preset", preset.name)
            assertEquals("A test preset", preset.description)
            assertEquals(1, preset.datasets!!.size)
            assertEquals("ds-1", preset.datasets!![0].id)
            assertEquals("split-1", preset.datasets!![0].splitId)
            assertEquals(1, preset.evaluators!!.size)
            assertEquals("Bias", preset.evaluators!![0].name)
            assertEquals(1, preset.contextToEvaluate!!.size)
            assertEquals("DATASET_COLUMN", preset.contextToEvaluate!![0].type)
            assertEquals("context_col", preset.contextToEvaluate!![0].payload)
        }

        @Test
        fun `fromJsonObject handles missing optional fields`() {
            val json = buildJsonObject {
                put("id", "p-1")
                put("name", "Minimal")
            }
            val preset = Preset.fromJsonObject(json)
            assertNull(preset.description)
            assertNull(preset.datasets)
            assertNull(preset.evaluators)
            assertNull(preset.contextToEvaluate)
        }
    }

    // ─── ExecuteWorkflowResponse ────────────────────────────────────────

    @Nested
    inner class ExecuteWorkflowResponseTest {
        @Test
        fun `fromJsonObject parses all fields`() {
            val json = buildJsonObject {
                put("output", "workflow result")
                put("contextToEvaluate", "ctx")
                put("latency", 123.4)
            }
            val resp = ExecuteWorkflowResponse.fromJsonObject(json)
            assertEquals("workflow result", resp.output)
            assertEquals("ctx", resp.contextToEvaluate)
            assertEquals(123.4, resp.latency)
        }

        @Test
        fun `fromJsonObject defaults latency to 0`() {
            val json = buildJsonObject {}
            val resp = ExecuteWorkflowResponse.fromJsonObject(json)
            assertNull(resp.output)
            assertEquals(0.0, resp.latency)
        }
    }

    // ─── DatasetRow ─────────────────────────────────────────────────────

    @Nested
    inner class DatasetRowTest {
        @Test
        fun `fromJsonObject parses correctly`() {
            val json = buildJsonObject {
                put("id", "row-1")
                put("data", buildJsonObject {
                    put("input", "Hello")
                    put("expected_output", "Hi there")
                })
            }
            val row = DatasetRow.fromJsonObject(json)
            assertEquals("row-1", row.id)
            assertEquals("Hello", row.data["input"])
            assertEquals("Hi there", row.data["expected_output"])
        }
    }

    // ─── EvaluatorMeanScore ─────────────────────────────────────────────

    @Nested
    inner class EvaluatorMeanScoreTest {
        @Test
        fun `fromJsonObject handles numeric score`() {
            val json = buildJsonObject {
                put("score", 0.85)
                put("outOf", 1.0)
                put("pass", true)
            }
            val score = EvaluatorMeanScore.fromJsonObject(json)
            assertEquals(0.85, score.score)
            assertEquals(1.0, score.outOf)
            assertEquals(true, score.isPass)
        }

        @Test
        fun `fromJsonObject handles boolean score`() {
            val json = buildJsonObject {
                put("score", true)
            }
            val score = EvaluatorMeanScore.fromJsonObject(json)
            assertEquals(true, score.score)
            assertNull(score.outOf)
            assertNull(score.isPass)
        }

        @Test
        fun `fromJsonObject handles string score`() {
            val json = buildJsonObject {
                put("score", "N/A")
            }
            val score = EvaluatorMeanScore.fromJsonObject(json)
            assertEquals("N/A", score.score)
        }
    }

    // ─── ConsoleLogger ──────────────────────────────────────────────────

    @Nested
    inner class ConsoleLoggerTest {
        @Test
        fun `info does not throw`() {
            val logger = ConsoleLogger()
            logger.info("test message")
        }

        @Test
        fun `error with exception does not throw`() {
            val logger = ConsoleLogger()
            logger.error("error message", RuntimeException("boom"))
        }

        @Test
        fun `error without exception does not throw`() {
            val logger = ConsoleLogger()
            logger.error("error message")
        }
    }
}
