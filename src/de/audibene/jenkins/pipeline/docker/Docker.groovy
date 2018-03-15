package de.audibene.jenkins.pipeline.docker

import static de.audibene.jenkins.pipeline.Configurers.configure

class Docker {

    private final def script

    Docker(script) {
        this.script = script
    }

    Image image(Map config, Closure body = {}) {
        return Image.create(script, configure(config, body))
    }
}
