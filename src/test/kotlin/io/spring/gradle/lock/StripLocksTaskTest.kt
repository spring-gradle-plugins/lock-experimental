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

class StripLocksTaskTest : TestKitTest() {
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
    fun stripLocks() {
        buildFile.appendText("""
            configurations.all {
                resolutionStrategy {
                    force 'com.google.guava:guava:16.+' lock '19.0'
                }
            }
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '17.0'
                compile group: 'commons-lang',
                    name: 'commons-lang',
                    version: '2.+' lock '2.0'
                compile('com.google.inject:guice:latest.release') {
                    changing = true
                } lock '4.0'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("stripLocks")

        assertTrue(buildFile.readText().contains("""
            configurations.all {
                resolutionStrategy {
                    force 'com.google.guava:guava:16.+'
                }
            }
            dependencies {
                compile 'com.google.guava:guava:18.+'
                compile group: 'commons-lang',
                    name: 'commons-lang',
                    version: '2.+'
                compile('com.google.inject:guice:latest.release') {
                    changing = true
                }
            }
        """.trim('\n').trimIndent()))
    }
}
