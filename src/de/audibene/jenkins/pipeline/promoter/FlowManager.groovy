package de.audibene.jenkins.pipeline.promoter

import de.audibene.jenkins.pipeline.scm.Scm

import static de.audibene.jenkins.pipeline.Milestones.BUILD
import static de.audibene.jenkins.pipeline.Milestones.PROMOTE
import static java.util.Objects.requireNonNull

class FlowManager {

    private final def script
    private final Scm scm

    FlowManager(script, Scm scm) {
        this.script = script
        this.scm = scm
    }

    def promote(Map params) {
        script.milestone(ordinal: PROMOTE + 100)


        List<String> branches = params.branches ?: []
        if (branches.empty) {
            String branch = requireNonNull(params.branch, 'FlowManager.promote(params[branch])')
            branches.add(branch)
        }

        String title = params.get('title', "Promote to ${branches.first()}")

        if (!params.get('auto', false)) {
            script.approveStep("$title?")
        }

        script.milestone(ordinal: PROMOTE + 200)

        script.buildStep(title) {
            scm.checkout {
                script.milestone(ordinal: PROMOTE + 300)

                if (params.get('start', false)) {
                    boolean allowStarted = params.get('allowStarted', true)
                    String increment = params.get('increment', '0.1.0')
                    startVersion(allowStarted, increment)
                }

                script.milestone(ordinal: PROMOTE + 400)

                if (params.get('finish', false)) {
                    boolean allowFinished = params.get('allowFinished', true)
                    finishVersion(allowFinished)
                }

                script.milestone(ordinal: PROMOTE + 500)

                for (String branch : branches) {
                    scm.branch(branch)
                }

                script.milestone(ordinal: PROMOTE + 600)
            }
        }


    }

    def resolveVersion(Map params = [:]) {
        scm.checkout {
            String versionTag = resolveVersionTag()
            String version = extractVersion(versionTag)

            if (params.get('started', false)) {
                if (!versionTag.endsWith('-started')) {
                    throw new IllegalArgumentException("Version $version already finished")
                }
            }
            if (params.get('finished', false)) {
                if (!versionTag.endsWith('-end')) {
                    throw new IllegalArgumentException("Version $version not yet finished")
                }
            }

            return version
        }
    }

    def resolveHeadTag(boolean required = false, String... candidates) {
        scm.checkout {
            List<String> tags = scm.headTags()
            for (String candidate : candidates) {
                def tag = tags.findAll { it.startsWith(candidate) }.max()
                if (tag) {
                    return tag
                }
            }
            if (required) {
                throw new IllegalStateException("There is no head tags matched by $candidates")
            } else {
                return null
            }
        }
    }

    private resolveVersionTag() {
        List<String> versionTags = scm.branchTags().findAll { it.startsWith('version') }
        return versionTags.max() ?: 'initial'
    }

    private void finishVersion(boolean allowFinished) {
        String versionTag = resolveVersionTag()
        if (versionTag.endsWith('-begin')) {
            scm.tag(versionTag.replace('-begin', '-end'))
        } else {
            if (!allowFinished) {
                def version = extractVersion(versionTag)
                throw new IllegalArgumentException("Version $version already finished")
            }
        }
    }

    private void startVersion(boolean allowStarted, String increment) {
        String versionTag = resolveVersionTag()
        String version = extractVersion(versionTag)

        if (versionTag.endsWith('-begin')) {
            if (!allowStarted) {
                throw new IllegalArgumentException("Version $version already started")
            }
        } else if (versionTag.endsWith('-end')) {
            def next = nextVersion(version, increment)
            scm.tag("version-$next-begin")
        } else {
            scm.tag('version-1.0.0-begin')
        }
    }

    private static String extractVersion(String versionTag) {
        return versionTag.replace('version-', '').replace('-begin', '').replace('-end', '')
    }

    private static String nextVersion(String version, String increment) {
        def versions = version.tokenize('.')
        def increments = increment.tokenize('.')

        for (int index : 0..2) {
            int versionValue = Integer.parseInt(versions[index])
            int incrementValue = Integer.parseInt(increments[index])
            versions[index] = "${versionValue + incrementValue}"
        }

        return versions.join('.')
    }

    FlowManager validated() {
        requireNonNull(script, 'FlowManager.script')
        requireNonNull(scm, 'FlowManager.scm')
        return this
    }

}
