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

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.SourceUnit
import org.gradle.api.Project
import java.util.*


data class Declaration(val startingColumn: Int, val endingColumn: Int, val startingLine: Int, val endingLine: Int)
data class ConfigurationDependency(val indent: Int, val conf: String, val replacement: Declaration)

class GroovyPrepareForLocksAstVisitor(val project: Project) : ClassCodeVisitorSupport() {
    val updates = mutableMapOf<IntRange, MutableList<ConfigurationDependency>>()
    private var inDependencies = false

    override fun getSourceUnit(): SourceUnit? = null
    val path = Stack<String>()

    override fun visitMethodCallExpression(call: MethodCallExpression) {
        val caller = call.objectExpression.text
        if (caller != "this") path.push(caller)
        path.push(call.methodAsString)

        if (inDependencies) {
            visitMethodCallInDependencies(call)
        } else if (path.any { it == "dependencies" }) {
            inDependencies = true
            super.visitMethodCallExpression(call)
            inDependencies = false
        }

        path.pop()
    }

    fun visitMethodCallInDependencies(call: MethodCallExpression) {
        // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
        val conf = call.methodAsString
        val args = call.parseArgs()
        if (args.size > 1 && args.all { it is ConstantExpression || it is GStringExpression }) {
            if (isConf(conf)) {
                args.map {
                    updates
                            .getOrPut((it.lineNumber - 1..it.lastLineNumber - 1), { mutableListOf<ConfigurationDependency>() })
                            .add(ConfigurationDependency(call.columnNumber - 1, conf, Declaration(it.columnNumber - 1, it.lastColumnNumber - 1, it.lineNumber - 1, it.lastLineNumber - 1)))
                }
            }
        }
    }

    private fun isConf(methodName: String) =
            project.configurations.findByName(methodName) != null ||
                    project.subprojects.any { sub -> sub.configurations.findByName(methodName) != null }

    private fun MethodCallExpression.parseArgs(): List<Expression> {
        return when (arguments) {
            is ArgumentListExpression -> (arguments as ArgumentListExpression).expressions
            is TupleExpression -> (arguments as TupleExpression).expressions
            else -> emptyList()
        }
    }
}