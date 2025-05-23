// Copyright (c) 2024. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package com.autonomousapps.internal.advice

import com.autonomousapps.internal.utils.Colors
import com.autonomousapps.internal.utils.Colors.colorize
import com.autonomousapps.internal.utils.appendReproducibleNewLine
import com.autonomousapps.internal.utils.mapToOrderedSet
import com.autonomousapps.model.*

internal class ProjectHealthConsoleReportBuilder(
  private val projectAdvice: ProjectAdvice,
  private val postscript: String,
  dslKind: DslKind,
  /** Customize how dependencies are printed. */
  dependencyMap: ((String) -> String?)? = null,
  useTypesafeProjectAccessors: Boolean,
) {

  val text: String

  private val advicePrinter = AdvicePrinter(dslKind, dependencyMap, useTypesafeProjectAccessors)
  private var shouldPrintNewLine = false

  init {
    val dependencyAdvice = projectAdvice.dependencyAdvice
    val removeAdvice = mutableSetOf<Advice>()
    val addAdvice = mutableSetOf<Advice>()
    val changeAdvice = mutableSetOf<Advice>()
    val runtimeOnlyAdvice = mutableSetOf<Advice>()
    val compileOnlyAdvice = mutableSetOf<Advice>()
    val processorAdvice = mutableSetOf<Advice>()

    dependencyAdvice.forEach { advice ->
      if (advice.isRemove()) removeAdvice += advice
      if (advice.isAdd()) addAdvice += advice
      if (advice.isChange()) changeAdvice += advice
      if (advice.isRuntimeOnly()) runtimeOnlyAdvice += advice
      if (advice.isCompileOnly()) compileOnlyAdvice += advice
      if (advice.isProcessor()) processorAdvice += advice
    }

    text = buildString {
      if (removeAdvice.isNotEmpty()) {
        // This is the first printed advice, so we don't print new lines here.
        shouldPrintNewLine = true
        appendReproducibleNewLine("Unused dependencies which should be removed:")

        val toPrint = removeAdvice.mapToOrderedSet {
          line(it.fromConfiguration!!, printableIdentifier(it.coordinates))
        }.joinToString(separator = "\n")
        append(toPrint)
      }

      if (addAdvice.isNotEmpty()) {
        maybeAppendTwoLines()
        appendReproducibleNewLine("These transitive dependencies should be declared directly:")

        val toPrint = addAdvice.mapToOrderedSet {
          line(it.toConfiguration!!, printableIdentifier(it.coordinates))
        }.joinToString(separator = "\n")
        append(toPrint)
      }

      if (changeAdvice.isNotEmpty()) {
        maybeAppendTwoLines()
        appendReproducibleNewLine("Existing dependencies which should be modified to be as indicated:")

        val toPrint = changeAdvice.mapToOrderedSet {
          line(it.toConfiguration!!, printableIdentifier(it.coordinates), " (was ${it.fromConfiguration})")
        }.joinToString(separator = "\n")
        append(toPrint)
      }

      if (runtimeOnlyAdvice.isNotEmpty()) {
        maybeAppendTwoLines()
        appendReproducibleNewLine("Dependencies which should be removed or changed to runtime-only:")

        val toPrint = runtimeOnlyAdvice.mapToOrderedSet {
          line(it.toConfiguration!!, printableIdentifier(it.coordinates), " (was ${it.fromConfiguration})")
        }.joinToString(separator = "\n")
        append(toPrint)
      }

      if (compileOnlyAdvice.isNotEmpty()) {
        maybeAppendTwoLines()
        appendReproducibleNewLine("Dependencies which could be compile-only:")

        val toPrint = compileOnlyAdvice.mapToOrderedSet {
          line(it.toConfiguration!!, printableIdentifier(it.coordinates), " (was ${it.fromConfiguration})")
        }.joinToString(separator = "\n")
        append(toPrint)
      }

      if (processorAdvice.isNotEmpty()) {
        maybeAppendTwoLines()
        appendReproducibleNewLine("Unused annotation processors that should be removed:")

        val toPrint = processorAdvice.mapToOrderedSet {
          line(it.fromConfiguration!!, printableIdentifier(it.coordinates))
        }.joinToString(separator = "\n")
        append(toPrint)
      }

      val pluginAdvice = projectAdvice.pluginAdvice
      if (pluginAdvice.isNotEmpty()) {
        maybeAppendTwoLines()
        appendReproducibleNewLine("Unused plugins that can be removed:")

        val toPrint = pluginAdvice.mapToOrderedSet {
          "  ${it.redundantPlugin}: ${it.reason}"
        }.joinToString(separator = "\n")
        append(toPrint)
      }

      appendModuleAdvice()
      appendWarnings()
      appendPostscript()
    }.trimEnd()
  }

  private fun StringBuilder.maybeAppendTwoLines() {
    if (shouldPrintNewLine) {
      appendReproducibleNewLine()
      appendReproducibleNewLine()
    }
    shouldPrintNewLine = true
  }

  private fun StringBuilder.appendModuleAdvice() {
    val moduleAdvice = projectAdvice.moduleAdvice
    if (!moduleAdvice.hasPrintableAdvice()) return

    if (moduleAdvice.isNotEmpty()) {
      maybeAppendTwoLines()
      appendReproducibleNewLine("Module structure advice")

      moduleAdvice.forEach { m ->
        when (m) {
          is AndroidScore -> if (m.couldBeJvm()) append(m.text())
        }
      }
    }
  }

  private fun StringBuilder.appendWarnings() {
    val duplicateClasses = projectAdvice.warning.duplicateClasses
    if (duplicateClasses.isEmpty()) return

    maybeAppendTwoLines()
    appendReproducibleNewLine("Warnings")

    appendReproducibleNewLine("Some of your classpaths have duplicate classes, which means the compile and runtime behavior can be sensitive to the classpath order.")
    appendReproducibleNewLine()

    duplicateClasses
      .mapToOrderedSet { it.sourceKind.name }
      .forEachIndexed { i, v ->
        if (i > 0) appendReproducibleNewLine()

        appendReproducibleNewLine("Source set: $v")

        val duplicatesByVariant = duplicateClasses.filter { it.sourceKind.name == v }

        duplicatesByVariant
          .mapToOrderedSet { it.classpathName }
          .forEach { c ->
            "$c classpath".let { txt ->
              append("\\--- ")
              appendReproducibleNewLine(txt)
            }

            val duplicatesByClasspath = duplicatesByVariant.filter { it.classpathName == c }
            duplicatesByClasspath
              .filter { it.classpathName == c }
              .forEachIndexed { i, d ->
                // TODO(tsr): print capabilities too
                val deps = d.dependencies
                  .map { if (it is IncludedBuildCoordinates) it.resolvedProject else it }
                  .map { it.gav() }

                if (duplicatesByClasspath.size > 1 && i < duplicatesByClasspath.size - 1) {
                  append("     +--- ")
                } else {
                  append("     \\--- ")
                }

                appendReproducibleNewLine("${d.className} is provided by multiple dependencies: $deps")
              }
          }
      }
  }

  private fun StringBuilder.appendPostscript() {
    // Only print the postscript if there is anything at all to report.
    if (isEmpty() || postscript.isEmpty()) return

    maybeAppendTwoLines()
    appendReproducibleNewLine(postscript.colorize(Colors.BOLD))
  }

  private fun Set<ModuleAdvice>.hasPrintableAdvice(): Boolean {
    return ModuleAdvice.isNotEmpty(this)
  }

  private fun AndroidScore.text() = buildString {
    if (shouldBeJvm()) {
      appendReproducibleNewLine("This project doesn't use any Android features and should be a JVM project.")
    } else {
      appendReproducibleNewLine("This project uses limited Android features and could be a JVM project.")
      if (usesAndroidClasses) appendReproducibleNewLine("* Uses Android classes.")
      if (hasAndroidRes) appendReproducibleNewLine("* Uses Android resources.")
      if (hasAndroidAssets) appendReproducibleNewLine("* Contains Android assets.")
      if (hasBuildConfig) appendReproducibleNewLine("* Includes BuildConfig.")
      if (hasAndroidDependencies) appendReproducibleNewLine("* Has Android library dependencies.")
    }
  }

  private fun line(configuration: String, printableIdentifier: String, was: String = ""): String {
    return advicePrinter.line(configuration, printableIdentifier, was)
  }

  private fun printableIdentifier(coordinates: Coordinates) = advicePrinter.gav(coordinates)
}
