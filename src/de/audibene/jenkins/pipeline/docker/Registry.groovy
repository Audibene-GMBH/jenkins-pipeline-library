package de.audibene.jenkins.pipeline.docker

interface Registry {
    def <T> T call(Closure<T> body)
}