// Copyright (c) 2024. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.exception.BuildHealthException
import com.autonomousapps.internal.utils.Colors
import com.autonomousapps.internal.utils.Colors.colorize
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.model.BuildHealth
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@UntrackedTask(because = "Always prints output")
public abstract class BuildHealthTask : DefaultTask() {

  init {
    group = TASK_GROUP_DEP
    description = "Generates holistic advice for whole project, and can fail the build if desired"
  }

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  public abstract val shouldFail: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  public abstract val buildHealth: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  public abstract val consoleReport: RegularFileProperty

  @get:Input
  public abstract val printBuildHealth: Property<Boolean>

  @get:Input
  public abstract val postscript: Property<String>

  @TaskAction public fun action() {
    val shouldFail = shouldFail.get().asFile.readText().toBoolean()
    val consoleReportFile = consoleReport.get().asFile
    val consoleReportPath = consoleReportFile.toPath()

    // if the report contains only warnings
    val isWarningOnly = buildHealth.fromJson<BuildHealth>().isEmptyOrWarningOnly()
    // if the console report is non-empty
    val hasText = consoleReportFile.length() > 0
    // user has requested we print buildHealth console report
    val printBuildHealth = printBuildHealth.get()

    val output = buildString {
      if (printBuildHealth) {
        append(consoleReportFile.readText())
      } else {
        // If we're not printing the build health report, we should still print the postscript.
        val ps = postscript.get()
        if (ps.isNotEmpty()) {
          append(ps.bold())
        }
      }

      // Need some space between report and help message explaining where report is located.
      appendLine()
      appendLine()

      if (isWarningOnly) {
        appendLine("There were non-fatal dependency warnings.".warning())
      } else if (!shouldFail) {
        appendLine("There were non-fatal dependency violations.".warning())
      } else {
        appendLine("There were fatal dependency violations.".error())
      }

      // Trailing space so terminal UIs linkify it
      append("See report at ${consoleReportPath.toUri()} ")
    }

    if (shouldFail) {
      check(hasText) { "Console report should not be blank if buildHealth should fail" }
      throw BuildHealthException(output)
    } else if (hasText) {
      logger.quiet(output)
    }
  }

  private fun String.bold() = colorize(Colors.BOLD)
  private fun String.error() = colorize(Colors.RED_BOLD)
  private fun String.warning() = colorize(Colors.YELLOW_BOLD)
}
