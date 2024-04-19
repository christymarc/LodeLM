package activities;
import javax.crypto.*;
import javax.crypto.spec.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.security.spec.KeySpec;

import utils.FileEncryption;

public class Server {
    // private static final int PORT = 12599;
    private static final int PORT = 50709;
    public static final String PROJECTS_DIRECTORY = "projects/";
    private static Map<String, byte[]> userSecretKeys = new HashMap<>();
    private static Map<String, byte[]> testuserSecretKeys = new HashMap<>();
    private static Map<String, Map<String, byte[]>> userPasswords = new HashMap<>();
    private static Map<String, Map<String, byte[]>> testuserPasswords = new HashMap<>();
    private static String userPath = "src/main/java/activities/users.txt";
    private static String testPath = "src/test/java/activities/test_users.txt";
    private static String secretPath = "src/main/java/activities/secret_keys.txt";
    private static String testsecretPath = "src/test/java/activities/test_secret_keys.txt";

    static {
        loadUserPasswords(userPath);
        loadUserPasswords(testPath);
        loadUserSecretKeysFromFile(secretPath);
        loadUserSecretKeysFromFile(testsecretPath);
    }

    public static void main(String[] args) {

        // Create server key to encrypt files if key does not exist
        File serverKeyFile = new File("../file_keys.csv");
        if (!serverKeyFile.exists()) {
            try {
                FileEncryption fe = new FileEncryption();
                SecretKey serverKey = fe.getAESKey();
                fe.saveKey(serverKey, serverKeyFile);
            } catch (Exception e) {
                System.out.println(e);
            }
        }

        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started. Waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket);

                // Handle client in a separate thread
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, byte[]> getUserSecretKeys() {
        return userSecretKeys;
    }

    public static Map<String, byte[]> testGetUserSecretKeys() {
        return testuserSecretKeys;
    }

    public static Map<String, Map<String, byte[]>> getUserPasswords() {
        return userPasswords;
    }

    public static Map<String, Map<String, byte[]>> testGetUserPasswords() {
        return testuserPasswords;
    }

    private static void loadUserPasswords(String filePath) {

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split the line into tokens
                String[] tokens = line.split(" ");
                if (tokens.length == 4) {
                    String uid = tokens[0];
                    byte[] salt = Base64.getDecoder().decode(tokens[1]);
                    byte[] hashedPassword = Base64.getDecoder().decode(tokens[2]);
                    byte[] hashedEmail = Base64.getDecoder().decode(tokens[3]);
                    if (filePath.equals(userPath)) {
                        // Create a nested map to store salt and hashed password
                        Map<String, byte[]> userData = new HashMap<>();
                        userData.put("salt", salt);
                        userData.put("passwordHash", hashedPassword);
                        userData.put("emailHash", hashedEmail);
        
                        // Store the user information in the map
                        userPasswords.put(uid, userData);
                    }
                    else if (filePath.equals(testPath)) {
                        // hash password and email for test
                        byte[] hashedPasswordForTest = hashSalt(new String(Base64.getDecoder().decode(tokens[2]), StandardCharsets.UTF_8), salt);
                        byte[] hashedEmailForTest = hashSalt(new String(Base64.getDecoder().decode(tokens[3]), StandardCharsets.UTF_8), salt);
                        // Create a nested map to store salt and hashed password
                        Map<String, byte[]> testuserData = new HashMap<>();
                        testuserData.put("salt", salt);
                        testuserData.put("passwordHash", hashedPasswordForTest);
                        testuserData.put("emailHash", hashedEmailForTest);
        
                        // Store the user information in the map
                        testuserPasswords.put(uid, testuserData);
                    }
                } else {
                    System.out.println("testData: " + testuserPasswords);
                    System.out.println("Invalid format for user entry: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading user file: " + e.getMessage());
        }
    }

    private static void loadUserSecretKeysFromFile(String filePath) {
        // Load encrypted secret keys from a file
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                String username = parts[0];
                byte[] encryptedSecretKey = Base64.getDecoder().decode(parts[1]);
                if (filePath.equals(testsecretPath)) {
                    testuserSecretKeys.put(username, encryptedSecretKey);
                }
                else {
                    userSecretKeys.put(username, encryptedSecretKey);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    public static byte[] hashSalt(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            digest.update(salt);
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            // iterations
            for (int i = 0; i < 20000; i++) {
                digest.reset();
                hashedBytes = digest.digest(hashedBytes);
            }
            return hashedBytes;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    // private static byte[] concatenateByteArrays(byte[] a, byte[] b) {
    //     byte[] result = new byte[a.length + b.length];
    //     System.arraycopy(a, 0, result, 0, a.length);
    //     System.arraycopy(b, 0, result, a.length, b.length);
    //     return result;
    // }

    // private static byte[] generateRandomBytes(int length) {
    //     byte[] bytes = new byte[length];
    //     new SecureRandom().nextBytes(bytes);
    //     return bytes;
    // }

    public static boolean verifyPassword(byte[] providedPasswordHash, byte[] storedPasswordHash) {
        // Compare the provided password hash with the stored password hash
        return Arrays.equals(providedPasswordHash, storedPasswordHash);
    }

    public static byte[] encryptSecretKey(byte[] secretKey, byte[] passwordHash) {
        try {
            // Ensure the password hash is of appropriate length for AES
            byte[] trimmedPasswordHash = Arrays.copyOf(passwordHash, 16); // 16 bytes for AES-128

            // Derive a secret key from the trimmed password hash
            SecretKeySpec secretKeySpec = new SecretKeySpec(trimmedPasswordHash, "AES");

            // Encrypt the secret key using AES
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            return cipher.doFinal(secretKey);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] decryptSecretKey(byte[] encryptedSecretKey, byte[] passwordHash) {
        try {
            // Ensure the password hash is of appropriate length for AES
            byte[] trimmedPasswordHash = Arrays.copyOf(passwordHash, 16); // 16 bytes for AES-128

            // Derive a secret key from the trimmed password hash
            SecretKeySpec secretKeySpec = new SecretKeySpec(trimmedPasswordHash, "AES");

            // Decrypt the secret key using AES
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            return cipher.doFinal(encryptedSecretKey);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }
}