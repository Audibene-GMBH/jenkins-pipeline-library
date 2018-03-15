package de.audibene.jenkins.pipeline.scm

interface Scm {
    void checkout()
    def <T> T checkout(Closure body)
    void tag(String tag)
    void branch(String branch)
    List<String> headTags()
    List<String> branchTags()
}