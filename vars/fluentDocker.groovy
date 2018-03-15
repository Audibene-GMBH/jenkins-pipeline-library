import de.audibene.jenkins.pipeline.docker.Docker

def call() {
    return new Docker(this)
}