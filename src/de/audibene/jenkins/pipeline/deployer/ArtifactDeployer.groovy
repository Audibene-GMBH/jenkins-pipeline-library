package de.audibene.jenkins.pipeline.deployer

interface ArtifactDeployer extends Serializable {

    def deploy(Map params)

}