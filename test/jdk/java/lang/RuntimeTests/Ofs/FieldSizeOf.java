/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

/*
 * @test
 * @summary Test for Runtime.fieldSizeOf
 * @library /test/lib
 *
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xint                   FieldSizeOf
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -XX:TieredStopAtLevel=1 FieldSizeOf
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -XX:TieredStopAtLevel=2 FieldSizeOf
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -XX:TieredStopAtLevel=3 FieldSizeOf
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -XX:TieredStopAtLevel=4 FieldSizeOf
 * @run main/othervm -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -XX:-TieredCompilation  FieldSizeOf
 */

import java.lang.reflect.Field;

public class FieldSizeOf {

    public static void main(String ... args) throws Exception {
        testInstanceOffsets();
        testStaticOffsets();
        testNulls();
    }

    private static void testInstanceOffsets() throws Exception {
        testWith(1, Holder.class.getDeclaredField("f_boolean"));
        testWith(1, Holder.class.getDeclaredField("f_byte"));
        testWith(2, Holder.class.getDeclaredField("f_char"));
        testWith(2, Holder.class.getDeclaredField("f_short"));
        testWith(4, Holder.class.getDeclaredField("f_int"));
        testWith(4, Holder.class.getDeclaredField("f_float"));
        testWith(8, Holder.class.getDeclaredField("f_long"));
        testWith(8, Holder.class.getDeclaredField("f_double"));
        testWith(4, Holder.class.getDeclaredField("f_object")); // TODO: Assumes compressed oops
        testWith(4, Holder.class.getDeclaredField("f_array"));  // TODO: Assumes compressed oops
    }

    private static void testStaticOffsets() throws Exception {
        testWith(1, Holder.class.getDeclaredField("s_boolean"));
        testWith(1, Holder.class.getDeclaredField("s_byte"));
        testWith(2, Holder.class.getDeclaredField("s_char"));
        testWith(2, Holder.class.getDeclaredField("s_short"));
        testWith(4, Holder.class.getDeclaredField("s_int"));
        testWith(4, Holder.class.getDeclaredField("s_float"));
        testWith(8, Holder.class.getDeclaredField("s_long"));
        testWith(8, Holder.class.getDeclaredField("s_double"));
        testWith(4, Holder.class.getDeclaredField("s_object")); // TODO: Assumes compressed oops
        testWith(4, Holder.class.getDeclaredField("s_array"));  // TODO: Assumes compressed oops
    }

    private static void testWith(int expected, Field f) {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.fieldSizeOf(f));
        }
    }

    private static void testNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            try {
                Runtime.fieldSizeOf(null);
                RuntimeOfUtil.assertFail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

    public static class Holder {
        static boolean  s_boolean;
        static byte     s_byte;
        static char     s_char;
        static short    s_short;
        static int      s_int;
        static float    s_float;
        static double   s_double;
        static long     s_long;
        static Object   s_object;
        static Object[] s_array;
        boolean  f_boolean;
        byte     f_byte;
        char     f_char;
        short    f_short;
        int      f_int;
        float    f_float;
        double   f_double;
        long     f_long;
        Object   f_object;
        Object[] f_array;
    }

}
