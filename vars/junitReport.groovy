def call(String testResults, Closure body) {
    try {
        body()
    } finally {
        junit allowEmptyResults: true, testResults: testResults
    }
}