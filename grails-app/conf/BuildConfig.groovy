grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.6
//grails.project.war.file = "target/${appName}-${appVersion}.war"

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsCentral()
        grailsRepo "http://grails.org/plugins"

        mavenCentral()
        mavenRepo "http://nexus.nmcdsp.org/content/repositories/releases"
        mavenRepo "http://repository.springsource.com/maven/bundles/release"
        mavenRepo "http://repository.springsource.com/maven/bundles/external"
        mavenRepo "http://repository.springsource.com/maven/libraries/release"
        mavenRepo "http://repository.springsource.com/maven/libraries/external"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.

        // runtime 'mysql:mysql-connector-java:5.1.5'
    }

    plugins {
        build(
                ":release:latest.release",
                ":rest-client-builder:latest.release"
        ) {
            // plugin only plugin, should not be transitive to the application
            export = false
        }

        compile(
                ":jquery:1.7.2",
                ":webflow:1.3.8",
                ":ajaxflow:0.2.3",
                ":crypto:2.0"
//                ,
//                ":spring-security-core:1.1.2"
        ) {
            export = false
        }

        compile(":gdt:0.3.0") {
            export = true
        }
    }
}

//grails.plugin.location.'gdt' = '../GDT'