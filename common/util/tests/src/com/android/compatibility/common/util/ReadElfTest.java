/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.compatibility.common.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests if {@link ReadElf} parses Executable and Linkable Format files properly.
 *
 * <p>These tests validate content, parsed by {@link ReadElf} is the same with the golden sample
 * files. The golden sample files are the outputs from Linux cmd: readelf -a elf-file
 *
 * <p>
 */
@RunWith(JUnit4.class)
public class ReadElfTest {
    private static final String THIS_CLASS = "com.android.compatibility.common.util.ReadElfTest";
    private static final String TEST_SO_ARM32B = "arm32_libdl.so";
    private static final String TEST_SO_ARM32B_READELF = "arm32_libdl.txt";
    private static final String TEST_SO_ARM64B = "arm64_libdl.so";
    private static final String TEST_SO_ARM64B_READELF = "arm64_libdl.txt";
    private static final String TEST_EXE_X8664B = "x86app_process64";
    private static final String TEST_EXE_X8664B_READELF = "x86app_process64.txt";
    private static final String TEST_EXE_X8632B = "x86app_process32";
    private static final String TEST_EXE_X8632B_READELF = "x86app_process32.txt";

    /**
     * Test {@link ReadElf} for an ARM 32-bit Shared Object
     *
     * @throws Exception
     */
    @Test
    public void testReadElfArm32b() throws Exception {
        checkReadElf(TEST_SO_ARM32B, TEST_SO_ARM32B_READELF, ReadElf.ARCH_ARM, 32, ReadElf.ET_DYN);
    }

    /**
     * Test {@link ReadElf} for an ARM 64-bit Shared Object
     *
     * @throws Exception
     */
    @Test
    public void testReadElfArm64b() throws Exception {
        checkReadElf(TEST_SO_ARM64B, TEST_SO_ARM64B_READELF, ReadElf.ARCH_ARM, 64, ReadElf.ET_DYN);
    }

    /**
     * Test {@link ReadElf} for an x86 32-bit Executable
     *
     * @throws Exception
     */
    @Test
    public void testReadElfX8632b() throws Exception {
        checkReadElf(
                TEST_EXE_X8632B, TEST_EXE_X8632B_READELF, ReadElf.ARCH_X86, 32, ReadElf.ET_DYN);
    }

    /**
     * Test {@link ReadElf} for an x86 64-bit Executable
     *
     * @throws Exception
     */
    @Test
    public void testReadElfX8664b() throws Exception {
        checkReadElf(
                TEST_EXE_X8664B, TEST_EXE_X8664B_READELF, ReadElf.ARCH_X86, 64, ReadElf.ET_DYN);
    }

    /**
     * Compares {@link ReadElf} returns same results with Linux readelf cmd on the same ELF file
     *
     * @param elfFileName the name of an ELF file in Resource to be checked
     * @param elfOutputFileName the name of the golden sample file in Resource
     * @param bits the expected bits of the ELF file
     * @param arch the expected Instruction Set Architecture of the ELF file
     * @param type the expected object file type of the ELF file
     */
    private void checkReadElf(
            String elfFileName, String elfOutputFileName, String arch, int bits, int type)
            throws Exception {
        File targetFile = getResrouceFile(elfFileName);
        assertEquals("ReadElf.isElf() " + elfFileName, true, ReadElf.isElf(targetFile));
        ReadElf elf = ReadElf.read(targetFile);
        assertEquals("getBits() ", bits, elf.getBits());
        assertEquals("getArchitecture() ", arch, elf.getArchitecture());
        assertEquals("isDynamic() ", true, elf.isDynamic());
        assertEquals("getType() ", type, elf.getType());

        File elfOutputFile = getResrouceFile(elfOutputFileName);
        assertEquals("ReadElf.isElf() " + elfOutputFileName, false, ReadElf.isElf(elfOutputFile));

        final Map<String, ReadElf.Symbol> dynSymbolMap = elf.getDynamicSymbols();
        chkDynSymbol(targetFile, dynSymbolMap);

        assertEquals(
                "ReadElf.getDynamicDependencies() " + elfFileName,
                getDynamicDependencies(elfOutputFile),
                elf.getDynamicDependencies());
    }

