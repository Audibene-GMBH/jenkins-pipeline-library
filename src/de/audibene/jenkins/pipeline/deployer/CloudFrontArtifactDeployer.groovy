package de.audibene.jenkins.pipeline.deployer

import static de.audibene.jenkins.pipeline.Milestones.DEPLOY
import static java.util.Objects.requireNonNull
import com.amazonaws.services.s3.model.PutObjectRequest;

// import groovy.json.*

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
        def artifact = requireNonNull(params.artifact, {
            'CloudFrontArtifactDeployer.deploy(params.artifact)'
        }) as String

        def environment = requireNonNull(params.environment, {
            'CloudFrontArtifactDeployer.deploy(params.environment)'
        }) as String

        def environmentConfig = requireNonNull(config.environments[environment], {
             "CloudFrontArtifactDeployer.config.environments.${environment}"
        }) as Map<String, Object>

        def deployBucket = requireNonNull(environmentConfig.bucket, {
            "CloudFrontArtifactDeployer.config.environments.${environment}.bucket"
        }) as String

        def artifactBucket = requireNonNull(config.artifactBucket, {
            'CloudFrontArtifactDeployer.config.artifactBucket'
        }) as String

        def environmentParams = requireNonNull(environmentConfig.params ?: [:], {
            "CloudFrontArtifactDeployer.config.environments.${environment}.params"
        }) as Map<String, Object>

        script.lock(resource: "${script.env.JOB_NAME}:deploy:$environment", inversePrecedence: true) {
            script.milestone(ordinal: DEPLOY + 300)
            

            script.buildNode(config.node) {
                script.buildStep("Deploy to ${environment}") {
                    script.s3Download(file: "${artifact}.gz", bucket: artifactBucket, path: "${artifact}.gz")
                    script.sh "tar -xzf ${artifact}.gz"

                    // script.echo "environmentParams"
                    // script.println new JsonBuilder(environmentParams).toPrettyString()
                    for(entry in mapToList(environmentParams)) {
                        script.echo "sed -i 's/${entry[0]}/${entry[1]}/g' build/index.html"
                        script.sh "sed -i 's/${entry[0]}/${entry[1]}/g' build/index.html"
                    }

                    script.s3Upload(workingDir: 'build', includePathPattern: '**/*', bucket: deployBucket, path: 'dist', acl: 'PublicRead')
                    script.s3Upload(workingDir: 'build', file: 'index.html', bucket: deployBucket, path: 'dist/index.html', acl: 'PublicRead', cacheControl:'no-cache')
                    
                    // because upload is not supporting AccessControlList
                    script.sh "aws s3 cp s3://${deployBucket}/ s3://${deployBucket}/ --recursive --acl bucket-owner-full-control"
                    // remove the old artifact
                    script.sh "rm ${artifact}.gz"
                }
            }
            script.milestone(ordinal: DEPLOY + 400)
        }
    }

    def validated() {
        requireNonNull(config.environments, "BeansTalkDeployer.config.environments")
        requireNonNull(config.artifactBucket, "BeansTalkDeployer.config.artifactBucket")
        return this
    }
    // Required due to JENKINS-27421
    @NonCPS
    List<List<?>> mapToList(Map map) {
        return map.collect { it ->
            [it.key, it.value]
        }
    }
}
