import static de.audibene.jenkins.pipeline.Configurers.configure

def call(Closure body) {

    def config = configure(new LibraryConfig(this), body)
    def dockerConfig = config.dockerConfig
    def gitConfig = config.gitConfig
    def mavenConfig = config.mavenConfig

    def buildTag = Long.toString(new Date().time, Character.MAX_RADIX)

    def maven = fluentDocker().image(id: dockerConfig.image, args: dockerConfig.args)

    buildNode(dockerConfig.label) {
        buildStep('Build') {
            checkout scm
            try {
                maven.inside { sh 'mvn -B clean verify' }
            } finally {
                junit 'target/*/*.xml'
            }
        }
    }


    if (env.BRANCH_NAME) {
        approveStep('Release new version?')

        buildNode(dockerConfig.label) {
            buildStep('Release') {
                checkout scm
                String version = "${buildTag}.RELEASE"
                maven.inside {
                    configFileProvider([configFile(fileId: mavenConfig, variable: 'MAVEN_SETTINGS')]) {
                        sh "mvn -B versions:set -DnewVersion=$version"
                        sh 'mvn -s $MAVEN_SETTINGS -B clean deploy -DskipITs -DskipTests'
                    }
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
    def mavenConfig = 'nexus.audibene.net'
    def timeout = [time: 1, unit: 'HOURS']

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

