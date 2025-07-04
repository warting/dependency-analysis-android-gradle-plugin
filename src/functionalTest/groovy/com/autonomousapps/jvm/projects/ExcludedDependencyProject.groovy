package com.autonomousapps.jvm.projects

import com.autonomousapps.AbstractProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.model.Advice
import com.autonomousapps.model.ProjectAdvice

import static com.autonomousapps.AdviceHelper.*

/**
 * Given a build script like this:
 *
 * <pre>
 * // build.gradle
 * plugins {
 *   id("java")
 * }
 *
 * configurations.configureEach {
 *   if (canBeResolved) {
 *     exclude(group: "com.squareup.okio", module: "okio")
 *   }
 * }
 *
 * dependencies {
 *   implementation("com.squareup.okio:okio:2.6.0")
 * }
 * </pre>
 *
 * The plugins should report {@code com.squareup.okio:okio:2.6.0} as unused because it's excluded.
 */
final class ExcludedDependencyProject extends AbstractProject {

  private final okio = dependencies.okio('implementation')
  final GradleProject gradleProject

  ExcludedDependencyProject() {
    this.gradleProject = build()
  }

  private GradleProject build() {
    return newGradleProjectBuilder()
      .withSubproject('consumer') { s ->
        s.withBuildScript { bs ->
          bs.plugins(javaLibrary)
          bs.dependencies(okio)

          def i = okio.identifier.indexOf(':')
          def okioGroup = okio.identifier.substring(0, i)
          def okioModule = okio.identifier.substring(i + 1, okio.identifier.length())
          bs.withGroovy(
            """\
            configurations.configureEach {
              if (canBeResolved) {
                exclude(group: '$okioGroup', module: '$okioModule')
              }
            }
            """.stripIndent()
          )
        }
      }
      .write()
  }

  Set<ProjectAdvice> actualBuildHealth() {
    return actualProjectAdvice(gradleProject)
  }

  private final Set<Advice> consumerAdvice = [
    Advice.ofRemove(moduleCoordinates(okio), okio.configuration)
  ]

  final Set<ProjectAdvice> expectedBuildHealth = [
    projectAdviceForDependencies(':consumer', consumerAdvice),
  ]
}
