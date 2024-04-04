package utils;

import java.io.*;

public class OtherUtils {
    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(array1);
            outputStream.write(array2);

            byte c[] = outputStream.toByteArray();
            return c;
        }
        catch (Exception e) {
            System.out.println(e);
        }
        return "".getBytes();
    }
}
