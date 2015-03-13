package xyz.johansson.huffman;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import static xyz.johansson.huffman.HuffmanCode.*;

/**
 * Huffman compression/decompression of arbitrary file.
 *
 * @author Tobias Johansson
 */
public class Main {

    // Error exit statuses
    public static final int ERROR_404 = 1;
    public static final int ERROR_READING = 2;
    public static final int ERROR_WRITING = 3;
    public static final int ERROR_CODING = 4;
    public static final int ERROR_DECODING = 5;

    // Nested class
    static class IntBox {

        int value;

        IntBox() {
        }

        IntBox(int value) {
            this.value = value;
        }
    }

    /**
     * Main method.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.print("1) Compress\n2) Decompress\nx) Exit\nSelect: ");
        try {
            int i = new Scanner(System.in).nextInt();
            if (i >= 1 && i <= 2) {
                System.out.print("Filename: ");
                String filename = new Scanner(System.in).nextLine();
                switch (i) {
                    case 1:
                        compress(filename);
                        break;
                    case 2:
                        decompress(filename);
                        break;
                }
            }
        } catch (InputMismatchException ex) {
            // nothing
        } finally {
            System.out.println("\nGood bye!");
        }
    }

    //--------------------------------------------------------------------------
    // Compression
    /**
     * Compress a file.
     *
     * @param filename file to compress
     */
    public static void compress(String filename) {
        // Read data from file
        byte[] data = readFile(filename);
        char[] unsignedData = new char[data.length];
        for (int i = 0; i < data.length; i++) {
            unsignedData[i] = (char) (data[i] >= 0 ? data[i] : 256 + data[i]);
        }

        // Code the data
        int[] counts = null;
        String[] codes = null;
        IntBox dataBits = new IntBox();
        byte[] compressedData = null;
        try {
            // Count frequency
            counts = getCharacterFrequency(new String(unsignedData));
            // Create a Huffman tree
            Tree tree = getHuffmanTree(counts);
            // Get codes
            if (tree == null) { // if no byte
                codes = new String[256];
            } else { // if one byte
                if (tree.root.left == null && tree.root.right == null) {
                    codes = new String[256];
                    codes[tree.root.element] = "0";
                } else { // if > one byte
                    codes = getCode(tree.root);
                }
            }
            // Compress
            compressedData = code(unsignedData, dataBits, codes);
        } catch (Exception ex) {
            System.out.println("\nError coding");
            System.exit(ERROR_CODING);
        }

        // Map used bytes with their frequency
        Map<Byte, Integer> countsMap = new TreeMap();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] != 0) {
                countsMap.put((byte) i, counts[i]);
            }
        }

        // Print information about the Huffman compression
        if (dataBits.value > 0) {
            System.out.printf("\n%-15s%-15s%-15s%-15s\n",
                    "ASCII Code", "Character", "Frequency", "Code");
            for (int i = 0; i < codes.length; i++) {
                if (counts[i] != 0) {
                    System.out.printf("%-15d%-15s%-15d%-15s\n",
                            i, (char) i + "", counts[i], codes[i]);
                }
            }
        }
        System.out.println("\nUncompressed data\t" + data.length + " bytes");
        System.out.println("Compressed data\t\t" + compressedData.length + " bytes");
        System.out.println("Frequency map size\t" + countsMap.size());

        // Write byte frequency, № compressed data bits and compressed data
        try {
            ObjectOutputStream oos;
            oos = new ObjectOutputStream(new FileOutputStream(filename + ".cmp"));
            oos.writeObject(new Object[]{countsMap, dataBits.value, compressedData});
        } catch (IOException ex) {
            System.out.println("\nError writing to file");
            System.exit(ERROR_WRITING);
        }

        System.out.println("\nCompression successful");
    }

    /**
     * Return coded data.
     *
     * @param unsignedData the data
     * @param dataBits number of data bits
     * @param counts byte frequency
     * @return coded data
     */
    private static byte[] code(char[] unsignedData, IntBox dataBits, String[] codes) {
        // Code the data to a binary String with its length divisible by 8
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < unsignedData.length; i++) {
            sb.append(codes[unsignedData[i]]);
        }
        dataBits.value = sb.length();
        String binaryString = sb.toString();
        for (int i = 0; i < binaryString.length() % 8; i++) {
            binaryString += "0"; // add until full last byte
        }

        // Convert the binary string to a byte array
        byte[] compressedData = new byte[binaryString.length() / 8];
        for (int i = 0; i < compressedData.length; i++) {
            String s = (String) binaryString.subSequence(i * 8, i * 8 + 8);
            compressedData[i] = (byte) Integer.parseInt(s, 2);
        }

        return compressedData;
    }

    //--------------------------------------------------------------------------
    // Decompression
    /**
     * Decompress a file.
     *
     * @param filename file to decompress
     */
    public static void decompress(String filename) {
        // Read compressed data from file
        Map<Byte, Integer> countsMap = null;
        int dataBits = 0;
        byte[] compressedData = null;
        try {
            ObjectInputStream ois;
            ois = new ObjectInputStream(new FileInputStream(filename));
            Object[] o = (Object[]) ois.readObject();
            countsMap = (Map<Byte, Integer>) o[0];
            dataBits = (int) o[1];
            compressedData = (byte[]) o[2];
        } catch (FileNotFoundException ex) {
            System.out.println("\nError 404");
            System.exit(ERROR_404);
        } catch (IOException | ClassNotFoundException ex) {
            System.out.println("\nError reading from file");
            System.exit(ERROR_READING);
        }

        // Get frequencies for used bytes
        int[] counts = new int[256];
        for (int i = 0; i < counts.length; i++) {
            if (countsMap.get((byte) i) != null) {
                counts[i] = countsMap.get((byte) i);
            }
        }

        // Decode the data
        byte[] data = null;
        try {
            // Decompress
            data = decode(compressedData, dataBits, counts);
        } catch (Exception ex) {
            System.out.println("\nError decoding");
            System.exit(ERROR_DECODING);
        }

        // Write decompressed data
        String[] splitFilename = filename.split("\\.");
        StringBuilder sb = new StringBuilder();
        sb.append(splitFilename[0]);
        sb.append("-clone");
        for (int i = 1; i < splitFilename.length; i++) {
            sb.append(".");
            sb.append(splitFilename[i]);
        }
        if (sb.toString().endsWith(".cmp")) {
            sb.setLength(sb.length() - 4);
        }
        writeFile(sb.toString(), data);
        System.out.println("\nDecompression successful");
    }

    /**
     * Return decoded data.
     *
     * @param compressedData the compressed data
     * @param dataBits number of data bits
     * @param counts byte frequency
     * @return decoded data
     */
    private static byte[] decode(byte[] compressedData, int dataBits, int[] counts) {
        Tree tree = getHuffmanTree(counts); // recreate the Huffman tree
        if (tree == null) { // if no byte
            return new byte[0];
        } else {
            byte data[];
            if (tree.root.left == null && tree.root.right == null) { // if one byte
                data = new byte[dataBits];
                for (int i = 0; i < dataBits; i++) {
                    data[i] = (byte) tree.root.element;
                }
            } else { // if > one byte
                // Convert byte array to a binary String
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < compressedData.length; i++) {
                    sb.append(toBinaryString(compressedData[i]));
                }
                sb.setLength(dataBits); // remove unused bits
                String binaryString = sb.toString();

                // Decode the data from the binary String
                ArrayList<Byte> dataList = new ArrayList();
                for (int i = 0; i < binaryString.length(); i++) {
                    Tree.Node node = tree.root;
                    while (true) {
                        char bit = binaryString.charAt(i);
                        if (bit == '0') {
                            node = node.left;
                        }
                        if (bit == '1') {
                            node = node.right;
                        }
                        if (node.left == null && node.right == null) {
                            dataList.add((byte) node.element);
                            break;
                        }
                        i++;
                    }
                }
                data = new byte[dataList.size()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = dataList.get(i);
                }
            }
            return data;
        }
    }

    //--------------------------------------------------------------------------
    // Helper methods
    /**
     * Read from file.
     *
     * @param filename the file
     * @return data from file
     */
    private static byte[] readFile(String filename) {
        byte[] b = null;
        try {
            DataInputStream dis;
            dis = new DataInputStream(new FileInputStream(filename));
            dis.read(b = new byte[dis.available()]);
        } catch (FileNotFoundException ex) {
            System.out.println("\nError 404");
            System.exit(ERROR_404);
        } catch (IOException ex) {
            System.out.println("\nError reading from file");
            System.exit(ERROR_READING);
        }
        return b;
    }

    /**
     * Write to file.
     *
     * @param filename the file
     * @param data the data
     */
    private static void writeFile(String filename, byte[] data) {
        try {
            DataOutputStream dos;
            dos = new DataOutputStream(new FileOutputStream(filename));
            dos.write(data);
        } catch (IOException ex) {
            System.out.println("\nError writing to file");
            System.exit(ERROR_WRITING);
        }
    }

    /**
     * Convert byte to a binary String.
     *
     * @param b the byte
     * @return a binary String representing b
     */
    private static String toBinaryString(byte b) {
        char unsignedB = (char) (b >= 0 ? b : 256 + b);
        StringBuilder sb = new StringBuilder();
        for (int i = 8; i > 0; i--) {
            int mod = (int) Math.pow(2, i);
            int div = (int) Math.pow(2, i - 1);
            sb.append((unsignedB % mod) / div);
        }
        return sb.toString();
    }
}
