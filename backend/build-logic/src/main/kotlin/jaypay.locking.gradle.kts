dependencyLocking {
    lockAllConfigurations()
}

tasks.register("resolveAndLockAll") {
    group = "help"
    description = "Resolves every configuration, and with --write-locks records it in gradle.lockfile."
    notCompatibleWithConfigurationCache("Resolves configurations at execution time")
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
