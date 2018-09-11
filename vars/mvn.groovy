def call(cmd, config = 'nexus.audibene.net') {
    configFileProvider([configFile(fileId: config, variable: 'MAVEN_SETTINGS')]) {
        junitReport('build/test-results/*/*.xml') {
            sh "mvn -B -s \$MAVEN_SETTINGS $cmd"
        }
    }
}