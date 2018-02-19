/*
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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GroovyLockExtensions {
	static Logger logger = LoggerFactory.getLogger(GroovyLockExtensions)

	/**
	 * Because somehow project is sticky inside the lock closure, even if we remove the metaClass on Dependency.
	 * This is only really a problem during test execution, but the solution doesn't harm normal operation.
	 */
	static Project cachedProject

	static void enhanceDependencySyntax(Project project) {
		cachedProject = project

		def updatingLocks = project.gradle.startParameter.taskNames.any { task ->
			if(task == 'updateLocks')
				true
			else {
				def taskNameParts = task.split('(?<!^)(?=[A-Z])')
				taskNameParts.size() == 2 && 'update'.startsWith(taskNameParts[0]) &&
						'Locks'.startsWith(taskNameParts[1])
 			}
		}

		Dependency.metaClass.lock = { lockedVersion ->
			if(updatingLocks)
				return this

			if (delegate instanceof ExternalModuleDependency && !cachedProject.hasProperty('dependencyLock.ignore')) {
				ExternalModuleDependency dep = delegate

				// This metaClass definition of lock is going to contain a reference to SOME
				// project in the set of all projects (whichever one is configured last). So we'll
				// have to search through all projects' configurations looking for this dependency.
				def configurations = cachedProject.rootProject.allprojects*.configurations.flatten()

				def containingConf = configurations.find {
					it.dependencies.any { it.is(dep) }
				}

				if(!containingConf) {
					logger.warn("Unable to lock ${dep.group}:${dep.name}:${dep.version} because no configuration could be found containing it")
					return this
				}

				containingConf.dependencies.remove(dep)

				def locked = new DefaultExternalModuleDependency(dep.group, dep.name,
						lockedVersion?.toString(), dep.targetConfiguration)
				locked.setChanging(dep.changing)
				locked.setForce(dep.force)

				dep.excludeRules.each { ex ->
					def excludeMap = [:]
					if(ex.group)
						excludeMap.group = ex.group
					if(ex.module)
						excludeMap.module = ex.module
					locked.exclude(excludeMap)
				}

				containingConf.dependencies.add(locked)
			}

			return this
		}

		ResolutionStrategy.metaClass.lock = { lockedVersion ->
			if (delegate instanceof ExternalModuleDependency && !cachedProject.hasProperty('dependencyLock.ignore')) {
				ExternalModuleDependency dep = delegate

				def containingConf = cachedProject.configurations.find {
					it.dependencies.any { it.is(dep) }
				}
				containingConf.dependencies.remove(dep)

				def locked = new DefaultExternalModuleDependency(dep.group, dep.name, lockedVersion?.toString(),
						dep.targetConfiguration)
				locked.setChanging(dep.changing)
				locked.setForce(dep.force)

				containingConf.dependencies.add(locked)
			}

			return this
		}
	}
}