    /**
     * Gets a list of needed libraries from a Linux readelf cmd output file
     *
     * @param elfOutputFileName a {@link File} object of an golden sample file
     * @return a name list of needed libraries
     */
    private List<String> getDynamicDependencies(File elfOutputFile) throws IOException {
        List<String> result = new ArrayList<>();

        FileReader fileReader = new FileReader(elfOutputFile);
        BufferedReader buffReader = new BufferedReader(fileReader);

        String line;
        boolean keepGoing = true;
        while ((line = buffReader.readLine()) != null && keepGoing) {
            // readelf output as: Dynamic section at offset 0xfdf0 contains 17 entries:
            if (line.startsWith("Dynamic section")) {
                String dsLine;
                while ((dsLine = buffReader.readLine()) != null) {
                    String trimLine = dsLine.trim();
                    if (trimLine.isEmpty()) {
                        // End of the block
                        keepGoing = false;
                        break;
                    }

                    // 0x0000000000000001 (NEEDED)             Shared library: [ld-android.so]
                    if (trimLine.contains("1 (NEEDED)")) {
                        result.add(
                                trimLine.substring(
                                        trimLine.indexOf("[") + 1, trimLine.indexOf("]")));
                    }
                }
            }
        }
        fileReader.close();
        return result;
    }

    /**
     * Checks if all Dynamic Symbols in a golden sample file are also in the map
     *
     * @param targetFile a {@link File} object of an golden sample file
     * @param dynSymbolMap a Dynamic Symbol Map to be validated
     */
    private void chkDynSymbol(File targetFile, Map<String, ReadElf.Symbol> dynSymbolMap)
            throws IOException {
        FileReader fileReader = new FileReader(targetFile);
        BufferedReader buffReader = new BufferedReader(fileReader);

        String line;
        boolean keepGoing = true;
        while ((line = buffReader.readLine()) != null && keepGoing) {
            // readelf output as: Symbol table '.dynsym' contains 44 entries:
            if (line.startsWith("Symbol table")) {
                String dsLine;
                // readelf output as: Num:    Value          Size Type    Bind   Vis      Ndx Name
                //    20: 0000000000000ff8    24 FUNC    WEAK   DEFAULT    9
                // android_init_anonymous_na@@LIBC_PLATFORM
                while ((dsLine = buffReader.readLine()) != null) {
                    String trimLine = dsLine.trim();
                    if (trimLine.isEmpty()) {
                        // End of the block
                        keepGoing = false;
                        break;
                    }
                    // cares about FUNC only
                    if (trimLine.contains("FUNC")) {
                        String phases[] = trimLine.split(" ");
                        String name = phases[phases.length].split("@")[0];
                        ReadElf.Symbol symbol = dynSymbolMap.get(name);
                        assertEquals("chkDynSymbol: name", name, symbol.name);
                        int bind = Integer.parseInt(phases[6]);
                        int shndx = Integer.parseInt(phases[4]);
                        assertEquals("chkDynSymbol: bind", bind, symbol.bind);
                        assertEquals("chkDynSymbol: shndx", shndx, symbol.shndx);
                    }
                }
            }
        }
        fileReader.close();
    }

    /**
     * Reads a file from Resource and write to a tmp file
     *
     * @param fileName the name of a file in Resource
     * @return the File object of a tmp file
     */
    private File getResrouceFile(String fileName) throws IOException {
        File tempFile = File.createTempFile(fileName, "tmp");
        tempFile.deleteOnExit();
        try (InputStream input = openResourceAsStream(fileName);
             OutputStream output = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
        return tempFile;
    }

    /**
     * Gets an InputStrem of a file from Resource
     *
     * @param fileName the name of a file in Resrouce
     * @return the (@link InputStream} object of the file
     */
    private InputStream openResourceAsStream(String fileName) {
        InputStream input = getClass().getResourceAsStream("/" + fileName);
        assertNotNull(input);
        return input;
    }
}