@file:JsModule("fs")
package dev.tesserakt.util.node

external fun readFileSync(filepath: String, opts: dynamic): dynamic

external fun writeFileSync(filepath: String, content: String, opts: dynamic): dynamic
