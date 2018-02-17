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

package com.netflix.nebula.lock

import com.netflix.nebula.lock.groovy.GroovyLockExtensions
import com.netflix.nebula.lock.groovy.NebulaLockExtension
import com.netflix.nebula.lock.task.PrepareForLocksTask
import com.netflix.nebula.lock.task.StripLocksTask
import com.netflix.nebula.lock.task.UpdateLockTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.*

class NebulaLockPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val locksInEffect = ArrayList<Locked>()
        val lockService = LockService(project, locksInEffect)

        val prepareForLocks = project.tasks.create("prepareForLocks", PrepareForLocksTask::class.java) { it.lockService = lockService }
        project.tasks.create("updateLocks", UpdateLockTask::class.java) { it.lockService = lockService }.dependsOn(prepareForLocks)
        project.tasks.create("stripLocks", StripLocksTask::class.java) { it.lockService = lockService }
        project.extensions.create("nebulaDependencyLock", NebulaLockExtension::class.java)
        GroovyLockExtensions.enhanceDependencySyntax(project, locksInEffect)
    }
}