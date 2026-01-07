package biz.digitalindustry.gradleyard

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

@CompileStatic
abstract class UploadCentralBundleTask extends DefaultTask {
    @Input
    abstract Property<String> getTokenName()

    @Input
    abstract Property<String> getTokenPassword()

    @Input
    abstract Property<String> getBundleName()

    @Input
    abstract Property<String> getUploadUrl()

    @Input
    abstract Property<String> getPublishingType()

    @InputFile
    abstract RegularFileProperty getBundle()

    @TaskAction
    void upload() {
        def archive = bundle.get().asFile
        if (!archive.exists()) {
            throw new GradleException("Bundle file not found at ${archive}. Run centralBundle first.")
        }

        def boundary = "------------------------${UUID.randomUUID().toString().replace('-', '')}"
        def auth = Base64.encoder.encodeToString("${tokenName.get()}:${tokenPassword.get()}".getBytes(StandardCharsets.UTF_8))
        def encodedName = URLEncoder.encode(bundleName.get(), 'UTF-8')
        def encodedPublishingType = URLEncoder.encode(publishingType.get(), 'UTF-8')

        def requestUri = URI.create("${uploadUrl.get()}?publishingType=${encodedPublishingType}&name=${encodedName}")
        def body = buildMultipartBody(boundary, archive)

        def request = HttpRequest.newBuilder(requestUri)
            .header('Authorization', "Basic ${auth}")
            .header('Content-Type', "multipart/form-data; boundary=${boundary}")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw new GradleException("Upload failed (${response.statusCode()}): ${response.body()}")
        }

        logger.lifecycle("Deployment ID: ${response.body().trim()}")
    }

    private static byte[] buildMultipartBody(String boundary, File archive) {
        def lineBreak = "\r\n".getBytes(StandardCharsets.UTF_8)
        def prefix = "--${boundary}".getBytes(StandardCharsets.UTF_8)
        def suffix = "--${boundary}--".getBytes(StandardCharsets.UTF_8)

        def output = new ByteArrayOutputStream()
        output.write(prefix)
        output.write(lineBreak)
        output.write("Content-Disposition: form-data; name=\"bundle\"; filename=\"${archive.name}\"".getBytes(StandardCharsets.UTF_8))
        output.write(lineBreak)
        output.write("Content-Type: application/zip".getBytes(StandardCharsets.UTF_8))
        output.write(lineBreak)
        output.write(lineBreak)
        output.write(Files.readAllBytes(archive.toPath()))
        output.write(lineBreak)
        output.write(suffix)
        output.write(lineBreak)
        return output.toByteArray()
    }
}
