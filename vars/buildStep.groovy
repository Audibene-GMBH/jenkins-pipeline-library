#!groovy

def call(String name, boolean visible = true, Closure body) {
    if (!visible || env.STAGE_NAME) {
        body()
    } else {
        stage(name, body)
    }
}