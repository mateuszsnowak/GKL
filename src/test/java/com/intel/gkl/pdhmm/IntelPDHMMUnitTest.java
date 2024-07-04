package com.intel.gkl.pdhmm;

import com.intel.gkl.IntelGKLUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.FileInputStream;

import htsjdk.samtools.util.BufferedLineReader;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;

import htsjdk.samtools.SAMUtils;

public class IntelPDHMMUnitTest {
    private static final String[] pdhmmDataFiles = { IntelGKLUtils.pathToTestResource("expected.PDHMM.hmmresults.txt"),
            IntelGKLUtils.pathToTestResource("pdhmm_syn_199_68_51.txt"),
            IntelGKLUtils.pathToTestResource("pdhmm_syn_1412_129_223.txt"),
            IntelGKLUtils.pathToTestResource("pdhmm_syn_990_1_2.txt") };

    private static final double DOUBLE_ASSERTION_DELTA = 0.0001;
    private static final int READ_MAX_LENGTH = 200;
    private static final int HAPLOTYPE_MAX_LENGTH = 500;

    private IntelPDHMM intelPDHMM;

    @DataProvider
    public Object[][] dataWithNullArguments() {
        PDHMMTestData td = new PDHMMTestData(1, HAPLOTYPE_MAX_LENGTH, READ_MAX_LENGTH);

        return new Object[][]{
                {td.copyWithHapBases(null)},
                {td.copyWithHapPdbases(null)},
                {td.copyWithReadBases(null)},
                {td.copyWithReadQual(null)},
                {td.copyWithReadInsQual(null)},
                {td.copyWithReadDelQual(null)},
                {td.copyWithGcp(null)},
                {td.copyWithReadLengths(null)},
                {td.copyWithHapLengths(null)}
        };
    }

    @DataProvider
    public Object[][] dataWithWrongSizeArrays() {
        PDHMMTestData td = new PDHMMTestData(1, HAPLOTYPE_MAX_LENGTH, READ_MAX_LENGTH);

        byte[] wrongSizeHapArray = new byte[td.hapArraySize + 1];
        byte[] wrongSizeReadArray = new byte[td.readArraySize + 1];
        long[] wrongSizeLengthsArray = new long[td.batchSize + 1];

        return new Object[][]{
                {td.copyWithHapBases(wrongSizeHapArray)},
                {td.copyWithHapPdbases(wrongSizeHapArray)},
                {td.copyWithReadBases(wrongSizeReadArray)},
                {td.copyWithReadQual(wrongSizeReadArray)},
                {td.copyWithReadInsQual(wrongSizeReadArray)},
                {td.copyWithReadDelQual(wrongSizeReadArray)},
                {td.copyWithGcp(wrongSizeReadArray)},
                {td.copyWithReadLengths(wrongSizeLengthsArray)},
                {td.copyWithHapLengths(wrongSizeLengthsArray)}
        };
    }

    @DataProvider
    public Object[][] dataWithWrongBatchSizeAndLengths() {
        PDHMMTestData td = new PDHMMTestData(1, HAPLOTYPE_MAX_LENGTH, READ_MAX_LENGTH);

        return new Object[][]{
                {td.copyWithBatchSize(0)},
                {td.copyWithBatchSize(-1)},
                {td.copyWithMaxHapLength(0)},
                {td.copyWithMaxHapLength(-1)},
                {td.copyWithMaxReadLength(0)},
                {td.copyWithMaxReadLength(-1)}
        };
    }

    @BeforeMethod
    public void initializePDHMM() {
        final boolean isLoaded = new IntelPDHMM().load(null);
        Assert.assertTrue(isLoaded);
        intelPDHMM = new IntelPDHMM();
    }

    @AfterMethod
    public void closePDHMM() {
        intelPDHMM.done();
    }

