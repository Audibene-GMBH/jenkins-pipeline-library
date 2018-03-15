#!groovy

def <V> V call(String name = null, Closure<V> body) {
    if (env.NODE_NAME) {
        body()
    } else {
        name = name?:env.DEFAULT_NODE_NAME?:'master'
        node(name) {
            body()
        }
    }
}