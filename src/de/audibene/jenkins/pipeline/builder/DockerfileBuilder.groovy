package de.audibene.jenkins.pipeline.builder

import static de.audibene.jenkins.pipeline.Objects.requireNonNull

class DockerfileBuilder implements ArtifactBuilder {

    private final def script
    private final Map<String, Closure> steps
    private final Map<String, Object> image
    private final Map<String, Object> artifact

    DockerfileBuilder(script, config) {
        this.script = script
        this.steps = config.steps ?: requireNonNull('steps') as Map
        this.image = config.image ?: requireNonNull('image') as Map
        this.artifact = config.artifact ?: requireNonNull('artifact') as Map
    }

    def inside(image = this.image, body) {
        script.docker.image(image.id).inside(image.args) {
            body()
        }
    }

    def withRun(image, body) {
        script.docker.image(image.id).withRun(image.args) { container ->
            body("--link ${container.id}:${image.linkAs}")
        }
    }

    def insideWithPostgres(def params = [:], body) {
        String imageVersion = params.version ?: 'latest'
        String imageId = "postgres:$imageVersion"
        String username = params.username ?: 'postgres'
        String password = params.password ?: 'postgres'
        String args = "-e 'POSTGRES_USER=$username' -e 'POSTGRES_PASSWORD=$password'"

        withRun(id: imageId, args: args, linkAs: 'postgres') { link ->

            inside(id: imageId, args: "$args $link") {
                script.sh 'while ! pg_isready -h postgres -q; do sleep 1; done'
            }

            inside(id: image.id, args: "${image.args} $link") {
                body()
            }
        }
    }

    private def runStep(String name) {
        def body = steps[name]
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = this
        body()
    }

    @Override
    String build(Map parameters) {
        def verbose = parameters.verbose ?: true
        def push = parameters.push ?: false
        def tag = parameters.tag ?: requireNonNull('tag', push)

        def imageName = null

        script.node('ecs') {
            wrappedStage('Build', !verbose) {
                wrappedStage('Prepare', verbose) {
                    script.deleteDir()
                    script.checkout script.scm
                    runStep('prepare')
                }
                wrappedStage('Test', verbose) {
                    runStep('test')
                }
                wrappedStage('IT', verbose) {
                    runStep('it')
                }
                wrappedStage('Build', verbose) {
                    runStep('build')
                    def dockerImage = script.docker.build(artifact.name)
                    if (push) {
                        script.docker.withRegistry(artifact.registry) {
                            script.sh script.ecrLogin()
                            dockerImage.push(tag)
                            imageName = "${dockerImage.imageName()}:$tag"
                        }

                    }
                }
            }
        }

        return imageName
    }

    def wrappedStage(String name, boolean verbose, Closure body) {
        if (verbose) {
            script.stage(name) {
                body()
            }
        } else {
            body()
        }
    }
}