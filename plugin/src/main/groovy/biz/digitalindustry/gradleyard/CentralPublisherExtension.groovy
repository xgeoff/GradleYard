package biz.digitalindustry.gradleyard

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested

@CompileStatic
class CentralPublisherExtension {
    final Property<String> tokenName
    final Property<String> tokenPassword
    final Property<String> bundleName
    final Property<String> uploadUrl
    final Property<String> publishingType

    @Nested
    final org.gradle.api.DomainObjectSet<MavenPublication> publications

    private final Project project
    private final ProviderFactory providers

    CentralPublisherExtension(Project project) {
        this.project = project
        this.providers = project.providers

        def objects = project.objects
        tokenName = objects.property(String)
        tokenPassword = objects.property(String)
        bundleName = objects.property(String)
        uploadUrl = objects.property(String)
        publishingType = objects.property(String)
        publications = objects.domainObjectSet(MavenPublication)

        bundleName.convention(providers.provider { "${project.name}-${project.version}".toString() })
        uploadUrl.convention('https://central.sonatype.com/api/v1/publisher/upload')
        publishingType.convention('USER_MANAGED')

        project.plugins.withType(PublishingPlugin) {
            PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
            publishing.publications.withType(MavenPublication).all { MavenPublication pub ->
                if (pub.name == 'mavenJava') {
                    publications.add(pub)
                }
            }
        }
    }

    void publications(Action<? super org.gradle.api.DomainObjectSet<MavenPublication>> action) {
        action.execute(publications)
    }

    @Internal
    org.gradle.api.DomainObjectSet<MavenPublication> resolvePublications(PublishingExtension publishing) {
        if (!publications.empty) {
            return publications
        }
        return publishing.publications.withType(MavenPublication).matching { MavenPublication pub -> pub.name == 'mavenJava' }
    }
}
