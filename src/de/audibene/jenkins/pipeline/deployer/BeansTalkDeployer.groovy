package de.audibene.jenkins.pipeline.deployer

import de.audibene.jenkins.pipeline.scm.Scm
import groovy.json.JsonBuilder

import static de.audibene.jenkins.pipeline.Milestones.DEPLOY
import static java.util.Objects.requireNonNull

class BeansTalkDeployer implements ArtifactDeployer {

    private final def script
    private final Map config
    private final Scm scm

    BeansTalkDeployer(def script, config, Scm scm) {
        this.scm = scm
        this.script = script
        this.config = config
    }

    @Override
    def deploy(final Map params) {
        script.milestone(ordinal: DEPLOY + 100)

        String environment = requireNonNull(params.environment, "BeansTalkDeployer.deploy(params.environment)")
        requireNonNull(params.artifact, "BeansTalkDeployer.deploy(params.artifact)")

        boolean auto = params.get('auto', false)

        if (!auto) {
            script.approveStep("Deploy to ${environment}?")
        }

        script.milestone(ordinal: DEPLOY + 200)

        script.lock(resource: "${script.env.JOB_NAME}:deploy:$environment", inversePrecedence: true) {
            script.milestone(ordinal: DEPLOY + 300)

            script.buildNode(config.node) {
                script.buildStep("Deploy to ${environment}") {
                    scm.checkout {
                        switch (config.platform) {
                            case 'docker': docker(params); break
                            default: unknown()
                        }
                    }
                }
            }

            script.milestone(ordinal: DEPLOY + 400)
        }
    }

    private void docker(Map params) {
        def port = requireNonNull(config.port, "BeansTalkDeployer.config.port") as int
        def artifact = requireNonNull(params.artifact, 'BeansTalkDeployer.deploy(params.artifact)') as String

        script.writeFile file: 'Dockerrun.aws.json', text: new JsonBuilder([
                "AWSEBDockerrunVersion": "1",
                "Image"                : ["Name": artifact, "Update": true],
                "Ports"                : [["ContainerPort": "$port"]]
        ]).toPrettyString()

        ArrayList<String> credentials = [
                config.get('org', 'aud'),
                config.get('account', 'shared'),
                params.environment
        ]

        ArrayList<String> environmentName = [
                config.application,
                params.environment
        ]

        script.step([
                $class                  : 'AWSEBDeploymentBuilder',
                credentialId            : config.get('credentialId', credentials.join('-')),
                awsRegion               : config.get('region'),
                applicationName         : config.application,
                environmentName         : environmentName.join('-'),
                bucketName              : config.get('bucket', ''),
                keyPrefix               : config.get('keyPrefix', config.application),
                versionLabelFormat      : params.get('label', artifact.split('/').last()),
                versionDescriptionFormat: params.get('description', artifact),
                rootObject              : config.get('root', '.'),
                includes                : config.get('includes', '.ebextensions/**,Dockerrun.aws.json'),
                excludes                : config.get('excludes', ''),
                zeroDowntime            : config.get('zeroDowntime', false),
                checkHealth             : config.get('checkHealth', true),
                sleepTime               : config.get('sleepTime', 10),
                maxAttempts             : config.get('maxAttempts', 20)
        ])
    }

    private void unknown() {
        throw new IllegalArgumentException("Unknown platform type: '${config.platform}'")
    }

    BeansTalkDeployer validated() {
        requireNonNull(script, "BeansTalkDeployer.script")
        requireNonNull(config.region, "BeansTalkDeployer.config.region")
        requireNonNull(config.platform, "BeansTalkDeployer.config.platform")
        requireNonNull(config.application, "BeansTalkDeployer.config.application")
        return this
    }
}
