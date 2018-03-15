# jenkins-pipeline-library

Sandbox to provide jenkins shared library to support declarative pipeline, which:
* build pull requests and notify github
* build `develop` branch, auto-deploy to `development` env, ask for promotion to `candidate`
* build `candidate` branch (reuse artifact from `develop` if was promoted), ask for deploy to `staging` env, ask for promotion to `release`
* build `master` branch by reusing artifact from `candidate`, ask for deploy to `production` env`

Usage sample: 

```groovy
threeFlowPipeline {
    git {
        username = "jenkins-audibene"
        email = "jenkins@audibene.de"
        credentials = "jenkins-audibene-ssh"
    }

    beansTalk {
        platform = 'docker'
        application = 'aud-m-1'
        region = 'eu-central-1'
        port = 8080
    }

    stages {
        config {
            node = 'ecs'
            docker = fluentDocker()
            registry = ecrRegistry()
        }

        build {
            buildStep('Prepare') {
                java = docker.image(id: 'java:8-jdk', args: "-e 'GRADLE_USER_HOME=.gradle'").pull()
                postgres = docker.image(id: 'postgres:9.6', args: "-e 'POSTGRES_PASSWORD=postgres'").pull()
                postgres.hooks.beforeAround = {
                    postgres.link('pg', it) { sh 'while ! pg_isready -h pg -q; do sleep 1; done' }
                }
                java.inside { sh './gradlew prepare' }
            }
            buildStep('Test') {
                java.inside {
                    junitReport('build/test-results/*/*.xml') { sh './gradlew test' }
                }
            }
            buildStep('IT') {
                java.with('postgres', postgres) {
                    junitReport('build/test-results/*/*.xml') { sh './gradlew it' }
                }
            }
            buildStep('Build') {
                java.inside { sh './gradlew assemble' }
                artifact = docker.image(name: 'audibene-microservice', tag: tag).build()
            }
        }

        retag {
            buildStep('Retag') {
                previous = docker.image(name: 'audibene-microservice', tag: previousTag)
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
```