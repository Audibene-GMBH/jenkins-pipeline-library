import de.audibene.jenkins.pipeline.docker.Registry

def call() {
    return new EcrRegistry(this)
}

class EcrRegistry implements Registry {

    private def script
    private def docker

    EcrRegistry(script) {
        this.script = script
        this.docker= script.docker
    }

    @Override
    <T> T call(final Closure<T> body) {
        docker.node {
            String loginCommand = script.ecrLogin()
            String url = loginCommand.tokenize(' ').last()
            script.sh loginCommand
            docker.withRegistry(url, body)
        }
    }
}