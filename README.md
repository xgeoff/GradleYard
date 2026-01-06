# GradleYard
# Maven Central Publish Plugin Template

Use this template as the reference payload when you spin up a new Codex instance for building a reusable Maven-Central pipeline plugin. The template includes every task, helper, and configuration detail you need to wire into a fresh Gradle project so you can publish *any* Maven publication via Sonatype’s Portal API.

## Quickstart (TL;DR)
- Install Java 17+ and run everything via the wrapper: `GRADLE_USER_HOME=.gradle-home ./gradlew clean centralBundle uploadCentralBundle`.
- Add credentials (Sonatype Central tokens) in `~/.gradle/gradle.properties` or via environment variables; never hard-code in `build.gradle`.
- Ensure your project applies `maven-publish` and defines a `mavenJava` publication (defaults to that).
- After `uploadCentralBundle` succeeds, go to https://central.sonatype.com/publisher to review the deployment ID, verify artifacts, and release.

## Getting Sonatype Central Credentials
1. Sign in at https://central.sonatype.com/ with the same org account that owns your groupId.
2. Navigate to **Tokens** (Publisher Tokens) and create a new token:
   - Name: descriptive (e.g., `ci-gradleyard`).
   - Role: **Publisher**.
   - Copy the **Token Name** and **Token Password** immediately; you won’t see the password again.
3. Keep both pieces secret; treat them like any other deploy credential.

## Storing Credentials Securely
Preferred: `~/.gradle/gradle.properties` (user-specific, not committed):
```
sonatypePublisherTokenName=YOUR_TOKEN_NAME
sonatypePublisherTokenPassword=YOUR_TOKEN_PASSWORD
```
Environment variable alternative (e.g., for CI):
```
export SONATYPE_PUBLISHER_TOKEN_NAME=YOUR_TOKEN_NAME
export SONATYPE_PUBLISHER_TOKEN_PASSWORD=YOUR_TOKEN_PASSWORD
```
Then map them in `gradle.properties` or `build.gradle` using `System.getenv(...)`.
Never commit secrets to VCS. `.gradle-home` and `.gradle` are ignored; keep credentials out of project files.

## Project Layout
```
publishing-plugin/
├── README.md                         # This file
├── gradle/                           # wrapper (optional)
├── build.gradle                      # applies the plugin
├── settings.gradle
└── src/
    └── main/groovy/
        └── biz/digitalindustry/publish/
            ├── CentralPublisherPlugin.groovy
            ├── CentralPublisherExtension.groovy
            └── CentralBundleTask.groovy
```

## Plugin Responsibilities
1. Provide an extension `centralPublisher` with:
    * `tokenName` / `tokenPassword`
    * `bundleName` (defaults to `project.name` + `version`)
    * `uploadUrl` (default `https://central.sonatype.com/api/v1/publisher/upload`)
    * `publishingType` (`USER_MANAGED` default)
    * `publications` collection to publish (default to `project.publishing.publications.matching { it.name == 'mavenJava' }`)
2. Add tasks:
    * `centralBundle`: stages selected publications into `build/central-bundle-repo` and zips them.
    * `uploadCentralBundle`: POSTs the resulting zip to Sonatype using `HttpClient`, adds bundle/query params, and reports the returned deployment ID.

## How to Use the GradleYard Plugin (step-by-step)
1. Apply plugin and configure publishing (example `build.gradle`):
   ```groovy
   plugins {
       id 'java'
       id 'maven-publish'
       id 'biz.digitalindustry.publish.gradleyard'
   }

   group = 'com.example'
   version = '1.0.0'

   publishing {
       publications {
           mavenJava(MavenPublication) {
               from components.java
           }
       }
   }

   centralPublisher {
       tokenName = findProperty('sonatypePublisherTokenName') ?: System.getenv('SONATYPE_PUBLISHER_TOKEN_NAME')
       tokenPassword = findProperty('sonatypePublisherTokenPassword') ?: System.getenv('SONATYPE_PUBLISHER_TOKEN_PASSWORD')
       // bundleName, uploadUrl, publishingType left as defaults unless you need to override
   }
   ```
2. Prepare credentials (see “Storing Credentials Securely” above).
3. Build and stage the bundle locally:
   - `GRADLE_USER_HOME=.gradle-home ./gradlew clean centralBundle`
   - What happens: Gradle publishes selected Maven publications to a local repo `build/central-bundle-repo` and zips it to `build/central-bundle.zip`.
4. Upload to Sonatype Central:
   - `GRADLE_USER_HOME=.gradle-home ./gradlew uploadCentralBundle`
   - Output will include `Deployment ID: <id>` on success.
   - Or run everything in one step: `GRADLE_USER_HOME=.gradle-home ./gradlew publishCentralBundle` (runs bundle + upload).
5. Validate and release in Sonatype UI:
   - Go to https://central.sonatype.com/publisher
   - Open **Deployments** (or **Review**), locate the deployment by the ID from the upload step.
   - Inspect components; ensure signatures, POM metadata, and checksums look correct.
   - Click **Release** (or the publish/confirm action) to promote the bundle to Maven Central.

### Task Ordering and What They Do
- `clean` (optional but recommended for a fresh bundle)
- `centralBundle`: runs `publish<Pub>PublicationToCentralBundleRepository` for each chosen publication, then zips the repo.
- `uploadCentralBundle`: depends on `centralBundle`, performs HTTPS multipart upload using the configured token, logs deployment ID.

