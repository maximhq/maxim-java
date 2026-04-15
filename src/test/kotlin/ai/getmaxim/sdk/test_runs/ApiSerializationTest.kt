package ai.getmaxim.sdk.test_runs

import ai.getmaxim.sdk.models.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests that verify the JSON serialization format matches what the backend API expects.
 */
class ApiSerializationTest {

    // ─── createTestRun Request Body ─────────────────────────────────────

    @Nested
    inner class CreateTestRunRequestTest {
        @Test
        fun `evaluator config serializes to correct format`() {
            val evaluator = Evaluator(
                id = "eval-1",
                name = "Bias",
                type = EvaluatorType.AI,
                builtin = true,
                reversed = false,
                config = buildJsonObject {
                    put("passFailCriteria", buildJsonObject {
                        put("entryLevel", buildJsonObject {
                            put("value", 0.5)
                            put("operator", ">=")
                            put("name", "score")
                        })
                    })
                }
            )
            val json = evaluator.toJsonObject()

            // Verify it matches the expected backend format
            assertEquals("eval-1", json["id"]!!.jsonPrimitive.content)
            assertEquals("AI", json["type"]!!.jsonPrimitive.content) // Backend expects string "AI" not enum
            assertEquals(true, json["builtin"]!!.jsonPrimitive.boolean)
        }

        @Test
        fun `human evaluation config serializes correctly`() {
            val config = HumanEvaluationConfig(
                emails = listOf("rev1@test.com", "rev2@test.com"),
                instructions = "Rate output quality",
                requester = "tester@test.com"
            )
            val json = config.toJsonObject()

            assertEquals(2, json["emails"]!!.jsonArray.size)
            assertEquals("rev1@test.com", json["emails"]!!.jsonArray[0].jsonPrimitive.content)
            assertEquals("Rate output quality", json["instructions"]!!.jsonPrimitive.content)
            assertEquals("tester@test.com", json["requester"]!!.jsonPrimitive.content)
        }
    }

    // ─── pushTestRunEntry Request Body ──────────────────────────────────

