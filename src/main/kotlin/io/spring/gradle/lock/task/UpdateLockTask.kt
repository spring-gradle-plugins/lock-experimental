/**
 * Copyright 2018 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spring.gradle.lock.task

import io.spring.gradle.lock.ConfigurationModuleIdentifier
import io.spring.gradle.lock.groovy.GroovyLockAstVisitor
import io.spring.gradle.lock.groovy.GroovyLockWriter
import io.spring.gradle.lock.groovy.GroovyVariableExtractionVisitor
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

open class UpdateLockTask : DefaultTask() {
	@TaskAction
	fun updateLock() = project.updateLocks()

	private fun Project.updateLocks(overrides: Map<ConfigurationModuleIdentifier, String> = emptyMap()) {
		configurations.all {
			it.resolutionStrategy.apply {
				cacheDynamicVersionsFor(0, "seconds")
				cacheChangingModulesFor(0, "seconds")
			}
		}
		arrayOf(this, rootProject).toSet().forEach { p ->
			when {
				p.buildFile.name.endsWith("gradle") -> updateLockGroovy(p, overrides)
				else -> { /* do nothing */
				}
			}
		}
	}

	private fun updateLockGroovy(p: Project, overrides: Map<ConfigurationModuleIdentifier, String>) {
		val ast = AstBuilder().buildFromString(p.buildFile.readText())
		val stmt = ast.find { it is BlockStatement }
		if (stmt is BlockStatement) {
			val variableExtractionVisitor = GroovyVariableExtractionVisitor()
			variableExtractionVisitor.visitBlockStatement(stmt)
			val visitor = GroovyLockAstVisitor(p, overrides, variableExtractionVisitor.variables)
			visitor.visitBlockStatement(stmt)
			GroovyLockWriter.updateLocks(p, visitor.updates)
		}
	}
}