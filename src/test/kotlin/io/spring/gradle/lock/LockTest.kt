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

import org.gradle.testkit.runner.BuildResult
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LockTest: TestKitTest() {

    @Before
    override fun before() {
        super.before()
        buildFile.writeText("""
            plugins {
                id 'java'
                id 'spring.lock'
				id 'nebula.optional-base' version '3.3.0'
            }

            repositories {
                mavenCentral()
            }

            task listDependencies << {
                [configurations.compile, configurations.testCompile].each { conf ->
                    conf.resolvedConfiguration.firstLevelModuleDependencies.each {
                        println "${'$'}conf.name: ${'$'}it.module.id"
                    }
                }
            }
        """)
    }

    @Test
    fun lockingInOneConfigurationDoesNotAffectAnother() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:latest.release' lock '16.0'
                testCompile 'com.google.guava:guava:19.0'
            }
        """)

        val result = runTasksSuccessfully("listDependencies")
        result.assertDependency("com.google.guava:guava:16.0", "compile")
        result.assertDependency("com.google.guava:guava:19.0", "testCompile")
    }

    @Test
    fun ignoredDependenciesAreStillResolved() {
        buildFile.appendText("""
            dependencies {
                dependencyLock.ignore {
                    compile 'com.google.guava:guava:19.0'
                }
            }
        """)

        val result = runTasksSuccessfully("listDependencies")
        result.assertDependency("com.google.guava:guava:19.0", "compile")
    }

    @Test
    fun lockingWithFloatingPointNumbersIsOk() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:latest.release' lock 16.0
            }
        """)

        val result = runTasksSuccessfully("listDependencies")
        result.assertDependency("com.google.guava:guava:16.0", "compile")
    }

	@Test
	fun lockingOptionalDependency() {
		buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:latest.release', optional lock '16.0'
            }
        """)

		val result = runTasksSuccessfully("listDependencies")
		result.assertDependency("com.google.guava:guava:16.0", "compile")
	}

	@Test
    fun lockingStringNotationWithClosure() {
        buildFile.appendText("""
            dependencies {
                compile('com.google.guava:guava:latest.release') { changing = true } lock '16.0'
            }
        """)

        val result = runTasksSuccessfully("listDependencies")
        result.assertDependency("com.google.guava:guava:16.0", "compile")
    }

    @Test
    fun lockingMapNotation() {
        buildFile.appendText("""
            dependencies {
                compile group: 'com.google.guava', name: 'guava', version: 'latest.release' lock '16.0'
            }
        """)

        val result = runTasksSuccessfully("listDependencies")
        result.assertDependency("com.google.guava:guava:16.0", "compile")
    }

    @Test
    fun lockingOnCommaSeparatedListOfDependenciesFails() {
        buildFile.appendText("""
            dependencies {
                compile(
                    'com.google.guava:guava:latest.release',
                    'commons-lang:commons-lang:latest.release'
                ) lock '16.0'
            }
        """)

        runTasksAndFail("listDependencies")
    }

    @Test
    fun locksCanBeIgnoredWithProperty() {
        buildFile.appendText("""
            dependencies {
                compile 'com.google.guava:guava:18.+' lock '16.0'
            }
        """)

        val result = runTasksSuccessfully("listDependencies", "-PdependencyLock.ignore")
        result.assertDependency("com.google.guava:guava:18.0", "compile")
    }

	@Test
	fun lockingDependencyInCustomConfiguration() {
		buildFile.appendText("""
			configurations {
				myconfig
			}

            dependencies {
                myconfig 'com.google.guava:guava:latest.release' lock '16.0'
            }

			task listOptionalDependencies << {
                [configurations.myconfig].each { conf ->
                    conf.resolvedConfiguration.firstLevelModuleDependencies.each {
                        println "${'$'}conf.name: ${'$'}it.module.id"
                    }
                }
            }
        """)

		val result = runTasksSuccessfully("listOptionalDependencies")
		result.assertDependency("com.google.guava:guava:16.0", "myconfig")
	}

	@Test
	fun lockPreservesExcludes() {
		buildFile.appendText("""
            dependencies {
                compile('org.latencyutils:LatencyUtils:latest.release') {
					exclude group: 'org.hdrhistogram', module: 'HdrHistogram'
				}   lock '2.0.3'
            }

			task listExcludes << {
				configurations.compile.dependencies.collect { dep ->
					if(dep instanceof ExternalModuleDependency) {
						ExternalModuleDependency extDep = dep
						extDep.excludeRules.each { println(it.group + ":" + it.module) }
					}
				}
            }
        """)

		val result = runTasksSuccessfully("listExcludes")
		assertTrue(result.output.contains("org.hdrhistogram:HdrHistogram"))
	}

    private fun BuildResult.assertDependency(mvid: String, conf: String) {
		println(output)
        assertTrue(output.contains("$conf: $mvid"))
    }
}