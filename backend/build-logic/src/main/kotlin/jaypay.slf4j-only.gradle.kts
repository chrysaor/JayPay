plugins {
    java
}

val consoleOutput = Regex("""System\s*\.\s*(out|err)\b|\.printStackTrace\s*\(""")
val mainSources = the<SourceSetContainer>().named("main").map { it.allJava }
val projectDirectory = layout.projectDirectory

val noConsoleOutput = tasks.register("noConsoleOutput") {
    group = "verification"
    description = "Fails when main code writes to System.out, System.err or printStackTrace instead of SLF4J."
    inputs.files(mainSources).withPropertyName("mainSources")
    doLast {
        val root = projectDirectory.asFile
        val offenders = mainSources.get().files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (consoleOutput.containsMatchIn(line)) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException("Log through SLF4J instead of the console:\n" + offenders.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(noConsoleOutput)
}
