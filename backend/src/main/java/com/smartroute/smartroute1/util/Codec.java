package com.smartroute.smartroute1.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Codec {
    // Helper method to encode double[] as byte[]
    public static byte[] encodeDoubleArray(double[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length * Double.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (double v : data) {
            buffer.putDouble(v);
        }
        return buffer.array();
    }

    // Helper method to decode byte[] as double[]
    public static double[] decodeDoubleArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN);
        double[] data = new double[bytes.length / Double.BYTES];
        for (int i = 0; i < data.length; i++) {
            data[i] = buffer.getDouble();
        }
        return data;
    }

    // Helper method to create a List<Float> from double[]
    public static List<Float> toFloatList(double[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (double v : array) {
            list.add((float) v);
        }
        return list;
    }
}
