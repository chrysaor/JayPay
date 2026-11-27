plugins {
    java
}

val consoleOutput = Regex("""System\s*\.\s*(out|err)\b|\.printStackTrace\s*\(""")
val logLineWithoutArguments = Regex("""\blog\s*\.\s*(trace|debug|info|warn|error)\s*\(\s*"[^"]*"\s*\)""")
val mainSources = the<SourceSetContainer>().named("main").map { it.allJava }
val projectDirectory = layout.projectDirectory

fun registerLineCheck(name: String, summary: String, offence: Regex, failure: String) =
    tasks.register(name) {
        group = "verification"
        description = summary
        inputs.files(mainSources).withPropertyName("mainSources")
        doLast {
            val root = projectDirectory.asFile
            val offenders = mainSources.get().files.sorted().flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (offence.containsMatchIn(line)) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
                }
            }
            if (offenders.isNotEmpty()) {
                throw GradleException(failure + ":\n" + offenders.joinToString("\n"))
            }
        }
    }

val noConsoleOutput = registerLineCheck(
    "noConsoleOutput",
    "Fails when main code writes to System.out, System.err or printStackTrace instead of SLF4J.",
    consoleOutput,
    "Log through SLF4J instead of the console")

val noLogLineWithoutAnId = registerLineCheck(
    "noLogLineWithoutAnId",
    "Fails when main code logs a fixed text with no argument, so the line names no id.",
    logLineWithoutArguments,
    "Name the id of what each log line is about, see Logging in CONTRIBUTING.md")

tasks.named("check") {
    dependsOn(noConsoleOutput, noLogLineWithoutAnId)
}
