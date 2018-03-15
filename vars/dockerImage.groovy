import de.audibene.jenkins.pipeline.docker.Image
import static de.audibene.jenkins.pipeline.Configurers.configure

def call(Map params = [:], Closure body = {}) {
    Map config = configure(params, body)
    return Image.create(this, config)
}