### Selecting Publications
- Default: only `mavenJava` if present.
- To publish more/other publications:
  ```groovy
  publishing {
    publications {
      mavenJava(MavenPublication) { from components.java }
      docs(MavenPublication) { artifact tasks.javadocJar }
    }
  }
  centralPublisher {
    publications { withType(MavenPublication).matching { it.name in ['mavenJava', 'docs'] } }
  }
  ```

### Changing the Bundle Name or Target URL
```groovy
centralPublisher {
  bundleName = "my-artifact-${version}"
  uploadUrl = "https://central.sonatype.com/api/v1/publisher/upload" // default
  publishingType = "USER_MANAGED" // default; keep unless Sonatype directs otherwise
}
```

### Coordinates, Names, and How They Appear in Sonatype
- **Group (namespace):** Set via `group = 'com.example'` in Gradle. This is the leftmost coordinate (`com.example`); in Sonatype Central it shows as the groupId. Must match what your org owns.
- **ArtifactId:** Defaults to the project name, but set it explicitly on the publication to control what appears in Central:
  ```groovy
  publishing {
    publications {
      mavenJava(MavenPublication) {
        from components.java
        artifactId = 'my-artifact'   // what you’ll see as artifactId in Central
      }
    }
  }
  ```
- **Version:** Set `version = '1.2.3'` in Gradle. This shows as the version in Central. Follow your versioning policy (SemVer recommended).
- **Plugin id (this plugin):** Always `biz.digitalindustry.publish.gradleyard`. This is only for applying the plugin; it does *not* appear in the uploaded artifacts.
- **Bundle name:** Defaults to `${project.name}-${project.version}`; this is used as the bundle label in the upload request and is visible in the Central “Deployments” view next to the deployment ID.
- **What Sonatype shows after upload:** Under Deployments/Review you’ll see:
  - Deployment ID (logged by `uploadCentralBundle`)
  - GroupId/ArtifactId/Version for each module inside the zip
  - Checksums and signatures for each artifact
  - POM metadata (name, description, SCM, licenses, etc.)—make sure your `pom` block sets these as usual in your publication configuration.

### CI Hints
- Set `SONATYPE_PUBLISHER_TOKEN_NAME` / `SONATYPE_PUBLISHER_TOKEN_PASSWORD` as secret env vars in your CI.
- Run with `--no-daemon` in ephemeral runners and set `GRADLE_USER_HOME` to a cacheable path if desired.
- Artifacts are produced in `build/central-bundle.zip`; stash if you need to inspect before upload.

## Sample Task Logic (Groovy pseudo-code)
```groovy
class CentralBundleTask extends DefaultTask {
    @OutputDirectory
    DirectoryProperty bundleDir = project.objects.directoryProperty()

    @InputFiles
    ConfigurableFileCollection publicationsFiles

    @OutputFile
    RegularFileProperty archiveFile = project.objects.fileProperty()

    @TaskAction
    void bundle() {
        bundleDir.get().asFile.deleteDir()
        publicationsFiles.each { File pubDir -> copy { from pubDir; into bundleDir } }
        ant.zip(destfile: archiveFile.get().asFile) { fileset(dir: bundleDir) }
    }
}
```

The real task should use the `publish<Publication>PublicationTo<Repo>` tasks instead of copying files manually, but this snippet shows the idea.

## Upload Task Sketch
```groovy
class UploadCentralBundleTask extends DefaultTask {
    @Input
    String tokenName
    @Input
    String tokenPassword
    @Input
    String uploadUrl
    @InputFile
    RegularFileProperty bundle

    @TaskAction
    void upload() {
        def auth = Base64.encoder.encodeToString("$tokenName:$tokenPassword".bytes)
        def request = HttpRequest.newBuilder()
            .uri(new URI("$uploadUrl?publishingType=$publishingType&name=$bundleName"))
            .header('Authorization', "Bearer $auth")
            .header('Content-Type', "multipart/form-data; boundary=...")
            .POST(...)
            .build()
        def response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString())
        if (response.statusCode() >= 400) throw new GradleException("Upload failed: ${response.body()}")
        logger.lifecycle("Deployment ID: ${response.body().trim()}")
    }
}
```

## Integration Notes
- Apply the plugin to any project with `maven-publish`/`signing`.
- Configure the extension in `build.gradle`:
  ```groovy
  centralPublisher {
      tokenName = findProperty('sonatypePublisherTokenName')
      tokenPassword = findProperty('sonatypePublisherTokenPassword')
      bundleName = "my-artifact-${version}"
  }
  ```
- Declare dependencies: none beyond the JDK.
- Tests can mirror our `HelperLoaderSpec` pattern but focus on ensuring the bundle zips targeted publications and the upload task constructs the correct HTTP request (mock `HttpClient` via Spock).

## Deliverables for Codex
When you hand this to another Codex instance, include:
1. This README (for context).
2. Real Groovy classes implementing `CentralPublisherPlugin`, `CentralBundleTask`, and `UploadCentralBundleTask`.
3. The extension class with all configurable properties.
4. A sample `build.gradle` showing how to apply and configure the plugin.
5. Sample integration tests that confirm the plugin publishes only the `grimoire`/`mavenJava` publication and uploads a bundle with a deployment ID.

With those pieces, the other Codex agent can generate a reusable plugin that matches our current release workflow. Let me know if you’d like me to expand this template into actual source files here before handing it off.	