    @Test(dataProvider = "dataWithNullArguments", expectedExceptions = {NullPointerException.class})
    public void testComputePDHMM_ThrowsNullPointerException_WhenArgumentIsNull(PDHMMTestData td) {
        intelPDHMM.computePDHMM(
                td.hapBases,
                td.hapPdbases,
                td.readBases,
                td.readQual,
                td.readInsQual,
                td.readDelQual,
                td.gcp,
                td.hapLengths,
                td.readLengths,
                td.batchSize,
                td.maxHapLength,
                td.maxReadLength
        );
    }

    @Test(dataProvider = "dataWithWrongSizeArrays", expectedExceptions = {IllegalArgumentException.class})
    public void testComputePDHMM_ThrowsIllegalArgumentException_WhenArrayHasWrongSize(PDHMMTestData td) {
        intelPDHMM.computePDHMM(
                td.hapBases,
                td.hapPdbases,
                td.readBases,
                td.readQual,
                td.readInsQual,
                td.readDelQual,
                td.gcp,
                td.hapLengths,
                td.readLengths,
                td.batchSize,
                td.maxHapLength,
                td.maxReadLength
        );
    }

    @Test(dataProvider = "dataWithWrongBatchSizeAndLengths", expectedExceptions = {IllegalArgumentException.class})
    public void testComputePDHMM_ThrowsIllegalArgumentException_WhenBatchSizeOrLengthsHaveZeroOrNegativeValue(PDHMMTestData td) {
        intelPDHMM.computePDHMM(
                td.hapBases,
                td.hapPdbases,
                td.readBases,
                td.readQual,
                td.readInsQual,
                td.readDelQual,
                td.gcp,
                td.hapLengths,
                td.readLengths,
                td.batchSize,
                td.maxHapLength,
                td.maxReadLength
        );
    }

