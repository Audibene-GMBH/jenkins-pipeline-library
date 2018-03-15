package de.audibene.jenkins.pipeline.builder

interface ArtifactBuilder extends Serializable{

    String build(String tag, boolean verbose)

    String retag(String tag, String previousTag)

}