    @Nested
    inner class PushTestRunEntryRequestTest {
        @Test
        fun `TestRunEntry serializes dataEntry in correct format`() {
            val entry = TestRunEntry(
                variables = mapOf(
                    "question" to TestRunVariable("text", "What is ML?"),
                    "context" to TestRunVariable("text", "Tech domain")
                ),
                input = "What is ML?",
                expectedOutput = "Machine Learning",
                output = "ML stands for Machine Learning"
            )
            val json = entry.toJsonObject()

            val dataEntry = json["dataEntry"]!!.jsonObject
            assertEquals("text", dataEntry["question"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("What is ML?", dataEntry["question"]!!.jsonObject["payload"]!!.jsonPrimitive.content)
            assertEquals("text", dataEntry["context"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        }

        @Test
        fun `TestRunEntry with sdkVariables matches backend format`() {
            val entry = TestRunEntry(
                output = "result",
                sdkVariables = mapOf(
                    "evaluator-id-1" to mapOf(
                        "custom_input" to "mapped input",
                        "custom_context" to "mapped context"
                    )
                )
            )
            val json = entry.toJsonObject()

            // Backend expects: meta.sdkVariables.{evaluatorId}.{type: "json", payload: JSON_STRING}
            val meta = json["meta"]!!.jsonObject
            val sdkVars = meta["sdkVariables"]!!.jsonObject
            val evalVars = sdkVars["evaluator-id-1"]!!.jsonObject
            assertEquals("json", evalVars["type"]!!.jsonPrimitive.content)

            // Payload should be a JSON string
            val payloadStr = evalVars["payload"]!!.jsonPrimitive.content
            val parsed = Json.decodeFromString<Map<String, String>>(payloadStr)
            assertEquals("mapped input", parsed["custom_input"])
            assertEquals("mapped context", parsed["custom_context"])
        }

        @Test
        fun `TestRunWithDatasetEntry includes dataset info`() {
            val testRun = TestRun(
                id = "tr-1",
                workspaceId = "ws-1",
                evalConfig = buildJsonObject {
                    put("evals", buildJsonArray {
                        add(buildJsonObject {
                            put("id", "eval-1")
                            put("name", "Bias")
                            put("type", "AI")
                            put("builtin", true)
                        })
                    })
                }
            )
            val withDataset = TestRunWithDatasetEntry(testRun, "de-123", "ds-456")
            val json = withDataset.toJsonObject()

            assertEquals("tr-1", json["id"]!!.jsonPrimitive.content)
            assertEquals("ws-1", json["workspaceId"]!!.jsonPrimitive.content)
            assertEquals("de-123", json["datasetEntryId"]!!.jsonPrimitive.content)
            assertEquals("ds-456", json["datasetId"]!!.jsonPrimitive.content)
            assertNotNull(json["evalConfig"])
        }

        @Test
        fun `local evaluation results serialize correctly`() {
            val results = listOf(
                LocalEvaluationResultWithId(
                    id = "leval-1",
                    result = LocalEvaluatorReturn(score = true, reasoning = "Passed"),
                    name = "binary_check",
                    passFailCriteria = PassFailCriteria(
                        onEachEntry = PassFailCriteriaOnEachEntry("=", true),
                        forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 80, "percentageOfPassedResults")
                    ),
                    output = "output text"
                )
            )
            val entry = TestRunEntry(localEvaluationResults = results, output = "output text")
            val json = entry.toJsonObject()

            val evalResults = json["localEvaluationResults"]!!.jsonArray
            assertEquals(1, evalResults.size)

            val firstResult = evalResults[0].jsonObject
            assertEquals("leval-1", firstResult["id"]!!.jsonPrimitive.content)
            assertEquals("binary_check", firstResult["name"]!!.jsonPrimitive.content)
            assertEquals(true, firstResult["result"]!!.jsonObject["score"]!!.jsonPrimitive.boolean)
            assertEquals("Passed", firstResult["result"]!!.jsonObject["reasoning"]!!.jsonPrimitive.content)
        }
    }

    // ─── TestRunStatus Response Parsing ──────────────────────────────────

    @Nested
    inner class TestRunStatusResponseTest {
        @Test
        fun `parses typical status response`() {
            val json = buildJsonObject {
                put("total", 50)
                put("running", 10)
                put("queued", 15)
                put("failed", 2)
                put("completed", 20)
                put("stopped", 3)
                put("testRunStatus", "RUNNING")
            }
            val status = TestRunStatus.fromJsonObject(json)

            assertEquals(50, status.totalEntries)
            assertEquals(10, status.runningEntries)
            assertEquals(15, status.queuedEntries)
            assertEquals(2, status.failedEntries)
            assertEquals(20, status.completedEntries)
            assertEquals(3, status.stoppedEntries)
            assertEquals(RunStatus.RUNNING, status.testRunStatus)
        }

        @Test
        fun `parses COMPLETE status`() {
            val json = buildJsonObject {
                put("total", 10)
                put("running", 0)
                put("queued", 0)
                put("failed", 1)
                put("completed", 9)
                put("stopped", 0)
                put("testRunStatus", "COMPLETE")
            }
            val status = TestRunStatus.fromJsonObject(json)
            assertEquals(RunStatus.COMPLETE, status.testRunStatus)
            assertEquals(10, status.totalEntries)
            assertEquals(9, status.completedEntries)
            assertEquals(1, status.failedEntries)
        }
    }

    // ─── TestRunResult Response Parsing ──────────────────────────────────

    @Nested
    inner class TestRunResultResponseTest {
        @Test
        fun `parses full result with all metrics`() {
            val json = buildJsonObject {
                put("link", "/workspace/ws-1/testrun/tr-1")
                put("result", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "My Test")
                        put("individualEvaluatorMeanScore", buildJsonObject {
                            put("Bias", buildJsonObject {
                                put("score", 0.92)
                                put("outOf", 1.0)
                                put("pass", true)
                            })
                            put("Clarity", buildJsonObject {
                                put("score", 4.5)
                                put("outOf", 5.0)
                                put("pass", true)
                            })
                        })
                        put("usage", buildJsonObject {
                            put("total", 5000)
                            put("input", 2000)
                            put("completion", 3000)
                        })
                        put("cost", buildJsonObject {
                            put("total", 0.15)
                            put("input", 0.06)
                            put("completion", 0.09)
                        })
                    })
                })
            }
            val result = TestRunResult.fromJsonObject(json)

            assertEquals("/workspace/ws-1/testrun/tr-1", result.link)
            assertEquals(1, result.result.size)

            val obj = result.result[0]
            assertEquals("My Test", obj.name)
            assertEquals(2, obj.individualEvaluatorMeanScore.size)

            val bias = obj.individualEvaluatorMeanScore["Bias"]!!
            assertEquals(0.92, bias.score)
            assertEquals(true, bias.isPass)

            val clarity = obj.individualEvaluatorMeanScore["Clarity"]!!
            assertEquals(4.5, clarity.score)
            assertEquals(5.0, clarity.outOf)
        }

