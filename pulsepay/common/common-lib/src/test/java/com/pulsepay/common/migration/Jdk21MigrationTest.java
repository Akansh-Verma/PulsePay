package com.pulsepay.common.migration;

import net.jqwik.api.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based migration tests verifying JDK 21 compatibility.
 * Validates Requirements 5.1, 5.2
 */
public class Jdk21MigrationTest {

    // Feature: jdk21-migration, verifies Requirements 1.1, 1.2, 1.3, 2.1, 2.2
    @Test
    void parentPomDeclaresJava21Consistently() throws Exception {
        // Locate pulsepay/pom.xml by walking up from the working directory
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path pomPath = null;
        Path candidate = workingDir;
        while (candidate != null) {
            Path p = candidate.resolve("pulsepay/pom.xml");
            if (Files.exists(p)) {
                pomPath = p;
                break;
            }
            candidate = candidate.getParent();
        }
        assertNotNull(pomPath, "Could not locate pulsepay/pom.xml from working directory: " + workingDir);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomPath.toFile());
        doc.getDocumentElement().normalize();

        // Assert <properties> entries
        NodeList propertiesNodes = doc.getElementsByTagName("properties");
        assertTrue(propertiesNodes.getLength() > 0, "<properties> element not found in parent POM");
        Element properties = (Element) propertiesNodes.item(0);

        assertEquals("21", properties.getElementsByTagName("java.version").item(0).getTextContent(),
                "java.version should be 21");
        assertEquals("21", properties.getElementsByTagName("maven.compiler.source").item(0).getTextContent(),
                "maven.compiler.source should be 21");
        assertEquals("21", properties.getElementsByTagName("maven.compiler.target").item(0).getTextContent(),
                "maven.compiler.target should be 21");

        // Assert maven-compiler-plugin <source> and <target>
        NodeList plugins = doc.getElementsByTagName("plugin");
        Element compilerPlugin = null;
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            NodeList artifactIds = plugin.getElementsByTagName("artifactId");
            if (artifactIds.getLength() > 0 && "maven-compiler-plugin".equals(artifactIds.item(0).getTextContent())) {
                compilerPlugin = plugin;
                break;
            }
        }
        assertNotNull(compilerPlugin, "maven-compiler-plugin not found in parent POM");

        NodeList configNodes = compilerPlugin.getElementsByTagName("configuration");
        assertTrue(configNodes.getLength() > 0, "maven-compiler-plugin <configuration> not found");
        Element config = (Element) configNodes.item(0);

        assertEquals("21", config.getElementsByTagName("source").item(0).getTextContent(),
                "maven-compiler-plugin <source> should be 21");
        assertEquals("21", config.getElementsByTagName("target").item(0).getTextContent(),
                "maven-compiler-plugin <target> should be 21");
    }

    // Feature: jdk21-migration, Property 2: No child POM overrides the Java version
    @Property(tries = 100)
    void noChildPomOverridesJavaVersion(@ForAll("childPomPaths") String pomRelativePath) throws Exception {
        // Locate the pulsepay/ directory by walking up from the working directory
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path pulsepayDir = null;
        Path candidate = workingDir;
        while (candidate != null) {
            if (candidate.getFileName() != null && "pulsepay".equals(candidate.getFileName().toString())
                    && Files.exists(candidate.resolve("pom.xml"))) {
                pulsepayDir = candidate;
                break;
            }
            Path p = candidate.resolve("pulsepay");
            if (Files.exists(p) && Files.exists(p.resolve("pom.xml"))) {
                pulsepayDir = p;
                break;
            }
            candidate = candidate.getParent();
        }
        assertNotNull(pulsepayDir, "Could not locate pulsepay/ directory from working directory: " + workingDir);

        Path pomPath = pulsepayDir.resolve(pomRelativePath);
        assertTrue(Files.exists(pomPath), "Child POM not found: " + pomPath);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomPath.toFile());
        doc.getDocumentElement().normalize();

        NodeList propertiesNodes = doc.getElementsByTagName("properties");
        if (propertiesNodes.getLength() == 0) {
            // No <properties> element at all — passes by definition
            return;
        }

        Element properties = (Element) propertiesNodes.item(0);
        assertEquals(0, properties.getElementsByTagName("java.version").getLength(),
                pomRelativePath + " must not override java.version");
        assertEquals(0, properties.getElementsByTagName("maven.compiler.source").getLength(),
                pomRelativePath + " must not override maven.compiler.source");
        assertEquals(0, properties.getElementsByTagName("maven.compiler.target").getLength(),
                pomRelativePath + " must not override maven.compiler.target");
    }

    @Provide
    Arbitrary<String> childPomPaths() {
        return Arbitraries.of(
                "common/common-lib/pom.xml",
                "services/payment-service/pom.xml",
                "services/ledger-service/pom.xml",
                "services/fraud-service/pom.xml",
                "services/notification-service/pom.xml"
        );
    }

}
