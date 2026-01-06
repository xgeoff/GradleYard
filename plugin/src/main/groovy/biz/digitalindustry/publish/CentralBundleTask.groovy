package biz.digitalindustry.publish

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CompileStatic
abstract class CentralBundleTask extends DefaultTask {
    CentralBundleTask() {
        // Avoid rerunning if contents unchanged; uses archive file to track output.
        outputs.upToDateWhen { archiveFile.get().asFile.exists() }
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getBundleDir()

    @OutputFile
    abstract RegularFileProperty getArchiveFile()

    @Input
    abstract ListProperty<String> getPublicationNames()

    @CompileDynamic
    @TaskAction
    void bundle() {
        def sourceDir = bundleDir.get().asFile
        if (!sourceDir.exists() || sourceDir.listFiles()?.length == 0) {
            throw new GradleException("No artifacts found in ${sourceDir}. Did you configure publications and run publish tasks?")
        }

        def archive = archiveFile.get().asFile
        archive.parentFile.mkdirs()
        if (archive.exists()) {
            archive.delete()
        }

        ant.zip(destfile: archive) {
            fileset(dir: sourceDir)
        }

        logger.lifecycle("Central bundle created at ${archive} for publications: ${publicationNames.get().join(', ')}")
    }
}
