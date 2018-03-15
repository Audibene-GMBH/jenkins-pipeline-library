package de.audibene.jenkins.pipeline

interface Milestones {
    int BUILD = 100_000
    int DEPLOY = 200_000
    int PROMOTE = 300_000
}