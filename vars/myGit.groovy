def call(body) {
    return new Git(this,  configure(body))
}

class Git {
    private final def script
    private final Map config

    Git(script, config) {
        this.script = script
        this.config = config
        this.script.git = this
    }

    def tag(tag) {
        execute "git tag -a $tag -m 'create tag: $tag'"
        execute "git push origin --tags"
    }

    def branch(branch) {
        execute "git push origin HEAD:$branch"
    }

    def execute(command) {
        script.buildNode {
            script.sh "git config user.name '${config.username}'"
            script.sh "git config user.email '${config.email}'"

            script.sshagent([config.credentials]) {
                script.sh command
            }
        }
    }
}