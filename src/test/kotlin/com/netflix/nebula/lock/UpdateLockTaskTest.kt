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

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateLockTaskTest : TestKitTest() {
    @Before
    override fun before() {
        super.before()

        buildFile.writeText("""
            plugins {
                id 'java'
                id 'nebula.lock-experimental'
            }

            repositories {
                mavenCentral()
            }

        """.trim('\n').trimIndent())
    }

    @Test
    fun updateLocks() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+'
                compile group: 'commons-lang',
                    name: 'commons-lang',
                    version: '2.+'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '18.0'
                compile group: 'commons-lang',
                    name: 'commons-lang',
                    version: '2.+' lock '2.6'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun updateAnExistingLock() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '16.0'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '18.0'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun lockStatementsOnDependenciesWithStaticVersionsAreRemoved() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.0' lock '18.0'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun multipleDependenciesAddedToConfigurationCommaSeparated() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+',
                        'commons-lang:commons-lang:2.+'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '18.0'
                compile 'commons-lang:commons-lang:2.+' lock '2.6'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun lockRootProjectDependencies() {
        addSubproject("sub", "plugins { id 'nebula.lock-experimental' }")

        buildFile.writeText("""
            subprojects {
                apply plugin: 'java'
                repositories { mavenCentral() }
                dependencies {
                    compile 'com.google.guava:guava:18.+'
                }
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("sub:updateLocks")

        assertTrue(buildFile.readText().contains("""
                dependencies {
                    compile 'com.google.guava:guava:18.+' lock '18.0'
                }
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun lockDynamicForces() {
        buildFile.appendText("""
            configurations.all {
                resolutionStrategy {
                    force 'com.google.guava:guava:16.+'
                }
            }

            configurations.compile {
                resolutionStrategy {
                    force 'com.google.guava:guava:17.+'
                }
            }

            configurations {
                testCompile {
                    resolutionStrategy {
                        force 'com.google.guava:guava:14.+'
                    }
                }
            }

            dependencies {
                compile 'com.google.guava:guava:latest.release'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        assertTrue(buildFile.readText().contains("""
            configurations.all {
                resolutionStrategy {
                    force 'com.google.guava:guava:16.+' lock '17.0'
                }
            }

            configurations.compile {
                resolutionStrategy {
                    force 'com.google.guava:guava:17.+' lock '17.0'
                }
            }

            configurations {
                testCompile {
                    resolutionStrategy {
                        force 'com.google.guava:guava:14.+' lock '14.0.1'
                    }
                }
            }
        """.trim('\n').trimIndent()))
    }
}
