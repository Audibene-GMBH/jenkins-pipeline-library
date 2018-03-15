#!groovy
import de.audibene.jenkins.pipeline.builder.ArtifactBuilder
import de.audibene.jenkins.pipeline.builder.SimpleArtifactBuilder
import de.audibene.jenkins.pipeline.deployer.ArtifactDeployer
import de.audibene.jenkins.pipeline.deployer.BeansTalkDeployer
import de.audibene.jenkins.pipeline.exception.ApproveStepRejected
import de.audibene.jenkins.pipeline.promoter.FlowManager
import de.audibene.jenkins.pipeline.scm.Git
import de.audibene.jenkins.pipeline.scm.Scm

import static de.audibene.jenkins.pipeline.Configurers.configure
import static java.util.Objects.requireNonNull

def call(Closure body) {
    timestamps {
        pipeline(body)
    }
}

def pipeline(Closure body) {
    def buildId = Long.toString(new Date().time, Character.MAX_RADIX)

    FlowConfig config = configure(new FlowConfig(this), body).validate()

    env.DEFAULT_NODE_NAME = config.node ?: 'master'

    def builder = config.builder
    def deployer = config.deployer
    def branches = config.branches
    def environments = config.environments
    def scm = config.scm
    def flowManager = new FlowManager(this, scm).validated()

    def pullRequestFlow = {
        echo 'PR Flow'
        builder.build(null, true)
    }

    def snapshotFlow = {
        echo 'Snapshot Flow'
        String buildTag = "snapshot-$buildId"
        String artifact = builder.build(buildTag, false)
        deployer.deploy(artifact: artifact, environment: environments.snapshot, auto: true)
        flowManager.promote(title: 'Create Candidate', branch: branches.candidate, start: true)
    }

    def candidateFlow = {
        echo 'Candidate Flow'
        String version = flowManager.resolveVersion()
        String headTag = flowManager.resolveHeadTag('candidate', 'snapshot')
        String buildTag = "candidate-$version-$buildId"
        String artifact = headTag ? builder.retag(buildTag, headTag) : builder.build(buildTag, false)
        deployer.deploy(artifact: artifact, environment: environments.candidate)
        List<String> promoteBranches = [branches.release, "backmerge/$version"]
        flowManager.promote(title: 'Create Release', branches: promoteBranches, finish: true)
    }

    def releaseFlow = {
        echo 'Release Flow'
        String version = flowManager.resolveVersion(finished: true)
        String headTag = flowManager.resolveHeadTag(true, 'release', 'candidate')
        String buildTag = "release-$version-$buildId"
        String artifact = builder.retag(buildTag, headTag)
        deployer.deploy(artifact: artifact, environment: environments.release)
    }

    try {
        if (env.BRANCH_NAME.startsWith('PR-')) {
            pullRequestFlow()
        } else if (env.BRANCH_NAME == branches.snapshot) {
            snapshotFlow()
        } else if (env.BRANCH_NAME == branches.candidate) {
            candidateFlow()
        } else if (env.BRANCH_NAME == branches.release) {
            releaseFlow()
        }
    } catch (ApproveStepRejected ignore) {
        currentBuild.result = 'NOT_BUILT'
    }

}

class FlowConfig {
    def script
    Scm scm
    String node
    ArtifactBuilder builder
    ArtifactDeployer deployer
    Map<String, String> branches = [snapshot: 'develop', candidate: 'candidate', release: 'master']
    Map<String, String> environments = [snapshot: 'develop', candidate: 'staging', release: 'production']


    FlowConfig(script) {
        this.script = script
    }

    def git(Closure body) {
        def config = configure(body)
        scm = new Git(script, config).validated()
    }

    def stages(Closure body) {
        def builder = new SimpleArtifactBuilder(script, scm)
        this.builder = configure(builder, body).validated()
    }

    def beansTalk(Closure body) {
        def config = configure(body)
        def deployer = new BeansTalkDeployer(script, config)
        this.deployer = deployer.validated()
    }

    def validate() {
        requireNonNull(script, 'FlowConfig.script')
        requireNonNull(scm, 'FlowConfig.scm')
        requireNonNull(builder, 'FlowConfig.builder')
        requireNonNull(deployer, 'FlowConfig.deployer')
        requireNonNull(branches, 'FlowConfig.branches')
        requireNonNull(environments, 'FlowConfig.environments')
        return this
    }

}
