package de.audibene.jenkins.pipeline.exception

class ApproveStepRejected extends RuntimeException {
    private final String username;

    ApproveStepRejected(final String username) {
        super("Rejected by $username")
        this.username = username
    }
}
