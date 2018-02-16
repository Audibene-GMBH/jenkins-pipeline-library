package de.audibene.jenkins.pipeline.deployer

import static de.audibene.jenkins.pipeline.Objects.requireNonNull

class BeansTalkDeployer implements ArtifactDeployer {

    private final def script
    private final Map params

    BeansTalkDeployer(def script, params) {
        this.script = script
        this.params = params
    }

    @Override
    def deploy(final Map params) {
        def artifact = params.artifact ?: requireNonNull('artifact')
        def environment = params.environment ?: requireNonNull('environment')

        script.stage("Deploy to ${environment}?") {

        }

        script.input("Deploy to ${environment}?")

        script.stage("Deploy to ${environment}") {
            script.echo "TODO: deploy $artifact to $environment"
        }
    }
}
