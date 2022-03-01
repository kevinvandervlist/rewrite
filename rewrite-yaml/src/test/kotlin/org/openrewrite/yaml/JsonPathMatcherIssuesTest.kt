/*
 *  Copyright 2021 the original author or authors.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  https://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openrewrite.yaml

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.Issue
import org.openrewrite.yaml.tree.Yaml

class JsonPathMatcherIssuesTest {
    @Language("yaml")
    private val somethingYaml = """
        something:
          - steps: 
            - task: ABC
            - task: ZZZ
    """.trimIndent()

    private val subjectsYaml = """
        subjects:
          - kind: User
            name: some-user
          - kind: ServiceAccount
            name: monitoring-tools
    """.trimIndent()

    private val tocYaml = """
        toc:
          - section:
            - subsection: ABB
            - subsection: ABC
          - section:
            - subsection: AAA
            - subsection: ZZZ 
    """.trimIndent()

    private val testRecursiveDescent = "$..[?(@.kind == 'ServiceAccount')].kind"
    private val nestedSequences = "$.something[*].steps[?(@.task == 'ABC')].task"
    private val sequenceKeyByExactMatchWildcard = "$.*[?(@.kind == 'ServiceAccount')].kind"
    private val nestedCollectionValues = "$.toc[*].section[?(@.subsection == 'ABC')].subsection"

    @Test
    fun `must find element with recursive descent operator`() {
        val results = visit(testRecursiveDescent, subjectsYaml)
        assertThat(results).hasSize(1)
    }

    @Test
    fun `must find nested sequences with predicate`() {
        val results = visit(nestedSequences, somethingYaml)
        assertThat(results).hasSize(1)
    }

    @Test
    fun `must match a sequence via a wildcard`() {
        val results = visit(sequenceKeyByExactMatchWildcard, subjectsYaml)
        assertThat(results).hasSize(1)
    }

    @Test
    fun `must match nested collection values`() {
        val results = visit(nestedCollectionValues, tocYaml)
        assertThat(results).hasSize(1)
    }

    private fun visit(jsonPath: String, json: String, encloses: Boolean = false): List<Yaml> {
        val ctx = InMemoryExecutionContext({ it.printStackTrace() })
        val documents = YamlParser().parse(ctx, json)
        if (documents.isEmpty()) {
            return emptyList()
        }
        val matcher = JsonPathMatcher(jsonPath)

        val results = ArrayList<Yaml>()
        documents.forEach {
            object : YamlVisitor<MutableList<Yaml>>() {
                override fun visitMappingEntry(entry: Yaml.Mapping.Entry, p: MutableList<Yaml>): Yaml? {
                    val e = super.visitMappingEntry(entry, p)
                    if (if (encloses) matcher.encloses(cursor) else matcher.matches(cursor)) {
                        p.add(e)
                    }
                    return e
                }
            }.visit(it, results)
        }
        return results
    }
}
