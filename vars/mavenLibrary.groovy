import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import static de.audibene.jenkins.pipeline.Configurers.configure

def call(Closure body) {


    def config = configure(new LibraryConfig(this), body)
    def dockerConfig = config.dockerConfig
    def gitConfig = config.gitConfig

    def buildTag = Long.toString(new Date().time, Character.MAX_RADIX)

    def java = fluentDocker().image(id: dockerConfig.image, args: dockerConfig.args)

    try {

        buildNode(dockerConfig.label) {
            buildStep('Build') {
                checkout scm
                java.inside { mvn 'clean verify' }
            }
        }


        if (env.BRANCH_NAME == 'master') {
            approveStep('Release new version?')

            buildNode(dockerConfig.label) {
                buildStep('Release') {
                    checkout scm
                    String version = "${buildTag}.RELEASE"
                    java.inside {
                        mvn "versions:set -DnewVersion=$version"
                        mvn "clean deploy -DskipITs -DskipTests"
                    }

                    sshagent([gitConfig.credentials]) {
                        sh "git config user.name '${gitConfig.username}'"
                        sh "git config user.email '${gitConfig.email}'"

                        sh "git commit -a -m 'Release version $version'"
                        sh "git push origin HEAD:refs/tags/$buildTag"
                    }
                }
            }
        }

    } catch (FlowInterruptedException ignore) {
        echo "build was interrupted, but pipeline"
        stageStatus = 'ABORTED'
        currentBuild.result = 'SUCCESS'
    }
}

class LibraryConfig {
    def script

    def dockerConfig = [
            label: 'ecs',
            image: 'maven:3-jdk-8-slim',
            args : '-v "$HOME/.m2":/root/.m2'
    ]
    def gitConfig = [
            username   : 'jenkins-audibene',
            email      : 'jenkins@audibene.de',
            credentials: 'jenkins-audibene-ssh'
    ]

    LibraryConfig(def script) {
        this.script = script
    }

    def git(Closure body) {
        gitConfig = configure(gitConfig, body)
    }

    def docker(Closure body) {
        dockerConfig = configure(dockerConfig, body)
    }
}

