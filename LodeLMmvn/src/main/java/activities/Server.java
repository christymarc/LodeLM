package activities;
import javax.crypto.*;
import javax.crypto.spec.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.security.spec.KeySpec;

public class Server {
    private static final int PORT = 12345;
    public static final String PROJECTS_DIRECTORY = "projects/";
    private static Map<String, byte[]> userSecretKeys = new HashMap<>();
    private static Map<String, byte[]> userPasswords = new HashMap<>();

    static {
        // Load user passwords from a file or database
        loadUserPasswords();
        // Load user secret keys from a file
        loadUserSecretKeysFromFile();
    }

    public static void main(String[] args) {

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

    public static Map<String, byte[]> getUserPasswords() {
        return userPasswords;
    }

    private static void loadUserPasswords() {
        // Load hashed passwords from a file or database
        // TODO: with database make this not hard coded
        userPasswords.put("alice", hashPassword("password123"));
        userPasswords.put("bob", hashPassword("secret456"));
    }

    private static void loadUserSecretKeysFromFile() {
        // Load encrypted secret keys from a file
        try (BufferedReader reader = new BufferedReader(new FileReader("src/main/java/activities/secret_keys.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                String username = parts[0];
                byte[] encryptedSecretKey = Base64.getDecoder().decode(parts[1]);
                userSecretKeys.put(username, encryptedSecretKey);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(password.getBytes());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    // public static byte[] hashPassword(String password) {
    //     try {
    //         // Generate a random salt
    //         byte[] salt = generateRandomBytes(16);
            
    //         // Combine salt and password
    //         byte[] saltedPassword = concatenateByteArrays(salt, password.getBytes());
            
    //         // Hash the salted password
    //         MessageDigest digest = MessageDigest.getInstance("SHA-256");
    //         return digest.digest(saltedPassword);
    //     } catch (NoSuchAlgorithmException e) {
    //         e.printStackTrace();
    //         return null;
    //     }
    // }

    private static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

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
    
    // public void writeToSecretKeysFile(String username, byte[] secretKey) {
    //     try (FileWriter writer = new FileWriter("LodeLMmvn/src/main/java/activities/secret_keys.txt", true)) {
    //         writer.write(username + ":" + Base64.getEncoder().encodeToString(secretKey) + "\n");
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     }
    // }

    
}