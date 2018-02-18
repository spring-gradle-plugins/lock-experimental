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

import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.gradle.api.Project

class GroovyLockWriter {
    private val lockRegex = " lock '.*'\\s*$".toRegex()

    fun updateLocks(project: Project, updates: Collection<GroovyLockUpdate>) {
        val updated = StringBuilder()

        val lines = project.buildFile.readLines()
        var i = 0
        while(i < lines.size) {
            if(i > 0) updated.append('\n')

            updates.find { it.method.lastLineNumber-1 == i }?.apply {
                val arguments = method.arguments
                when(arguments) {
                    is ArgumentListExpression -> {
                        val line = lines[i++]
                        val endOfLastArgument = arguments.last().lastColumnNumber-1
                        val sb = StringBuilder(line.substring(0, endOfLastArgument))
                        val restOfLine = if(endOfLastArgument <= line.length) { line.substring(endOfLastArgument) } else ""
                        val trimmedRestOfLine = restOfLine.replace("^\\s*lock '.*'".toRegex(), "")
                        if(lock != null) {
                            sb.append(" lock '$lock'")
                        }
                        sb.append(trimmedRestOfLine)
                        updated.append(sb.toString())
                    }
                    is TupleExpression -> {
                        val trimmedLine = lines[i++].removeLock()
                        updated.append(trimmedLine)
                        if(lock != null) {
                            updated.append(" lock '$lock'")
                        }
                    }
                }

            } ?: updated.append(lines[i++])
        }

        project.buildFile.writeText(updated.toString())
    }

    fun stripLocks(project: Project) {
        val updated = StringBuilder()
        project.buildFile.readLines().forEach {
            updated.appendln(it.removeLock())
        }
        project.buildFile.writeText(updated.toString())
    }

    private fun String.removeLock() = replace(lockRegex, "")
}
