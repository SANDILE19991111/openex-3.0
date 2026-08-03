package com.openex.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CoreReactorApplication

fun main(args: Array<String>) {
    runApplication<CoreReactorApplication>(*args)
}
