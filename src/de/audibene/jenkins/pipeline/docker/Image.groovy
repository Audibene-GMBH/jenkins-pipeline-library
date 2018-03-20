package de.audibene.jenkins.pipeline.docker

import static java.util.Objects.requireNonNull

class Image {

    public static final def HOOK_NAMES = [
            'beforeRun',
            'afterRun',
            'beforeAround',
            'afterAround',
            'beforeInside',
            'afterInside'
    ]

    private final def script
    private final String id
    private final String args
    private final Map<String, ?> hooks
    private final docker

    private Image(script, String id, String args, Map hooks) {
        this.script = script
        this.id = id
        this.args = args
        this.hooks = hooks
        this.docker = script.docker
    }

    static Image create(script, Map config) {
        String args = config.remove('args') ?: ''
        String name = config.remove('name')
        String tag = config.remove('tag')
        String id = config.remove('id') ?: "$name:$tag"

        for (String hook : HOOK_NAMES) {
            config[hook] = config.get(hook, {})
        }
        return new Image(script, id, args, config).validated()
    }

    def args(extra = '') {
        return new Image(script, id, "${args} ${extra}".trim(), new LinkedHashMap(hooks))
    }

    def link(String name, String id) {
        return args("--link $id:$name")
    }

    def link(String name, String id, Closure body) {
        link(name, id).inside(body)
    }

    def with(String name, Image image, Closure body) {
        image.around { String id, _ -> link(name, id, body) }
    }

    def call(Closure body) {
        inside(body)
    }

    def inside(Closure body) {
        hooks.beforeRun()
        docker.image(id).inside(args) {
            hooks.beforeInside()
            body()
            hooks.afterInside()
        }
        hooks.afterRun()
    }

    def around(Closure body) {
        hooks.beforeRun()
        docker.image(id).withRun(args) { container ->
            def id = container.id as String
            hooks.beforeAround(id)
            body(id)
            hooks.afterAround(id)
        }
        hooks.afterRun()
    }

    Image build(path = '.') {
        def image = docker.build(this.id, path)
        def id = image.imageName() as String
        return new Image(script, id, args, hooks)
    }


    Image tag(String tag) {
        String id = docker.image(id).tag(tag) as String
        return new Image(script, id, args, hooks)
    }

    Image push(Registry registry) {
        registry {
            push()
        }
    }

    Image push() {
        def image = docker.image(id)
        image.push()
        def id = image.imageName() as String
        return new Image(script, id, args, hooks)
    }

    Image pull(Registry registry) {
        registry {
            pull()
        }
    }

    Image pull() {
        def image = docker.image(id)
        String id = image.imageName() as String
        image.pull()
        return new Image(script, id, args, hooks)

    }

    Image validated() {
        requireNonNull(id, 'Image.id')
        requireNonNull(args, 'Image.args')
        for (String hook : HOOK_NAMES) {
            requireNonNull(hooks[hook], "Image.hooks.${hook}")
        }
        return this
    }
}

