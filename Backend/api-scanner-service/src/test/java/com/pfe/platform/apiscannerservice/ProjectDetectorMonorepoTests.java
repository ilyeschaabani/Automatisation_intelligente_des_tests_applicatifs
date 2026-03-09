package com.pfe.platform.apiscannerservice;

import com.pfe.platform.apiscannerservice.Model.ProjectDetectionResult;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import com.pfe.platform.apiscannerservice.Service.ProjectDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectDetectorMonorepoTests {

    @TempDir
    Path repoRoot;

    @Test
    void detectsSpringBootInSubdirectory() throws IOException {
        Path backend = repoRoot.resolve("Backend").resolve("api-scanner-service");
        Files.createDirectories(backend);

        Files.writeString(backend.resolve("pom.xml"), "<project></project>", StandardCharsets.UTF_8);

        Path javaFile = backend.resolve("src/main/java/com/example/DemoController.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile,
                "import org.springframework.web.bind.annotation.RestController;\n" +
                        "@RestController class DemoController {}\n",
                StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.SPRING_BOOT, res.getMetadata().getFramework());
        assertEquals(backend.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
        assertTrue(res.getMetadata().getHints().containsKey("projectRoot"));
    }

    @Test
    void detectsExpressInSubdirectory() throws IOException {
        Path server = repoRoot.resolve("server");
        Files.createDirectories(server);

        Files.writeString(server.resolve("package.json"), "{\"dependencies\":{\"express\":\"4.0.0\"}}", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.EXPRESS, res.getMetadata().getFramework());
        assertEquals(server.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsFastApi() throws IOException {
        Path api = repoRoot.resolve("services").resolve("python-api");
        Files.createDirectories(api);
        Files.writeString(api.resolve("requirements.txt"), "fastapi==0.110.0\nuvicorn\n", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.FASTAPI, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsDjango() throws IOException {
        Path api = repoRoot.resolve("services").resolve("django-app");
        Files.createDirectories(api);
        Files.writeString(api.resolve("requirements.txt"), "Django==5.0.0\n", StandardCharsets.UTF_8);
        Files.writeString(api.resolve("manage.py"), "print('ok')", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.DJANGO, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsFlask() throws IOException {
        Path api = repoRoot.resolve("services").resolve("flask-app");
        Files.createDirectories(api);
        Files.writeString(api.resolve("requirements.txt"), "Flask==3.0.0\n", StandardCharsets.UTF_8);
        Files.writeString(api.resolve("app.py"), "from flask import Flask\napp = Flask(__name__)\n", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.FLASK, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsLaravel() throws IOException {
        Path api = repoRoot.resolve("php").resolve("laravel");
        Files.createDirectories(api);
        Files.writeString(api.resolve("composer.json"), "{\"require\":{\"laravel/framework\":\"^10.0\"}}", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.LARAVEL, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsSymfony() throws IOException {
        Path api = repoRoot.resolve("php").resolve("symfony");
        Files.createDirectories(api);
        Files.writeString(api.resolve("composer.json"), "{\"require\":{\"symfony/framework-bundle\":\"^6.0\"}}", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.SYMFONY, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsRails() throws IOException {
        Path api = repoRoot.resolve("ruby").resolve("rails-app");
        Files.createDirectories(api);
        Files.writeString(api.resolve("Gemfile"), "source 'https://rubygems.org'\ngem 'rails'\n", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.RAILS, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsGin() throws IOException {
        Path api = repoRoot.resolve("go").resolve("gin-app");
        Files.createDirectories(api);
        Files.writeString(api.resolve("go.mod"), "module example.com/x\nrequire github.com/gin-gonic/gin v1.9.0\n", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.GIN, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsQuarkusFromPom() throws IOException {
        Path api = repoRoot.resolve("java").resolve("quarkus-app");
        Files.createDirectories(api);
        Files.writeString(api.resolve("pom.xml"), "<project><dependencies><dependency><groupId>io.quarkus</groupId></dependency></dependencies></project>", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.QUARKUS, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsMicronautFromGradle() throws IOException {
        Path api = repoRoot.resolve("java").resolve("micronaut-app");
        Files.createDirectories(api);
        Files.writeString(api.resolve("build.gradle"), "dependencies { implementation(\"io.micronaut:micronaut-http-client\") }", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.MICRONAUT, res.getMetadata().getFramework());
        assertEquals(api.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void prefersSpringBootOverFrontendPackageJsonInMonorepo() throws IOException {
        Path frontend = repoRoot.resolve("frontend");
        Files.createDirectories(frontend);
        Files.writeString(frontend.resolve("package.json"), "{\"dependencies\":{\"react\":\"18.0.0\"}}", StandardCharsets.UTF_8);

        Path backend = repoRoot.resolve("Backend").resolve("api-scanner-service");
        Files.createDirectories(backend);
        Files.writeString(backend.resolve("pom.xml"), "<project></project>", StandardCharsets.UTF_8);
        Path javaFile = backend.resolve("src/main/java/com/example/DemoController.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile,
                "import org.springframework.web.bind.annotation.RestController;\n" +
                        "@RestController class DemoController {}\n",
                StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.SPRING_BOOT, res.getMetadata().getFramework());
        assertEquals(backend.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
    }

    @Test
    void detectsSpringBootFromPomEvenWithoutJavaSources() throws IOException {
        Path mod = repoRoot.resolve("backend").resolve("service");
        Files.createDirectories(mod);
        Files.writeString(mod.resolve("pom.xml"), "<project><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId></parent></project>", StandardCharsets.UTF_8);

        ProjectDetector detector = new ProjectDetector();
        ProjectDetectionResult res = detector.detectProject(repoRoot);

        assertEquals(ProjectMetadata.Framework.SPRING_BOOT, res.getMetadata().getFramework());
        assertEquals(mod.toAbsolutePath().normalize(), res.getProjectRoot().toAbsolutePath().normalize());
        assertEquals("build", res.getMetadata().getHints().get("springDetection"));
    }
}
