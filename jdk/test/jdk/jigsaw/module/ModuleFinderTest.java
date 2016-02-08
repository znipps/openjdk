/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @modules java.base/jdk.internal.module
 * @build ModuleFinderTest
 * @run testng ModuleFinderTest
 * @summary Basic tests for java.lang.module.ModuleFinder
 */

import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.FindException;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import jdk.internal.module.ModuleInfoWriter;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class ModuleFinderTest {

    private static final Path USER_DIR
        = Paths.get(System.getProperty("user.dir"));


    /**
     * Test ModuleFinder.ofInstalled
     */
    public void testOfInstalled() {
        ModuleFinder finder = ModuleFinder.ofInstalled();

        assertTrue(finder.find("java.se").isPresent());
        assertTrue(finder.find("java.base").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());

        Set<String> names = finder.findAll().stream()
            .map(ModuleReference::descriptor)
            .map(ModuleDescriptor::name)
            .collect(Collectors.toSet());
        assertTrue(names.contains("java.se"));
        assertTrue(names.contains("java.base"));
        assertFalse(names.contains("java.rhubarb"));
    }


    /**
     * Test ModuleFinder.of with zero entries
     */
    public void testOfZeroEntries() {
        ModuleFinder finder = ModuleFinder.of();
        assertTrue(finder.findAll().isEmpty());
        assertFalse(finder.find("java.rhubarb").isPresent());
    }


    /**
     * Test ModuleFinder.of with one directory of modules
     */
    public void testOfOneDirectory() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createExplodedModule(dir.resolve("m1"), "m1");
        createModularJar(dir.resolve("m2.jar"), "m2");

        ModuleFinder finder = ModuleFinder.of(dir);
        assertTrue(finder.findAll().size() == 2);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());
    }


    /**
     * Test ModuleFinder.of with two directories
     */
    public void testOfTwoDirectories() throws Exception {
        Path dir1 = Files.createTempDirectory(USER_DIR, "mods1");
        createExplodedModule(dir1.resolve("m1"), "m1@1.0");
        createModularJar(dir1.resolve("m2.jar"), "m2@1.0");

        Path dir2 = Files.createTempDirectory(USER_DIR, "mods2");
        createExplodedModule(dir2.resolve("m1"), "m1@2.0");
        createModularJar(dir2.resolve("m2.jar"), "m2@2.0");
        createExplodedModule(dir2.resolve("m3"), "m3");
        createModularJar(dir2.resolve("m4.jar"), "m4");

        ModuleFinder finder = ModuleFinder.of(dir1, dir2);
        assertTrue(finder.findAll().size() == 4);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertTrue(finder.find("m3").isPresent());
        assertTrue(finder.find("m4").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());

        // check that m1@1.0 (and not m1@2.0) is found
        ModuleDescriptor m1 = finder.find("m1").get().descriptor();
        assertEquals(m1.version().get().toString(), "1.0");

        // check that m2@1.0 (and not m2@2.0) is found
        ModuleDescriptor m2 = finder.find("m2").get().descriptor();
        assertEquals(m2.version().get().toString(), "1.0");
    }


    /**
     * Test ModuleFinder.of with one JAR file
     */
    public void testOfOneJarFile() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path jar1 = createModularJar(dir.resolve("m1.jar"), "m1");

        ModuleFinder finder = ModuleFinder.of(jar1);
        assertTrue(finder.findAll().size() == 1);
        assertTrue(finder.find("m1").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());
    }


    /**
     * Test ModuleFinder.of with two JAR files
     */
    public void testOfTwoJarFiles() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");

        Path jar1 = createModularJar(dir.resolve("m1.jar"), "m1");
        Path jar2 = createModularJar(dir.resolve("m2.jar"), "m2");

        ModuleFinder finder = ModuleFinder.of(jar1, jar2);
        assertTrue(finder.findAll().size() == 2);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());
    }


    /**
     * Test ModuleFinder.of with many JAR files
     */
    public void testOfManyJarFiles() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");

        Path jar1 = createModularJar(dir.resolve("m1@1.0.jar"), "m1@1.0");
        Path jar2 = createModularJar(dir.resolve("m2@1.0.jar"), "m2");
        Path jar3 = createModularJar(dir.resolve("m1@2.0.jar"), "m1@2.0"); // shadowed
        Path jar4 = createModularJar(dir.resolve("m3@1.0.jar"), "m3");

        ModuleFinder finder = ModuleFinder.of(jar1, jar2, jar3, jar4);
        assertTrue(finder.findAll().size() == 3);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertTrue(finder.find("m3").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());

        // check that m1@1.0 (and not m1@2.0) is found
        ModuleDescriptor m1 = finder.find("m1").get().descriptor();
        assertEquals(m1.version().get().toString(), "1.0");
    }


    /**
     * Test ModuleFinder.of with one exploded module.
     */
    public void testOfOneExplodedModule() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path m1_dir = createExplodedModule(dir.resolve("m1"), "m1");

        ModuleFinder finder = ModuleFinder.of(m1_dir);
        assertTrue(finder.findAll().size() == 1);
        assertTrue(finder.find("m1").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());
    }


    /**
     * Test ModuleFinder.of with two exploded modules.
     */
    public void testOfTwoExplodedModules() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path m1_dir = createExplodedModule(dir.resolve("m1"), "m1");
        Path m2_dir = createExplodedModule(dir.resolve("m2"), "m2");

        ModuleFinder finder = ModuleFinder.of(m1_dir, m2_dir);
        assertTrue(finder.findAll().size() == 2);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());
    }


    /**
     * Test ModuleFinder.of with a mix of module directories and JAR files.
     */
    public void testOfMixDirectoriesAndJars() throws Exception {

        // directory with m1@1.0 and m2@1.0
        Path dir1 = Files.createTempDirectory(USER_DIR, "mods1");
        createExplodedModule(dir1.resolve("m1"), "m1@1.0");
        createModularJar(dir1.resolve("m2.jar"), "m2@1.0");

        // JAR files: m1@2.0, m2@2.0, m3@2.0, m4@2.0
        Path dir2 = Files.createTempDirectory(USER_DIR, "mods2");
        Path jar1 = createModularJar(dir2.resolve("m1.jar"), "m1@2.0");
        Path jar2 = createModularJar(dir2.resolve("m2.jar"), "m2@2.0");
        Path jar3 = createModularJar(dir2.resolve("m3.jar"), "m3@2.0");
        Path jar4 = createModularJar(dir2.resolve("m4.jar"), "m4@2.0");

        // directory with m3@3.0 and m4@3.0
        Path dir3 = Files.createTempDirectory(USER_DIR, "mods3");
        createExplodedModule(dir3.resolve("m3"), "m3@3.0");
        createModularJar(dir3.resolve("m4.jar"), "m4@3.0");

        // JAR files: m5 and m6
        Path dir4 = Files.createTempDirectory(USER_DIR, "mods4");
        Path jar5 = createModularJar(dir4.resolve("m5.jar"), "m5@4.0");
        Path jar6 = createModularJar(dir4.resolve("m6.jar"), "m6@4.0");


        ModuleFinder finder
            = ModuleFinder.of(dir1, jar1, jar2, jar3, jar4, dir3, jar5, jar6);
        assertTrue(finder.findAll().size() == 6);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertTrue(finder.find("m3").isPresent());
        assertTrue(finder.find("m4").isPresent());
        assertTrue(finder.find("m5").isPresent());
        assertTrue(finder.find("m6").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());

        // m1 and m2 should be located in dir1
        ModuleDescriptor m1 = finder.find("m1").get().descriptor();
        assertEquals(m1.version().get().toString(), "1.0");
        ModuleDescriptor m2 = finder.find("m2").get().descriptor();
        assertEquals(m2.version().get().toString(), "1.0");

        // m3 and m4 should be located in JAR files
        ModuleDescriptor m3 = finder.find("m3").get().descriptor();
        assertEquals(m3.version().get().toString(), "2.0");
        ModuleDescriptor m4 = finder.find("m4").get().descriptor();
        assertEquals(m4.version().get().toString(), "2.0");

        // m5 and m6 should be located in JAR files
        ModuleDescriptor m5 = finder.find("m5").get().descriptor();
        assertEquals(m5.version().get().toString(), "4.0");
        ModuleDescriptor m6 = finder.find("m6").get().descriptor();
        assertEquals(m6.version().get().toString(), "4.0");
    }


    /**
     * Test ModuleFinder.of with a mix of module directories and exploded
     * modules.
     */
    public void testOfMixDirectoriesAndExplodedModules() throws Exception {
        // directory with m1@1.0 and m2@1.0
        Path dir1 = Files.createTempDirectory(USER_DIR, "mods1");
        createExplodedModule(dir1.resolve("m1"), "m1@1.0");
        createModularJar(dir1.resolve("m2.jar"), "m2@1.0");

        // exploded modules: m1@2.0, m2@2.0, m3@2.0, m4@2.0
        Path dir2 = Files.createTempDirectory(USER_DIR, "mods2");
        Path m1_dir = createExplodedModule(dir2.resolve("m1"), "m1@2.0");
        Path m2_dir = createExplodedModule(dir2.resolve("m2"), "m2@2.0");
        Path m3_dir = createExplodedModule(dir2.resolve("m3"), "m3@2.0");
        Path m4_dir = createExplodedModule(dir2.resolve("m4"), "m4@2.0");

        ModuleFinder finder = ModuleFinder.of(dir1, m1_dir, m2_dir, m3_dir, m4_dir);
        assertTrue(finder.findAll().size() == 4);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertTrue(finder.find("m3").isPresent());
        assertTrue(finder.find("m4").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());

        // m1 and m2 should be located in dir1
        ModuleDescriptor m1 = finder.find("m1").get().descriptor();
        assertEquals(m1.version().get().toString(), "1.0");
        ModuleDescriptor m2 = finder.find("m2").get().descriptor();
        assertEquals(m2.version().get().toString(), "1.0");

        // m3 and m4 should be located in dir2
        ModuleDescriptor m3 = finder.find("m3").get().descriptor();
        assertEquals(m3.version().get().toString(), "2.0");
        ModuleDescriptor m4 = finder.find("m4").get().descriptor();
        assertEquals(m4.version().get().toString(), "2.0");
    }


    /**
     * Test ModuleFinder.of with file path to a module that does not exist.
     */
    public void testOfWithDoesNotExistEntry() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Files.delete(dir);

        ModuleFinder finder = ModuleFinder.of(dir);
        try {
            finder.find("java.rhubarb");
            assertTrue(false);
        } catch (FindException e) {
            assertTrue(e.getCause() instanceof IOException);
        }

        finder = ModuleFinder.of(dir);
        try {
            finder.findAll();
            assertTrue(false);
        } catch (FindException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
    }


    /**
     * Test ModuleFinder.of with a file path to an unrecognized file type.
     */
    public void testOfWithUnrecognizedEntry() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path mod = Files.createTempFile(dir, "m", "mod");

        ModuleFinder finder = ModuleFinder.of(mod);
        try {
            finder.find("java.rhubarb");
            assertTrue(false);
        } catch (FindException e) {
            // expected
        }

        finder = ModuleFinder.of(mod);
        try {
            finder.findAll();
            assertTrue(false);
        } catch (FindException e) {
            // expected
        }
    }


    /**
     * Test ModuleFinder.of with a directory that contains two
     * versions of the same module
     */
    public void testOfDuplicateModulesInDirectory() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createModularJar(dir.resolve("m1@1.0.jar"), "m1");
        createModularJar(dir.resolve("m1@2.0.jar"), "m1");

        ModuleFinder finder = ModuleFinder.of(dir);
        try {
            finder.find("m1");
            assertTrue(false);
        } catch (FindException expected) { }

        finder = ModuleFinder.of(dir);
        try {
            finder.findAll();
            assertTrue(false);
        } catch (FindException expected) { }
    }


    /**
     * Test ModuleFinder.of with a truncated module-info.class
     */
    public void testOfWithTruncatedModuleInfo() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");

        // create an empty <dir>/rhubarb/module-info.class
        Path subdir = Files.createDirectory(dir.resolve("rhubarb"));
        Files.createFile(subdir.resolve("module-info.class"));

        ModuleFinder finder = ModuleFinder.of(dir);
        try {
            finder.find("rhubarb");
            assertTrue(false);
        } catch (FindException e) {
            assertTrue(e.getCause() instanceof InvalidModuleDescriptorException);
        }

        finder = ModuleFinder.of(dir);
        try {
            finder.findAll();
            assertTrue(false);
        } catch (FindException e) {
            assertTrue(e.getCause() instanceof InvalidModuleDescriptorException);
        }
    }


    /**
     * Test ModuleFinder.concat
     */
    public void testConcat() throws Exception {
        Path dir1 = Files.createTempDirectory(USER_DIR, "mods1");
        createExplodedModule(dir1.resolve("m1"), "m1@1.0");
        createExplodedModule(dir1.resolve("m2"), "m2@1.0");

        Path dir2 = Files.createTempDirectory(USER_DIR, "mods2");
        createExplodedModule(dir2.resolve("m1"), "m1@2.0");
        createExplodedModule(dir2.resolve("m2"), "m2@2.0");
        createExplodedModule(dir2.resolve("m3"), "m3");
        createExplodedModule(dir2.resolve("m4"), "m4");

        ModuleFinder finder1 = ModuleFinder.of(dir1);
        ModuleFinder finder2 = ModuleFinder.of(dir2);

        ModuleFinder finder = ModuleFinder.concat(finder1, finder2);
        assertTrue(finder.findAll().size() == 4);
        assertTrue(finder.find("m1").isPresent());
        assertTrue(finder.find("m2").isPresent());
        assertTrue(finder.find("m3").isPresent());
        assertTrue(finder.find("m4").isPresent());
        assertFalse(finder.find("java.rhubarb").isPresent());

        // check that m1@1.0 (and not m1@2.0) is found
        ModuleDescriptor m1 = finder.find("m1").get().descriptor();
        assertEquals(m1.version().get().toString(), "1.0");

        // check that m2@1.0 (and not m2@2.0) is found
        ModuleDescriptor m2 = finder.find("m2").get().descriptor();
        assertEquals(m2.version().get().toString(), "1.0");
    }


    /**
     * Test ModuleFinder.empty
     */
    public void testEmpty() {
        ModuleFinder finder = ModuleFinder.empty();
        assertTrue(finder.findAll().isEmpty());
        assertFalse(finder.find("java.rhubarb").isPresent());
    }


    /**
     * Test null handling
     */
    public void testNulls() {

        try {
            ModuleFinder.ofInstalled().find(null);
            assertTrue(false);
        } catch (NullPointerException expected) { }

        try {
            ModuleFinder.of().find(null);
            assertTrue(false);
        } catch (NullPointerException expected) { }

        try {
            ModuleFinder.empty().find(null);
            assertTrue(false);
        } catch (NullPointerException expected) { }

        try {
            ModuleFinder.of((Path[])null);
            assertTrue(false);
        } catch (NullPointerException expected) { }

        try {
            ModuleFinder.of((Path)null);
            assertTrue(false);
        } catch (NullPointerException expected) { }

        // concat
        ModuleFinder finder = ModuleFinder.of();
        try {
            ModuleFinder.concat(finder, null);
            assertTrue(false);
        } catch (NullPointerException expected) { }
        try {
            ModuleFinder.concat(null, finder);
            assertTrue(false);
        } catch (NullPointerException expected) { }

    }


    /**
     * Parses a string of the form {@code name[@version]} and returns a
     * ModuleDescriptor with that name and version. The ModuleDescriptor
     * will have a requires on java.base.
     */
    static ModuleDescriptor newModuleDescriptor(String mid) {
        String mn;
        String vs;
        int i = mid.indexOf("@");
        if (i == -1) {
            mn = mid;
            vs = null;
        } else {
            mn = mid.substring(0, i);
            vs = mid.substring(i+1);
        }
        ModuleDescriptor.Builder builder
                = new ModuleDescriptor.Builder(mn).requires("java.base");
        if (vs != null)
            builder.version(vs);
        return builder.build();
    }

    /**
     * Creates an exploded module in the given directory and containing a
     * module descriptor with the given module name/version.
     */
    static Path createExplodedModule(Path dir, String mid) throws Exception {
        ModuleDescriptor descriptor = newModuleDescriptor(mid);
        Files.createDirectories(dir);
        Path mi = dir.resolve("module-info.class");
        try (OutputStream out = Files.newOutputStream(mi)) {
            ModuleInfoWriter.write(descriptor, out);
        }
        return dir;
    }

    /**
     * Creates a JAR file with the given file path and containing a module
     * descriptor with the given module name/version.
     */
    static Path createModularJar(Path file, String mid, String ... entries)
        throws Exception
    {
        ModuleDescriptor descriptor = newModuleDescriptor(mid);
        try (OutputStream out = Files.newOutputStream(file)) {
            try (JarOutputStream jos = new JarOutputStream(out)) {

                JarEntry je = new JarEntry("module-info.class");
                jos.putNextEntry(je);
                ModuleInfoWriter.write(descriptor, jos);
                jos.closeEntry();

                for (String entry : entries) {
                    je = new JarEntry(entry);
                    jos.putNextEntry(je);
                    jos.closeEntry();
                }
            }

        }
        return file;
    }

}

