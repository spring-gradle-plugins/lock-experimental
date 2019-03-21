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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

// From: https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
// "In both notations, all properties, except name, are optional."
data class GradleDependency(val group: String?,
                            val name: String,
                            val version: String?) {
    val id = DefaultModuleVersionIdentifier(group, name, version)
}

data class ConfigurationModuleIdentifier(val conf: Configuration, val mid: ModuleIdentifier)

fun ModuleIdentifier.withConf(conf: Configuration) = ConfigurationModuleIdentifier(conf, this)