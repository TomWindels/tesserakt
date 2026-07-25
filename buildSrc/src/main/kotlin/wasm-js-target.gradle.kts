import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    id("base-config")
}

repositories {
    mavenCentral()
}

// ensuring the yarn lock file behaves
// copied from the `js-package` convention plugin, but adapted
rootProject.plugins.withType(WasmYarnPlugin::class.java) {
    rootProject.the<WasmYarnRootExtension>().yarnLockMismatchReport = YarnLockMismatchReport.NONE
    rootProject.the<WasmYarnRootExtension>().reportNewYarnLock = true
    rootProject.the<WasmYarnRootExtension>().yarnLockAutoReplace = true
}

kotlin {
    // target configuration
    @Suppress("OPT_IN_USAGE")
    wasmJs {
        nodejs()
        browser {
            testTask {
                // we cannot guarantee every system has the same set of browsers available, so the browser target
                //  should not be tested
                enabled = false
            }
        }
    }
}