        @Test
        fun `link can be mutated for base URL prepend`() {
            val json = buildJsonObject {
                put("link", "/workspace/ws-1/testrun/tr-1")
                put("result", buildJsonArray {})
            }
            val result = TestRunResult.fromJsonObject(json)
            result.link = "https://app.getmaxim.ai" + result.link
            assertEquals("https://app.getmaxim.ai/workspace/ws-1/testrun/tr-1", result.link)
        }
    }

    // ─── Preset Response Parsing ────────────────────────────────────────

    @Nested
    inner class PresetResponseTest {
        @Test
        fun `parses preset with split dataset`() {
            val json = buildJsonObject {
                put("id", "preset-1")
                put("name", "Default Config")
                put("datasets", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "ds-1")
                        put("name", "Main Dataset")
                        put("splitId", "split-1")
                        put("splitName", "Test Split")
                    })
                })
                put("evaluators", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "eval-1")
                        put("name", "Bias")
                    })
                    add(buildJsonObject {
                        put("id", "eval-2")
                        put("name", "Clarity")
                    })
                })
                put("contextToEvaluate", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "DATASET_COLUMN")
                        put("payload", "context")
                    })
                })
            }
            val preset = Preset.fromJsonObject(json)

            assertEquals("preset-1", preset.id)
            assertEquals("Default Config", preset.name)
            assertEquals(1, preset.datasets!!.size)
            assertEquals("split-1", preset.datasets!![0].splitId)
            assertEquals(2, preset.evaluators!!.size)
            assertEquals("Bias", preset.evaluators!![0].name)
            assertEquals(1, preset.contextToEvaluate!!.size)
            assertEquals("DATASET_COLUMN", preset.contextToEvaluate!![0].type)
            assertEquals("context", preset.contextToEvaluate!![0].payload)
        }
    }

    // ─── Execute Response Parsing ───────────────────────────────────────

    @Nested
    inner class ExecuteResponseTest {
        @Test
        fun `workflow response parses all fields`() {
            val json = buildJsonObject {
                put("output", "Workflow result text")
                put("contextToEvaluate", "relevant context")
                put("latency", 245.5)
            }
            val resp = ExecuteWorkflowResponse.fromJsonObject(json)

            assertEquals("Workflow result text", resp.output)
            assertEquals("relevant context", resp.contextToEvaluate)
            assertEquals(245.5, resp.latency)
        }

        @Test
        fun `prompt response parses usage and cost`() {
            val json = buildJsonObject {
                put("output", "Prompt output")
                put("usage", buildJsonObject {
                    put("promptTokens", 100)
                    put("completionTokens", 150)
                    put("totalTokens", 250)
                    put("latency", 0.8)
                })
                put("cost", buildJsonObject {
                    put("input", 0.001)
                    put("output", 0.002)
                    put("total", 0.003)
                })
            }
            val resp = ExecutePromptResponse.fromJsonObject(json)

            assertEquals("Prompt output", resp.output)
            assertNotNull(resp.usage)
            assertEquals(100, resp.usage!!.promptTokens)
            assertEquals(150, resp.usage!!.completionTokens)
            assertEquals(0.8, resp.usage!!.latency)
            assertNotNull(resp.cost)
            assertEquals(0.003, resp.cost!!.totalCost)
        }

        @Test
        fun `prompt response handles null usage and cost`() {
            val json = buildJsonObject {
                put("output", "text")
            }
            val resp = ExecutePromptResponse.fromJsonObject(json)
            assertNull(resp.usage)
            assertNull(resp.cost)
        }
    }

    // ─── Local Evaluator Config Format ──────────────────────────────────

    @Nested
    inner class LocalEvaluatorConfigFormatTest {
        @Test
        fun `generated evaluator config matches backend expected format`() {
            val config = getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(
                id = "generated-uuid",
                name = "my_relevance_scorer",
                passFailCriteria = PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry(">=", 0.7),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 85, "average")
                )
            )

            val json = config.toJsonObject()

            // Verify the exact structure the backend expects
            assertEquals("generated-uuid", json["id"]!!.jsonPrimitive.content)
            assertEquals("my_relevance_scorer", json["name"]!!.jsonPrimitive.content)
            assertEquals("Local", json["type"]!!.jsonPrimitive.content)
            assertEquals(false, json["builtin"]!!.jsonPrimitive.boolean)

            val passFailCriteria = json["config"]!!.jsonObject["passFailCriteria"]!!.jsonObject

            val entryLevel = passFailCriteria["entryLevel"]!!.jsonObject
            assertEquals(0.7, entryLevel["value"]!!.jsonPrimitive.double)
            assertEquals(">=", entryLevel["operator"]!!.jsonPrimitive.content)
            assertEquals("score", entryLevel["name"]!!.jsonPrimitive.content)

            val runLevel = passFailCriteria["runLevel"]!!.jsonObject
            assertEquals(85, runLevel["value"]!!.jsonPrimitive.int)
            assertEquals(">=", runLevel["operator"]!!.jsonPrimitive.content)
            assertEquals("meanScore", runLevel["name"]!!.jsonPrimitive.content)
        }

        @Test
        fun `percentageOfPassedResults maps to queriesPassed`() {
            val config = getEvaluatorConfigFromEvaluatorNameAndPassFailCriteria(
                id = "id-1",
                name = "binary_check",
                passFailCriteria = PassFailCriteria(
                    onEachEntry = PassFailCriteriaOnEachEntry("=", true),
                    forTestrunOverall = PassFailCriteriaForTestrunOverall(">=", 90, "percentageOfPassedResults")
                )
            )

            val runLevel = config.config!!["passFailCriteria"]!!.jsonObject["runLevel"]!!.jsonObject
            assertEquals("queriesPassed", runLevel["name"]!!.jsonPrimitive.content)
        }
    }
}
