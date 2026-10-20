package com.stepvolt.app.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ImageMathTest {

    @Test
    public void sampleSizeKeepsShortSideAtLeastTarget() {
        assertEquals(1, ImageMath.sampleSize(600, 800, 512));
        assertEquals(2, ImageMath.sampleSize(1100, 2000, 512));
        assertEquals(4, ImageMath.sampleSize(3000, 4000, 512));   // 3000/4 = 750 >= 512; 3000/8 = 375 < 512
        assertEquals(1, ImageMath.sampleSize(100, 100, 512));
        for (int s : new int[]{700, 1024, 1500, 3024, 4000, 9000}) {
            int ss = ImageMath.sampleSize(s, s * 2, 512);
            assertTrue(s / ss >= 512 || ss == 1);
        }
    }

    @Test
    public void centerSquareIsCentered() {
        int[] a = ImageMath.centerSquare(4000, 3000);
        assertArrayEquals(new int[]{500, 0, 3000}, a);
        int[] b = ImageMath.centerSquare(3000, 4000);
        assertArrayEquals(new int[]{0, 500, 3000}, b);
        assertArrayEquals(new int[]{0, 0, 512}, ImageMath.centerSquare(512, 512));
    }

    @Test
    public void exifOrientationMapsToRotation() {
        assertArrayEquals(new int[]{0, 0}, ImageMath.exifToRotation(1));
        assertArrayEquals(new int[]{90, 0}, ImageMath.exifToRotation(6));
        assertArrayEquals(new int[]{180, 0}, ImageMath.exifToRotation(3));
        assertArrayEquals(new int[]{270, 0}, ImageMath.exifToRotation(8));
        assertArrayEquals(new int[]{0, 1}, ImageMath.exifToRotation(2));
        assertArrayEquals(new int[]{0, 0}, ImageMath.exifToRotation(99));
    }

    @Test
    public void qualityStepsDownAndStops() {
        int q = 88, steps = 0;
        while (q > 0) { q = ImageMath.nextQuality(q); steps++; assertTrue(steps < 10); }
        assertEquals(-1, q);
        assertEquals(78, ImageMath.nextQuality(88));
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i]);
    }
}
