package io.github.drawjustin.kairos

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KairosApplication

fun main(args: Array<String>) {
	runApplication<KairosApplication>(*args)
}
