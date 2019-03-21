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
package io.spring.gradle.lock

import io.spring.gradle.lock.groovy.GroovyLockExtensions
import io.spring.gradle.lock.groovy.DependencyLockExtension
import io.spring.gradle.lock.task.PrepareForLocksTask
import io.spring.gradle.lock.task.StripLocksTask
import io.spring.gradle.lock.task.UpdateLockTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.*

class DependencyLockPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val prepareForLocks = project.tasks.create("prepareForLocks", PrepareForLocksTask::class.java)
        project.tasks.create("updateLocks", UpdateLockTask::class.java).dependsOn(prepareForLocks)
        project.tasks.create("stripLocks", StripLocksTask::class.java)
        project.extensions.create("dependencyLock", DependencyLockExtension::class.java)
        GroovyLockExtensions.enhanceDependencySyntax(project)
    }
}