plugins {
    id("package-conventions")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":rdf"))
            }
        }
    }
}

//// configuring the test module's tests to run upon building, but only after the test module has
////  been evaluated, as otherwise that task does not exist
gradle.projectsEvaluated {
    val testsDisabled = tasks
        .withType(AbstractTestTask::class.java)
        .any { it.ignoreFailures }
    val test = project(":sparql:test")
        .tasks
        .named("test")
    test.configure {
        if (testsDisabled) {
            println("Ignoring failures from ${this.project.path}:$name!")
        }
        this as AbstractTestTask
        ignoreFailures = testsDisabled
    }
    tasks
        .withType(AbstractTestTask::class.java)
        .all { finalizedBy(test) }
}
