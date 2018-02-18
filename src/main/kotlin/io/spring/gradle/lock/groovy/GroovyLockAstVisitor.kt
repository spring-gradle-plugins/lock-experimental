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

import io.spring.gradle.lock.ConfigurationModuleIdentifier
import io.spring.gradle.lock.withConf
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.control.SourceUnit
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import java.util.*

class GroovyLockAstVisitor(val project: Project,
						   val overrides: Map<ConfigurationModuleIdentifier, String>,
						   val variableDefinitions: Map<String, String>) : ClassCodeVisitorSupport() {
	val updates = ArrayList<GroovyLockUpdate>()
	private val mvidPattern = "([^:]*):([^:]+):?([^@:]*).*".toRegex()
	private var inDependencies = false
	private var inResolutionStrategies = false

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
		if (path.any { it == "ignore" }) return
		val conf = path.takeLastWhile { it != "configurations" }.first()
		val args = call.parseArgs()
		if (isConf(conf) || conf == "all") {
			val lock = args.first().lock(conf, args)
			updates.add(GroovyLockUpdate(call, lock))
		}
	}

	fun visitMethodCallInDependencies(call: MethodCallExpression) {
		if (path.any { it == "ignore" }) return
		// https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
		val conf = call.methodAsString
		val args = call.parseArgs()
		if (isConf(conf)) {
			val lock = args.first().lock(conf, args)
			updates.add(GroovyLockUpdate(call, lock))
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
		val mid = DefaultModuleIdentifier.newId(group, name)
		val versionComparator = DefaultVersionComparator().asVersionComparator()
		val versionParser = VersionParser()

		fun overrideOrResolvedVersion(p: Project): Version? {
			val configurations = if (this == "all") p.configurations else setOf(p.configurations.getByName(this))
			val versions = configurations
					.filter { it.isCanBeResolved }
					.mapNotNull { conf ->
						overrides[mid.withConf(conf)] ?: conf
								.resolvedConfiguration
								.firstLevelModuleDependencies
								.find { it.module.id.module == mid }
								?.moduleVersion
					}

			return versions
					.map { v -> versionParser.transform(v) }
					.maxWith(versionComparator)
		}

		val lockedVersion: Version? = if (project.rootProject == project) {
			if (project.subprojects.isEmpty()) {
				// single module project
				overrideOrResolvedVersion(project)
			} else {
				// root project of a multi-module project
				project.subprojects
						.map(::overrideOrResolvedVersion)
						.maxWith(versionComparator)
			}
		} else {
			// subproject of a multi-module project
			overrideOrResolvedVersion(project)
		}

		return lockedVersion?.source
	}

	private fun Expression.lock(conf: String, args: List<Expression>): String? {
		return when (this) {
			is MapExpression -> {
				val entries = collectEntryExpressions(args)
				conf.lockedVersion(entries["group"], entries["name"]!!).let { locked ->
					if (locked == entries["version"]) null else locked
				}
			}
			is ConstantExpression -> {
				mvidPattern.matchEntire(value as String)?.run {
					val group = groupValues[1].let { if (it.isEmpty()) null else it }
					val name = groupValues[2]
					val version = groupValues[3].let { if (it.isEmpty()) null else it }
					conf.lockedVersion(group, name).let { locked -> if (locked == version) null else locked }
				}
			}
			is GStringExpression -> {
				mvidPattern.matchEntire(text)?.run {
					val group = groupValues[1].let { if (it.isEmpty()) null else it }
					val name = groupValues[2]
					val version = groupValues[3].let {
						when {
							it.isEmpty() -> null
							it.startsWith("$") -> variableDefinitions[it.drop(1)]
							else -> it
						}
					}
					conf.lockedVersion(group, name).let { locked -> if (locked == version) null else locked }
				}
			}
			else -> null
		}
	}

	private fun MethodCallExpression.parseArgs(): List<Expression> {
		return when (arguments) {
			is ArgumentListExpression -> (arguments as ArgumentListExpression).expressions
			is TupleExpression -> (arguments as TupleExpression).expressions
			else -> emptyList()
		}
	}
}