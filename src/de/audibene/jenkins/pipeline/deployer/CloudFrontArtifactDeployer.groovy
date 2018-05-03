package de.audibene.jenkins.pipeline.deployer

import static de.audibene.jenkins.pipeline.Milestones.DEPLOY
import static java.util.Objects.requireNonNull

class CloudFrontArtifactDeployer implements ArtifactDeployer {

    private final def script
    private final Map config

    CloudFrontArtifactDeployer(def script, def config) {
        this.script = script
        this.config = config
    }

    @Override
    def deploy(final Map params) {
        script.milestone(ordinal: DEPLOY + 100)
        def artifactBucket = requireNonNull(config.artifactBucket, 'CloudFrontArtifactDeployer.config.artifactBucket') as String
        def artifact = requireNonNull(params.artifact, 'CloudFrontArtifactDeployer.deploy(params.artifact)') as String
        def environment = requireNonNull(params.environment, 'CloudFrontArtifactDeployer.deploy(params.environment') as String
        def environmentConfig = config.environments[environment] as Map<String, Object>
        def deployBucket = requireNonNull(environmentConfig.bucket, {
            "CloudFrontArtifactDeployer.config.environments.${environment}.bucket"
        }) as String

        script.s3Download(file: 'build.gz', bucket: artifactBucket, path: "{$artifact}.gz")
        script.sh 'tar -xzf build.gz'
        script.s3Upload(includePathPattern: 'build/*', bucket: deployBucket, path: 'dist', acl: 'PublicRead')

        def instance = requireNonNull(environmentConfig.instance, {
            "CloudFrontArtifactDeployer.config.environments.${environment}.instance"
        }) as String
        script.pritn("TODO: invalidate cloud front instance: $instance")
        print('TODO: invalidate cloudfront') //TODO
        script.milestone(ordinal: DEPLOY + 400)
    }

    def validated() {
        requireNonNull(config.environments, "BeansTalkDeployer.config.environments")
        requireNonNull(config.artifactBucket, "BeansTalkDeployer.config.artifactBucket")
        //todo check instance and bucket in environments
        return this
    }
}
