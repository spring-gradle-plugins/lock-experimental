/**
 * Copyright 2018 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spring.gradle.lock.groovy

import org.gradle.api.Project

object GroovyLockPreparationWriter {
    fun prepareDependencies(project: Project, updates: Map<IntRange, List<ConfigurationDependency>>) {
        val updated = StringBuilder()
        val lines = project.buildFile.readLines()
        var i = 0

        while (i < lines.size) {
            if (i > 0) updated.append('\n')
            val matching = updates.filterKeys { it.contains(i) }
            check(matching.size <= 1)
            if (matching.isNotEmpty()) {
                matching.values.flatten().forEach { dep ->
                    val indentation = "".padStart(dep.indent)
                    updated.append(indentation).append("${dep.conf} ${lines[i].substring(dep.replacement.startingColumn, dep.replacement.endingColumn)}")
                }
                i += matching.keys.first().count()
            } else updated.append(lines[i++])
        }

        project.buildFile.writeText(updated.toString())
    }

}
