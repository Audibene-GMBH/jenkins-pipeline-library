package de.audibene.jenkins.pipeline

class Configurers {

    static <T> T configure(T config, Closure body) {
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()
        return config
    }

    static Map configure(Closure body) {
        return configure([:], body)
    }
}
