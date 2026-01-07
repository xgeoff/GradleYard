package biz.digitalindustry.gradleyard

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.TaskProvider

@CompileStatic
class CentralPublisherPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('maven-publish')

        def extension = project.extensions.create('centralPublisher', CentralPublisherExtension, project)
        def bundleDir = project.layout.buildDirectory.dir('central-bundle-repo')
        def archiveFile = project.layout.buildDirectory.file('central-bundle.zip')

        project.plugins.withType(PublishingPlugin) {
            PublishingExtension publishing = project.extensions.getByType(PublishingExtension)

            configureBundleRepo(publishing, bundleDir)

            TaskProvider<CentralBundleTask> centralBundle = project.tasks.register('centralBundle', CentralBundleTask) { task ->
                task.bundleDir.set(bundleDir)
                task.archiveFile.set(archiveFile)
            }

            project.afterEvaluate {
                def resolvedPublications = extension.resolvePublications(publishing)
                centralBundle.configure {
                    CentralBundleTask t ->
                        t.publicationNames.set(resolvedPublications.collect { MavenPublication pub -> pub.name })
                }

                resolvedPublications.all { MavenPublication publication ->
                    def publishTaskName = "publish${publication.name.capitalize()}PublicationToCentralBundleRepository"
                    centralBundle.configure { task -> task.dependsOn(project.tasks.named(publishTaskName)) }
                }
            }

            project.tasks.register('uploadCentralBundle', UploadCentralBundleTask) { task ->
                task.dependsOn(centralBundle)
                task.bundle.set(archiveFile)
                task.tokenName.convention(extension.tokenName)
                task.tokenPassword.convention(extension.tokenPassword)
                task.bundleName.convention(extension.bundleName)
                task.uploadUrl.convention(extension.uploadUrl)
                task.publishingType.convention(extension.publishingType)
            }
            project.tasks.register('publishCentralBundle') { task ->
                task.group = 'publishing'
                task.description = 'Bundles selected publications and uploads them to Sonatype Central in one step.'
                task.dependsOn('uploadCentralBundle')
            }
        }
    }

    @CompileDynamic
    private static void configureBundleRepo(PublishingExtension publishing, def bundleDir) {
        publishing.repositories.maven { MavenArtifactRepository repo ->
            repo.setName('centralBundle')
            repo.setUrl(bundleDir.get().asFile.toURI())
        }
    }
}
