# skymek-commons

Shared code of the SkyMek Quarkus services (ms-data, ms-workorder, ms-inventory), published to GitHub Packages as `app.skymek:skymek-commons`.

## Use it

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/ventixs/skymek-commons')
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR') ?: 'token'
            password = findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'app.skymek:skymek-commons:0.1.0'
}
```

Locally, put `gpr.token=<gh auth token>` in `~/.gradle/gradle.properties`.

## Release

Push a tag `vX.Y.Z`; the Publish workflow runs the tests and publishes that version.
