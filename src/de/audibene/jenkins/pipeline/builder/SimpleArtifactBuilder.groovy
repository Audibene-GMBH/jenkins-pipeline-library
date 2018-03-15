package de.audibene.jenkins.pipeline.builder

import de.audibene.jenkins.pipeline.scm.Scm

import static de.audibene.jenkins.pipeline.Configurers.configure
import static de.audibene.jenkins.pipeline.Milestones.BUILD
import static de.audibene.jenkins.pipeline.Milestones.DEPLOY
import static java.util.Objects.requireNonNull

class SimpleArtifactBuilder implements ArtifactBuilder {
    private final def script
    private final Scm scm
    private final Map<String, ?> config
    private final Map<String, Closure> steps

    SimpleArtifactBuilder(def script, Scm scm, Map<String, ?> config = [:], Map<String, Closure> steps = [:]) {
        this.script = script
        this.scm = scm
        this.config = config
        this.steps = steps
    }

    @Override
    String build(final String tag, final boolean verbose) {
        script.milestone(ordinal: BUILD + 100)
        def artifact = null

        def context = [tag: tag ?: 'latest']
        execute(context, 'config')

        script.milestone(ordinal: BUILD + 200)
        script.buildNode(context.node) {
            script.buildStep('Build', !verbose) {
                scm.checkout {
                    script.milestone(ordinal: BUILD + 300)
                    execute(context, 'build')
                    script.milestone(ordinal: BUILD + 400)
                    if (tag) {
                        execute(context, 'publish')
                        requireNonNull(context.artifact, 'Publish step should declare valid artifact')
                        artifact = requireNonNull(context.artifact.id, 'Artifact should have valid id')
                        scm.tag(tag)
                    }
                    script.milestone(ordinal: BUILD + 500)
                }
            }
        }
        script.milestone(ordinal: BUILD + 600)
        return artifact
    }

    @Override
    String retag(final String tag, final String previousTag) {
        script.milestone(ordinal: BUILD + 100)
        def artifact = null

        def context = [tag: tag ?: 'latest', previousTag: previousTag]
        execute(context, 'config')

        script.milestone(ordinal: BUILD + 200)
        script.buildNode(context.node) {
            script.buildStep('Build') {
                scm.checkout {
                    script.milestone(ordinal: BUILD + 300)
                    execute(context, 'retag')
                    script.milestone(ordinal: BUILD + 400)
                    execute(context, 'publish')
                    requireNonNull(context.artifact, 'Publish step should declare valid artifact')
                    artifact = requireNonNull(context.artifact.id, 'Artifact should have valid id')
                    scm.tag(tag)
                }
                script.milestone(ordinal: BUILD + 500)
            }
        }
        script.milestone(ordinal: BUILD + 600)
        return artifact
    }

    def config(Closure body) {
        steps.config = body
    }

    def build(Closure body) {
        steps.build = body
    }

    def retag(Closure body) {
        steps.retag = body
    }

    def publish(Closure body) {
        steps.publish = body
    }

    private void execute(context, String name) {
        if (steps[name]) {
            configure(context, steps[name])
        }
    }

    SimpleArtifactBuilder validated() {
        requireNonNull(script, 'SimpleArtifactBuilder.script')
        requireNonNull(scm, 'SimpleArtifactBuilder.scm')
        return this
    }

}
