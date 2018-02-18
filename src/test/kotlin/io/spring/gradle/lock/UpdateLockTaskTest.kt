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
import org.junit.Ignore
import org.junit.Test

class UpdateLockTaskTest : TestKitTest() {
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

	@Ignore("FIXME succeeds in IDE, fails on command line")
    @Test
    fun updateAnExistingLock() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '16.0'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("uL")

		println(buildFile.readText())

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
    fun dependenciesWithClosure() {
        buildFile.appendText("""
            dependencies {
                compile('com.google.guava:guava:18.+') {
                    changing = true
                }
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")
        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile('com.google.guava:guava:18.+') {
                    changing = true
                } lock '18.0'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun dependenciesWithVariable() {
        buildFile.appendText("""
            def version = '18.+'
            dependencies {
                compile "com.google.guava:guava:${'$'}version"
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        val readText = buildFile.readText()
        assertTrue(readText.contains("""
            def version = '18.+'
            dependencies {
                compile "com.google.guava:guava:${'$'}version" lock '18.0'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun dependenciesWithVariableFromExt() {
        buildFile.appendText("""
            ext {
                versions = [:]
                versions.guava = '18.+'
            }
            dependencies {
                compile "com.google.guava:guava:${'$'}{versions.guava}"
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        val readText = buildFile.readText()
        assertTrue(readText.contains("""
            dependencies {
                compile "com.google.guava:guava:${'$'}{versions.guava}" lock '18.0'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun ignoreDependenciesWithVariableThatSetsCompleteVersion() {
        buildFile.appendText("""
            ext {
                versions = [:]
                versions.guice = '4.0'
            }
            def completeGuavaVersion = '18.0'
            dependencies {
                compile "com.google.guava:guava:${'$'}completeGuavaVersion"
                compile "com.google.inject:guice:${'$'}{versions.guice}"
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        val readText = buildFile.readText()
        assertTrue(readText.contains("""
            dependencies {
                compile "com.google.guava:guava:${'$'}completeGuavaVersion"
                compile "com.google.inject:guice:${'$'}{versions.guice}"
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun lockRootProjectDependencies() {
        addSubproject("sub", "plugins { id 'spring.lock' }")

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

    @Test
    fun lockFromBom() {
        buildFile.writeText("""
            plugins {
                id 'io.spring.dependency-management' version '1.0.0.RC1'
                id 'java'
                id 'spring.lock'
            }

            repositories {
                mavenCentral()
            }

            dependencyManagement {
                imports {
                    mavenBom "org.springframework.cloud:spring-cloud-netflix:1.1.2.RELEASE"
                }
            }

            dependencies {
                compile 'org.springframework.cloud:spring-cloud-starter-feign'
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        assertTrue(buildFile.readText().contains("""
            dependencies {
                compile 'org.springframework.cloud:spring-cloud-starter-feign' lock '1.1.2.RELEASE'
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun ignoredBlocksAreNotLocked() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+'
                dependencyLock.ignore {
                    compile 'com.google.dagger:dagger:2.+'
                    compile module("com.jcraft:jsch.agentproxy:0.0.9") {
                        ['jsch', 'sshagent', 'usocket-jna', 'usocket-nc'].each {
                            dependency "com.jcraft:jsch.agentproxy.${'$'}{it}:0.0.9"
                        }
                    }
                }
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        val readText = buildFile.readText()
        assertTrue(readText.contains("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '18.0'
                dependencyLock.ignore {
                    compile 'com.google.dagger:dagger:2.+'
                    compile module("com.jcraft:jsch.agentproxy:0.0.9") {
                        ['jsch', 'sshagent', 'usocket-jna', 'usocket-nc'].each {
                            dependency "com.jcraft:jsch.agentproxy.${'$'}{it}:0.0.9"
                        }
                    }
                }
            }
        """.trim('\n').trimIndent()))
    }

    @Test
    fun trailingCommentsAreRetained() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+' // comments should stay 1
                compile "commons-beanutils:commons-beanutils:1.8.+" // comments should stay 2
            }
        """.trim('\n').trimIndent())

        runTasksSuccessfully("updateLocks")

        val readText = buildFile.readText()
        assertTrue(readText.contains("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '18.0' // comments should stay 1
                compile "commons-beanutils:commons-beanutils:1.8.+" lock '1.8.3' // comments should stay 2
            }
        """.trim('\n').trimIndent()))
    }
}
