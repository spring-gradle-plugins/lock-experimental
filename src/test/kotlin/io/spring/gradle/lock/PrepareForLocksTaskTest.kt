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

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrepareForLocksTaskTest : TestKitTest() {
    @Before
    override fun before() {
        super.before()

        buildFile.writeText("""
            plugins {
                id 'java'
                id 'spring.lock'
            }

            repositories {
                mavenCentral()
            }

        """.trim('\n').trimIndent())
    }

    @Test
    fun commaSeparatedStrings() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+',
                        'commons-lang:commons-lang:2.+'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("prepareForLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile 'com.google.guava:guava:18.+'
                compile 'commons-lang:commons-lang:2.+'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun commaSeparatedStringsWithVariable() {
        buildFile.appendText("""
            def guavaVersion = '18.+'
            dependencies {
                compile "com.google.guava:guava:${'$'}guavaVersion",
                        'commons-lang:commons-lang:2.+'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("prepareForLocks")

        assertTrue(buildFile.readText().contains("""
            def guavaVersion = '18.+'
            dependencies {
                compile "com.google.guava:guava:${'$'}guavaVersion"
                compile 'commons-lang:commons-lang:2.+'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun leaveSingleLineDependenciesAlone() {
        buildFile.appendText("""
            dependencies {
                compile 'commons-lang:commons-lang:2.+'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("prepareForLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile 'commons-lang:commons-lang:2.+'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun leaveDependenciesWithClosuresAlone() {
        buildFile.appendText("""
            dependencies {
                compile('org.springframework.boot:spring-boot-starter-web') {
                    exclude group: 'org.springframework.boot', module: 'spring-boot-starter-validation'
                }
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("prepareForLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile('org.springframework.boot:spring-boot-starter-web') {
                    exclude group: 'org.springframework.boot', module: 'spring-boot-starter-validation'
                }
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun leaveMapDependenciesAlone() {
        buildFile.appendText("""
            dependencies {
                compile group: 'commons-lang',
                    name: 'commons-lang',
                    version: '2.+'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("prepareForLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile group: 'commons-lang',
                    name: 'commons-lang',
                    version: '2.+'
            }
        """.trim('\n').trimIndent()))
    }
}
