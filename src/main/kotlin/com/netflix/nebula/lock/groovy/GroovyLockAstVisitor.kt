/*
 * Copyright 2016-2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.nebula.lock.groovy

import com.netflix.nebula.lock.ConfigurationModuleIdentifier
import com.netflix.nebula.lock.withConf
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.SourceUnit
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import java.util.*

class GroovyLockAstVisitor(val project: Project,
                           val overrides: Map<ConfigurationModuleIdentifier, String>): ClassCodeVisitorSupport() {
    val updates = ArrayList<GroovyLockUpdate>()
    private var inDependencies = false
    private var inResolutionStrategies = false

    override fun getSourceUnit(): SourceUnit? = null
    val path = Stack<String>()

    override fun visitMethodCallExpression(call: MethodCallExpression) {
        val caller = call.objectExpression.text
        if(caller != "this") path.push(caller)
        path.push(call.methodAsString)

        if(inDependencies) {
            visitMethodCallInDependencies(call)
        } else if (path.any { it == "dependencies" }) {
            inDependencies = true
            super.visitMethodCallExpression(call)
            inDependencies = false
        }

        if (inResolutionStrategies) {
            visitMethodCallInResolutionStrategies(call)
        } else if (path.any { it == "resolutionStrategy" }) {
            inResolutionStrategies = true
            super.visitMethodCallExpression(call)
            inResolutionStrategies = false
        } else super.visitMethodCallExpression(call)

        path.pop()
    }

    fun visitMethodCallInResolutionStrategies(call: MethodCallExpression) {
        // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
        val conf = path.takeLastWhile { it != "configurations" }.first()

        val args = when(call.arguments) {
            is ArgumentListExpression -> (call.arguments as ArgumentListExpression).expressions
            is TupleExpression -> (call.arguments as TupleExpression).expressions
            else -> emptyList()
        }
        if(isConf(conf) || conf == "all") {
            val locks = args.map { arg ->
                when (arg) {
                    is MapExpression -> {
                        val entries = collectEntryExpressions(args)
                        conf.lockedVersion(entries["group"], entries["name"]!!).let { locked ->
                            if (locked == entries["version"]) null else locked
                        }
                    }
                    is ConstantExpression -> {
                        "([^:]*):([^:]+):([^@:]*).*".toRegex().matchEntire(arg.value as String)?.run {
                            val group = groupValues[1].let { if (it.isEmpty()) null else it }
                            val name = groupValues[2]
                            val version = groupValues[3].let { if (it.isEmpty()) null else it }
                            conf.lockedVersion(group, name).let { locked -> if (locked == version) null else locked }
                        }
                    }
                    else -> null
                }
            }

            if (locks.isNotEmpty())
                updates.add(GroovyLockUpdate(call, locks))
        }
    }

    fun visitMethodCallInDependencies(call: MethodCallExpression) {
        // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
        val conf = call.methodAsString
        val args = when(call.arguments) {
            is ArgumentListExpression -> (call.arguments as ArgumentListExpression).expressions
            is TupleExpression -> (call.arguments as TupleExpression).expressions
            else -> emptyList()
        }

        if(isConf(conf)) {
            val locks = args.map { arg ->
                when (arg) {
                    is MapExpression -> {
                        val entries = collectEntryExpressions(args)
                        conf.lockedVersion(entries["group"], entries["name"]!!).let { locked ->
                            if (locked == entries["version"]) null else locked
                        }
                    }
                    is ConstantExpression -> {
                        "([^:]*):([^:]+):([^@:]*).*".toRegex().matchEntire(arg.value as String)?.run {
                            val group = groupValues[1].let { if (it.isEmpty()) null else it }
                            val name = groupValues[2]
                            val version = groupValues[3].let { if (it.isEmpty()) null else it }
                            conf.lockedVersion(group, name).let { locked -> if (locked == version) null else locked }
                        }
                    }
                    else -> null
                }
            }

            if (locks.isNotEmpty())
                updates.add(GroovyLockUpdate(call, locks))
        }
    }

    private fun isConf(methodName: String) =
            project.configurations.findByName(methodName) != null ||
                    project.subprojects.any { sub -> sub.configurations.findByName(methodName) != null }

    private fun collectEntryExpressions(args: List<Expression>) =
            args.filterIsInstance(MapExpression::class.java)
                    .map { it.mapEntryExpressions }
                    .flatten()
                    .map { it.keyExpression.text to it.valueExpression.text }
                    .toMap()

    private fun String.lockedVersion(group: String?, name: String): String? {
        val mid = DefaultModuleIdentifier(group, name)

        fun overrideOrResolvedVersion(p: Project): String? {
            val configurations = if(this == "all") p.configurations else setOf(p.configurations.getByName(this))
            val versions = configurations.map { conf ->
                overrides[mid.withConf(conf)] ?:
                        conf.resolvedConfiguration.firstLevelModuleDependencies.find {
                            it.module.id.module.equals(mid)
                        }?.moduleVersion
            }.filterNotNull()
            return versions.maxWith(DefaultVersionComparator().asStringComparator())
        }

        return if(project.rootProject == project) {
            if(project.subprojects.isEmpty()) {
                // single module project
                overrideOrResolvedVersion(project)
            }
            else {
                // root project of a multi-module project
                project.subprojects.map(::overrideOrResolvedVersion).maxWith(DefaultVersionComparator().asStringComparator())
            }
        } else {
            // subproject of a multi-module project
            overrideOrResolvedVersion(project)
        }
    }

    private fun Stack<String>.findConfiguration(): String {
        val idx = this.indexOf("configuration")
        return this[idx+1]
    }
}