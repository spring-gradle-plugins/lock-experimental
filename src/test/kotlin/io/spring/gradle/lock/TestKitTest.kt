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
package io.spring.gradle.lock

import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Things that really belong in the unfinished Gradle Testkit
 */
open class TestKitTest {
    @JvmField @Rule
    val temp = TemporaryFolder()

    lateinit var projectDir: File
    lateinit var buildFile: File
    lateinit var settingsFile: File

    @Before
    open fun before() {
        projectDir = temp.root
        buildFile = File(projectDir, "build.gradle")
        settingsFile = File(projectDir, "settings.gradle")
    }

    fun runner(tasks: Array<out String>) =
        GradleRunner.create()
                .withDebug(true)
                .withProjectDir(projectDir)
                .withArguments(tasks.toList().plus("--stacktrace"))
                .withPluginClasspath()

    fun runTasksSuccessfully(vararg tasks: String) = runner(tasks).build()
    fun runTasksAndFail(vararg tasks: String) = runner(tasks).buildAndFail()

    fun addSubproject(name: String): File {
        val subprojectDir = File(projectDir, name)
        subprojectDir.mkdirs()
        settingsFile.writeText("include '$name'\n")
        return subprojectDir
    }

    fun addSubproject(name: String, buildGradleContents: String): File {
        val subprojectDir = addSubproject(name)
        File(subprojectDir, "build.gradle").writeText(buildGradleContents)
        return subprojectDir
    }
}
