package de.audibene.jenkins.pipeline.builder

import de.audibene.jenkins.pipeline.scm.Scm

import static de.audibene.jenkins.pipeline.Configurers.configure
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
        def artifact = null

        def context = [tag: tag ?: 'latest']
        execute(context, 'config')

        script.buildNode(context.node) {
            script.buildStep('Build', !verbose) {
                scm.checkout {
                    execute(context, 'build')
                    if (tag) {
                        execute(context, 'publish')
                        requireNonNull(context.artifact, 'Publish step should declare valid artifact')
                        artifact = requireNonNull(context.artifact.id, 'Artifact should have valid id')
                        scm.tag(tag)
                    }
                }
            }
        }
        return artifact
    }

    @Override
    String retag(final String tag, final String previousTag) {
        def artifact = null

        def context = [tag: tag ?: 'latest', previousTag: previousTag]
        execute(context, 'config')

        script.buildNode(context.node) {
            script.buildStep('Build') {
                scm.checkout {
                    execute(context, 'retag')
                    execute(context, 'publish')
                    requireNonNull(context.artifact, 'Publish step should declare valid artifact')
                    artifact = requireNonNull(context.artifact.id, 'Artifact should have valid id')
                    scm.tag(tag)
                }
            }
        }
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
