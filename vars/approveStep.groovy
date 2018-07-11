#!groovy
import com.cloudbees.groovy.cps.NonCPS
import de.audibene.jenkins.pipeline.exception.ApproveStepRejected
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(String name, Map params = [:]) {
    def time = params.get('time', 365)
    def unit = params.get('unit', 'DAYS')
    def message = params.message ?: name
    def timeoutAs = params.get('timeoutAs', false)

    buildStep(name) {
        def approve = getApprove(time, unit, message, timeoutAs)
        def result = approve.result ? "approved" : "rejected"
        echo "$name: $result by ${approve.userName}"

        if (!approve.result) {
            stageStatus = 'ABORTED'
            throw new ApproveStepRejected("Rejected by ${approve.userName}")
        }
    }

}

private def getApprove(time, unit, message, timeoutAs) {
    try {
        timeout(time: time, unit: unit) {
            input(message)
        }
        return [result: true, userName: getApprover()]
    } catch (FlowInterruptedException e) {
        def rejectedBy = getRejectedBy(e)
        def result = rejectedBy == "SYSTEM" && timeoutAs
        return [result: result, userName: rejectedBy]
    }
}

@NonCPS
@SuppressWarnings("GrMethodMayBeStatic")
def getRejectedBy(FlowInterruptedException e) {
    def rejection = e.causes[0]

    return rejection.user?.displayName ?: "SYSTEM"
}

//http://stackoverflow.com/questions/32858782/get-input-approver-user-name-in-jenkins-workflow-plugin
//todo approving "method org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper getRawBuild" may introduce security vulnerability
@NonCPS
def getApprover() {
    def latest = null

    // this returns a CopyOnWriteArrayList, safe for iteration
    def acts = currentBuild.rawBuild.getAllActions()
    for (act in acts) {
        if (act instanceof org.jenkinsci.plugins.workflow.support.steps.input.ApproverAction) {
            latest = act.userName
        }
    }
    return latest
}

