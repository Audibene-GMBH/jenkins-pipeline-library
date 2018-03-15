package de.audibene.jenkins.pipeline.scm

import static java.util.Objects.requireNonNull

class Git implements Scm {
    private final def script
    private final Map config

    Git(script, Map config) {
        this.script = script
        this.config = config
    }

    @Override
    void checkout() {
        script.buildNode {
            script.checkout script.scm
        }
    }

    @Override
    <T> T checkout(final Closure body) {
        script.buildNode {
            script.checkout script.scm
            try {
                body()
            } finally {
                script.deleteDir()
            }
        }
    }

    @Override
    void tag(String tag) {
        execute {
            script.sh "git push origin HEAD:refs/tags/$tag"
        }
    }

    @Override
    void branch(String branch) {
        execute {
            script.sh "git push origin HEAD:refs/heads/$branch"
        }
    }

    @Override
    List<String> headTags() {
        execute {
            script.sh 'git tag -l | xargs git tag -d'
            script.sh 'git fetch --tags'
            String output = script.sh(returnStdout: true, script: 'git tag --list --points-at HEAD')
            output.tokenize("\n").collect { it.trim() }
        }
    }

    @Override
    List<String> branchTags() {
        execute {
            script.sh 'git tag -l | xargs git tag -d'
            script.sh 'git fetch --tags'
            String output = script.sh(returnStdout: true, script: 'git tag --list --merged HEAD')
            output.tokenize("\n").collect { it.trim() }
        }
    }

    private <T> T execute(body) {
        script.buildNode {
            script.sh "git config user.name '${config.username}'"
            script.sh "git config user.email '${config.email}'"

            script.sshagent([config.credentials]) {
                body()
            }
        }
    }

    Git validated() {
        requireNonNull(config.credentials, 'Git.config.credentials')
        requireNonNull(config.username, 'Git.config.username')
        requireNonNull(config.email, 'Git.config.email')
        return this
    }
}
