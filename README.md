# Gradle Dependency Lock (Experimental)

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
- [Purpose](#purpose)
- [Usage](#usage)
- [Why the change?](#why-the-change)
- [The `lock` extension method](#the-lock-extension-method)
- [The `updateLocks` task](#the-updatelocks-task)
  - [Multi-module projects](#multi-module-projects)
  - [Comma-separated dependencies](#comma-separated-dependencies)
- [Ignoring locks](#ignoring-locks)
- [Migration from the legacy plugin](#migration-from-the-legacy-plugin)
- [What about locking transitives?](#what-about-locking-transitives)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Purpose

This project is an experimental major overhaul of the [gradle-dependency-lock-plugin](https://github.com/nebula-plugins/gradle-dependency-lock-plugin).

## Usage

Clone and build with:

    ./gradlew publishToMavenLocal

To apply this plugin:

    buildscript {
        repositories { mavenLocal() }
        dependencies {
            classpath 'io.spring.gradle:dependency-lock:latest.release'
        }

        configurations.classpath.resolutionStrategy.cacheDynamicVersionsFor 0, 'minutes'
    }

    apply plugin: 'spring.lock'

## Why the change?

We believe this is the real root use case for dependency locks: **"I want to write static versions into my
dependency blocks, but I don't want to have to manually update the static versions as new versions come out."**

1. At various times, [gradle-dependency-lock-plugin](https://github.com/nebula-plugins/gradle-dependency-lock-plugin) served functions that are now
best served by other means, e.g. version alignment which is now satisfied by [gradle-resolution-rules](https://github.com/nebula-plugins/gradle-resolution-rules-plugin).

2. **dependencies.lock** is hard to read and separate from where users define their dependencies in **build.gradle**. Unaware
that nebula.dependency-lock is in effect, engineers are often confused about why they don't get a certain version when they update
 **build.gradle**.

3. Manually updating a particular dependency while leaving the rest locked requires *a priori* knowledge of
 the existence of the `./gradlew updateLock -PdependencyLock.updateDependencies=com.example:foo,com.example:bar` mechanism. Well-meaning
 engineers may attempt to update **dependencies.lock** manually, not realizing that the dependency they are attempting to update
 is reflected once for each configuration it appears in, and their manual effort fails to achieve their goal.

4. nebula.dependency-lock applies its locks using `resolutionStrategy.force`, which can lead to ordering problems with other
plugins that affect `resolutionStrategy`.

## The `lock` extension method

    compile 'com.google.guava:guava:latest.release' lock '19.0'

The experimental plugin hangs a method on `org.gradle.api.artifacts.Dependency` called `lock` that takes a single `String` parameter with
the locked version. The `lock` method receives the `Dependency` created and immediately substitutes it with a `Dependency` with the locked version.
This happens before any other dependency-affecting event in the Gradle ecosystem.

In effect, `lock` makes it seem to Gradle that rather than writing a dynamic constraint like **latest.release** you had actually just written
in a fixed version.

If you want to just update a single dependency, rather than running ``./gradlew updateLock -PdependencyLock.updateDependencies=...`, you can simply
change the text of the `lock` in **build.gradle**.

## The `updateLocks` task

Running `./gradlew updateLocks` resolves each configuration without locks, and uses an AST parser to locate first order
dependencies in your **build.gradle** and write out the appropriate `lock` method call with the resolved version. `updateLocks`
also detects if you change the unlocked version to a static constraint and removes the `lock` method call.

### Multi-module projects

In the case of a dependency specified in a root project, e.g.

    subprojects {
        dependencies {
            compile 'com.google.guava:guava:latest.release'
        }
    }

the `updateLocks` task will evaluate the resolved configuration for each of the subprojects and lock at the highest dependency found.

### Comma-separated dependencies

Because Gradle returns `null` from a method call like

    compile 'com.google.guava:19.+',
            'commons-lang:commons-lang:2.+'

the task will generate the following:

    compile 'com.google.guava:19.+' lock '19.0'
    compile 'commons-lang:commons-lang:2.+' lock '2.6'

## Ignoring locks

Running with `-PdependencyLock.ignore` causes the lock method to short-circuit and leave dynamic constraints in effect.

## What about locking transitives?

We no longer believe there are any reasons to lock transitive dependencies, because

1. The vast majority of Java libraries are published with fixed versions (and Gradle does not support Ivy's `revConstraint`)
2. Responsibility for version alignment has been externalized to [gradle-resolution-rules](https://github.com/nebula-plugins/gradle-resolution-rules-plugin).

