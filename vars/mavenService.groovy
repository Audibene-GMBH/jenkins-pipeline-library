import static de.audibene.jenkins.pipeline.Configurers.configure
import static java.util.Objects.requireNonNull

def call(Map appConfig, Closure body) {
    def cfg = configure(new ServiceConfig(this, appConfig), body)
    def gitConfig = cfg.gitConfig
    def ebtConfig = cfg.ebtConfig
    def dockerConfig = cfg.dockerConfig

    threeFlowPipeline() {
        git {
            username = gitConfig.username
            email = gitConfig.email
            credentials = gitConfig.credentials
        }

        beansTalk {
            application = appConfig.name
            platform = ebtConfig.platform
            region = ebtConfig.region
            port = ebtConfig.port

            onDeploy = { environment, version ->
                def issues = jiraIssueSelector(issueSelector: [$class: 'DefaultIssueSelector'])
                issues.each { issue ->
                    jiraComment issueKey: issue, body: "${appConfig.name} changes deployed to $environment in version $version"
                }
            }
        }

        stages {
            config {
                node = dockerConfig.label
                docker = fluentDocker()
                registry = ecrRegistry()
            }

            build {
                buildStep('Environment') {
                    java = docker.image(id: dockerConfig.java, args: '-v "$HOME/.m2":/root/.m2').pull()
                    pg = docker.image(id: dockerConfig.postgres, args: "-e 'POSTGRES_PASSWORD=postgres'").pull()
                    pg.hooks.beforeAround = { pg.link('pg', it) { sh 'while ! pg_isready -h pg -q; do sleep 1; done' } }
                }

                buildStep('Validate') {
                    java.inside {
                        mvn 'verify -DskipTests -DskipITs'
                    }
                }
                buildStep('Test') {
                    java.inside {
                        mvn 'verify -DskipITs'
                    }
                }
                buildStep('Verify') {
                    java.args('-e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/postgres').with('pg', pg) {
                        mvn 'verify -DskipTests'
                    }
                }
                buildStep('Build') {
                    artifact = docker.image(name: appConfig.id, tag: tag).build(dockerConfig.context)
                }
            }

            retag {
                buildStep('Retag') {
                    previous = docker.image(name: appConfig.id, tag: previousTag)
                    artifact = previous.pull(registry).tag(tag)
                }
            }

            publish {
                buildStep('Publish') {
                    artifact = artifact.push(registry)
                }
            }
        }

    }
}

class ServiceConfig {
    def script


    ServiceConfig(def script, def appConfig) {
        this.script = script
        requireNonNull(appConfig)
        requireNonNull(appConfig.id, 'Application id is not provided')
        requireNonNull(appConfig.name, 'Application name is not provided')
    }

    def gitConfig = [
            username   : "jenkins-audibene",
            email      : "jenkins@audibene.de",
            credentials: "jenkins-audibene-ssh",
    ]

    def ebtConfig = [
            platform: 'docker',
            region  : 'eu-central-1',
            port    : 8080,
    ]

    def dockerConfig = [
            label   : 'ecs',
            context : '.',
            java    : 'maven:3-jdk-8-slim',
            postgres: 'postgres:9.6'
    ]

    def git(Closure body) {
        configure(gitConfig, body)
    }

    def ebt(Closure body) {
        configure(ebtConfig, body)
    }

    def docker(Closure body) {
        configure(dockerConfig, body)
    }

}