    @Test(enabled = true)
    public void pdhmmPerformanceTest() {
        for (String pdhmmData : pdhmmDataFiles) {
            try {
                FileInputStream fis = new FileInputStream(pdhmmData);
                BufferedLineReader br = new BufferedLineReader(fis);
                br.readLine(); // skip first line
                int batchSize = 0;
                int max_read_length = 0, max_hap_length = 0;
                String line;
                while ((line = br.readLine()) != null) {
                    String[] split = line.split("\t"); // Assuming the integers are space-separated
                    byte[] alleleBases = split[0].getBytes(StandardCharsets.UTF_8);
                    byte[] readBases = split[2].getBytes(StandardCharsets.UTF_8);
                    max_hap_length = Math.max(max_hap_length, alleleBases.length);
                    max_read_length = Math.max(max_read_length, readBases.length);
                    batchSize++;
                }
                br.close();

                int hapArraySize = batchSize * max_hap_length;
                int readArraySize = batchSize * max_read_length;

                byte[] alleleBasesFull = new byte[hapArraySize];
                byte[] allelePDBasesFull = new byte[hapArraySize];
                byte[] readBasesFull = new byte[readArraySize];
                byte[] readQualsFull = new byte[readArraySize];
                byte[] readInsQualsFull = new byte[readArraySize];
                byte[] readDelQualsFull = new byte[readArraySize];
                byte[] overallGCPFull = new byte[readArraySize];
                double[] expectedFull = new double[batchSize];
                long[] hapLength = new long[batchSize];
                long[] readLength = new long[batchSize];

                fis.close();
                fis = new FileInputStream(pdhmmData);
                br = new BufferedLineReader(fis);
                br.readLine(); // skip first line

                int currentTestcase = 0;
                while ((line = br.readLine()) != null) {
                    String[] split = line.split("\t"); // Assuming the integers are space-separated
                    byte[] alleleBases = split[0].getBytes(StandardCharsets.UTF_8);
                    byte[] allelePDBases = ArrayUtils.toPrimitive(
                            Arrays.stream(split[1].substring(1, split[1].length() - 1).split(","))
                                    .map(num -> Byte.parseByte(num.trim())).toArray(Byte[]::new));
                    byte[] readBases = split[2].getBytes(StandardCharsets.UTF_8);
                    byte[] readQuals = SAMUtils.fastqToPhred(split[3]);
                    byte[] readInsQuals = SAMUtils.fastqToPhred(split[4]);
                    byte[] readDelQuals = SAMUtils.fastqToPhred(split[5]);
                    byte[] overallGCP = SAMUtils.fastqToPhred(split[6]);
                    double expected = Double.parseDouble(split[7]);

                    // append testcase to full arrays
                    System.arraycopy(alleleBases, 0, alleleBasesFull, currentTestcase * max_hap_length,
                            alleleBases.length);
                    System.arraycopy(allelePDBases, 0, allelePDBasesFull, currentTestcase * max_hap_length,
                            allelePDBases.length);
                    System.arraycopy(readBases, 0, readBasesFull, currentTestcase * max_read_length, readBases.length);
                    System.arraycopy(readQuals, 0, readQualsFull, currentTestcase * max_read_length, readQuals.length);
                    System.arraycopy(readInsQuals, 0, readInsQualsFull, currentTestcase * max_read_length,
                            readInsQuals.length);
                    System.arraycopy(readDelQuals, 0, readDelQualsFull, currentTestcase * max_read_length,
                            readDelQuals.length);
                    System.arraycopy(overallGCP, 0, overallGCPFull, currentTestcase * max_read_length,
                            overallGCP.length);

                    expectedFull[currentTestcase] = expected;
                    hapLength[currentTestcase] = alleleBases.length;
                    readLength[currentTestcase] = readBases.length;
                    currentTestcase++;
                }
                br.close();

                // Call Function
                long start = System.nanoTime();
                double[] actual = intelPDHMM.computePDHMM(alleleBasesFull, allelePDBasesFull,
                        readBasesFull, readQualsFull, readInsQualsFull, readDelQualsFull,
                        overallGCPFull, hapLength,
                        readLength,
                        batchSize, max_hap_length, max_read_length);
                long end = System.nanoTime();
                System.out.printf("Total Elapsed Time = %d ms.%n", TimeUnit.NANOSECONDS.toMillis(end - start));
                // Check Values
                for (int i = 0; i < batchSize; i++) {
                    Assert.assertEquals(actual[i], expectedFull[i], DOUBLE_ASSERTION_DELTA,
                            String.format(
                                    "Mismatching score. Actual: %e, Expected: %e. Computed on testcase number %d",
                                    actual[i],
                                    expectedFull[i], i));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        intelPDHMM.done();
    }

    /**
     * This test repeatedly calls computePDHMM.
     * *
     * <p>
     * To specify the number of iterations (repeatCount), use the following
     * command-line syntax with Gradle:
     * </p>
     *
     * <pre>{@code
     * ./gradlew test -DrepeatCount=<number_of_iterations>
     * }</pre>
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>{@code
     * ./gradlew test -DrepeatCount=5
     * }</pre>
     *
     * <p>
     * If the repeatCount is not provided or is invalid, it defaults to 1.
     * </p>
     */
    @Test(enabled = true)
    public void repeatedTest() {
        String repeatCountProperty = System.getProperty("repeatCount", "1");
        int repeatCount;
        try {
            repeatCount = Integer.parseInt(repeatCountProperty);
        } catch (NumberFormatException e) {
            System.err.println("Invalid format for repeatCount: " + repeatCountProperty + ". Using default value 1.");
            repeatCount = 1;
        }
        final boolean isLoaded = new IntelPDHMM().load(null);

        Assert.assertTrue(isLoaded);
        final IntelPDHMM intelPDHMM = new IntelPDHMM();
        for (int repeat = 0; repeat < repeatCount; repeat++) {
            for (String pdhmmData : pdhmmDataFiles) {
                try {
                    FileInputStream fis = new FileInputStream(pdhmmData);
                    BufferedLineReader br = new BufferedLineReader(fis);
                    br.readLine(); // skip first line
                    int testcase = 0;
                    int max_read_length = 0, max_hap_length = 0;
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] split = line.split("\t"); // Assuming the integers are space-separated
                        byte[] alleleBases = split[0].getBytes(StandardCharsets.UTF_8);
                        byte[] readBases = split[2].getBytes(StandardCharsets.UTF_8);
                        max_hap_length = Math.max(max_hap_length, alleleBases.length);
                        max_read_length = Math.max(max_read_length, readBases.length);
                        testcase++;
                    }
                    br.close();

                    int hapArraySize = testcase * max_hap_length;
                    int readArraySize = testcase * max_read_length;

                    byte[] alleleBasesFull = new byte[hapArraySize];
                    byte[] allelePDBasesFull = new byte[hapArraySize];
                    byte[] readBasesFull = new byte[readArraySize];
                    byte[] readQualsFull = new byte[readArraySize];
                    byte[] readInsQualsFull = new byte[readArraySize];
                    byte[] readDelQualsFull = new byte[readArraySize];
                    byte[] overallGCPFull = new byte[readArraySize];
                    double[] expectedFull = new double[testcase];
                    long[] hapLength = new long[testcase];
                    long[] readLength = new long[testcase];

                    fis.close();
                    fis = new FileInputStream(pdhmmData);
                    br = new BufferedLineReader(fis);
                    br.readLine(); // skip first line

                    int currentTestcase = 0;
                    while ((line = br.readLine()) != null) {
                        String[] split = line.split("\t"); // Assuming the integers are space-separated
                        byte[] alleleBases = split[0].getBytes(StandardCharsets.UTF_8);
                        byte[] allelePDBases = ArrayUtils.toPrimitive(
                                Arrays.stream(split[1].substring(1, split[1].length() - 1).split(","))
                                        .map(num -> Byte.parseByte(num.trim())).toArray(Byte[]::new));
                        byte[] readBases = split[2].getBytes(StandardCharsets.UTF_8);
                        byte[] readQuals = SAMUtils.fastqToPhred(split[3]);
                        byte[] readInsQuals = SAMUtils.fastqToPhred(split[4]);
                        byte[] readDelQuals = SAMUtils.fastqToPhred(split[5]);
                        byte[] overallGCP = SAMUtils.fastqToPhred(split[6]);
                        double expected = Double.parseDouble(split[7]);

                        // append testcase to full arrays
                        System.arraycopy(alleleBases, 0, alleleBasesFull, currentTestcase * max_hap_length,
                                alleleBases.length);
                        System.arraycopy(allelePDBases, 0, allelePDBasesFull, currentTestcase * max_hap_length,
                                allelePDBases.length);
                        System.arraycopy(readBases, 0, readBasesFull, currentTestcase * max_read_length,
                                readBases.length);
                        System.arraycopy(readQuals, 0, readQualsFull, currentTestcase * max_read_length,
                                readQuals.length);
                        System.arraycopy(readInsQuals, 0, readInsQualsFull, currentTestcase * max_read_length,
                                readInsQuals.length);
                        System.arraycopy(readDelQuals, 0, readDelQualsFull, currentTestcase * max_read_length,
                                readDelQuals.length);
                        System.arraycopy(overallGCP, 0, overallGCPFull, currentTestcase * max_read_length,
                                overallGCP.length);

                        expectedFull[currentTestcase] = expected;
                        hapLength[currentTestcase] = alleleBases.length;
                        readLength[currentTestcase] = readBases.length;
                        currentTestcase++;
                    }
                    br.close();

                    // Call Function
                    double[] actual = intelPDHMM.computePDHMM(alleleBasesFull, allelePDBasesFull,
                            readBasesFull, readQualsFull, readInsQualsFull, readDelQualsFull,
                            overallGCPFull, hapLength,
                            readLength,
                            testcase, max_hap_length, max_read_length);
                    // Check Values
                    for (int i = 0; i < testcase; i++) {
                        Assert.assertEquals(actual[i], expectedFull[i], DOUBLE_ASSERTION_DELTA,
                                String.format(
                                        "Mismatching score actual: %e expected: %e computed on testcase number %d",
                                        actual[i],
                                        expectedFull[i], i));

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        intelPDHMM.done();
    }
}
