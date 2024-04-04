package activities;

import java.io.*;
import java.net.*;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.nio.charset.StandardCharsets;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import java.util.Arrays;
import utils.*;
import javax.crypto.SecretKey;

public class ClientHandler implements Runnable {
    private int AES_KEY_LENGTH = 32;
    private int MAC_KEY_LENGTH = 32; // 256 bits to 32 bytes
    private int MAX_BUFFER_SIZE = 4096;

    private String serverName = "Server";

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    DataInputStream dataInputStream;
    DataOutputStream dataOutputStream;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    DatabHandler dbhandler = new DatabHandler();

    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            dataInputStream = new DataInputStream(clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

            FileEncryption fe = new FileEncryption();

            // Receive Name
            long size = dataInputStream.readLong();
            int maxBytesName = (int) Math.min(MAX_BUFFER_SIZE, size);
            byte[] clientNameByte = new byte[maxBytesName];
            dataInputStream.read(clientNameByte, 0, maxBytesName);
            String clientName = new String(clientNameByte, StandardCharsets.UTF_8);
            System.out.println(clientName);

            // Receive Time
            size = dataInputStream.readLong();
            int maxBytesTime = (int) Math.min(MAX_BUFFER_SIZE, size);
            byte[] timeByte = new byte[maxBytesTime];
            dataInputStream.read(timeByte, 0, maxBytesTime);
            String time = new String(timeByte, StandardCharsets.UTF_8);
            System.out.println(time);

            // Receive Name and AES Key
            byte[] cipherText = new byte[maxBytesName + AES_KEY_LENGTH];
            dataInputStream.read(cipherText, 0, maxBytesName + AES_KEY_LENGTH);
            // Decrypt
            String serverPrivateKeyFile = "rsa_keys/server_private.pem";
            byte[] nameKeyShare = cipherText;
            try {
                RSAPrivateKey serverPrivateKey = RSAEncryption.readRSAPrivateKey(serverPrivateKeyFile);
                nameKeyShare = RSAEncryption.decryptRSA(cipherText, serverPrivateKey);
            } catch (Exception e) {
                System.out.println(e);
            }
            byte[] clientNameByte2 = Arrays.copyOfRange(nameKeyShare, 0, maxBytesName);
            String clientName2 = new String(clientNameByte2, StandardCharsets.UTF_8);

            if (!clientName.equals(clientName2)) {
                System.out.println("Untrustworthy Client");
                clientSocket.close();
            }

            byte[] aesKey = Arrays.copyOfRange(nameKeyShare, maxBytesName, maxBytesName + AES_KEY_LENGTH);
            SecretKey aesSecretKey = new SecretKeySpec(aesKey, 0, AES_KEY_LENGTH, "AES");
            System.out.println("AES Key Received");

            // Receive MAC Key
            byte[] macKey = new byte[MAC_KEY_LENGTH];
            dataInputStream.read(macKey, 0, MAC_KEY_LENGTH);
            // macKey = decryptRSA(macKey, rsaKey);
            System.out.println("MAC Key Received");

            // Receive username from client
            // String username = in.readLine();
            byte[] usernameByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
            String username = new String(usernameByte, StandardCharsets.UTF_8);

            // Receive encrypted password from client
            byte[] passwordByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
            String passwordString = new String(passwordByte, StandardCharsets.UTF_8);

            String sub = Base64.getEncoder().encodeToString(passwordString.getBytes());

            // Ensure proper padding by adding '=' characters if necessary
            int padding = sub.length() % 4;
            if (padding > 0) {
                sub += "====".substring(padding);
            }

            byte[] password = Base64.getDecoder().decode(sub);

            // Validate username and password
            if (authenticateUser(username, password)) {
                // If authentication successful, obtain the secret key for the user
                byte[] secretKey = Server.getUserSecretKeys().get(username);

                // Send encrypted secret key and MAC to client
                byte[] encryptedSecretKey = encryptSecretKey(secretKey, password);
                byte[] mac = MACUtils.createMAC(encryptedSecretKey, password);

                //dataOutputStream.write(encryptedSecretKey);
                //dataOutputStream.write(mac);
                try {
                    String authenticationSuccess = "Authentication successful. Proceeding with connection...";
                    EncryptedCom.sendMessage(authenticationSuccess.getBytes(), aesSecretKey, fe, dataOutputStream);
                } catch(Exception e) {
                    System.out.println(e);
                } 
                
            } else {
                try {
                    String authenticationFailure = "Invalid username or password.";
                    EncryptedCom.sendMessage(authenticationFailure.getBytes(), aesSecretKey, fe, dataOutputStream);
                } catch(Exception e) {
                    System.out.println(e);
                } 
                clientSocket.close();
                return;
            }
            
            // Handle client requests
            // TODO: give the users a list of things they can do on the server to prompt them
            try {
                String output;
                String inputLine;
                while ((inputLine = new String(EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream), StandardCharsets.UTF_8)) != null) {

                    System.out.println("Received from client: " + inputLine);

                    // Handle create project command
                    if (inputLine.startsWith("create ")) {
                        String projectName = inputLine.substring(7); // Extract project name
                        // TODO: don't need this with databse -- delete
                    } 
                    // Handle list projects command
                    else if (inputLine.equals("list projects")) {
                        // TODO: don't need this with databse --> delete
                        
                    }
                    else if (inputLine.startsWith("send ")) {
                        String fileName = inputLine.substring(5);
                        FileHandler fileHandler = new FileHandler("server_data/" + fileName);
                        try {
                            fileHandler.receiveFile(dataInputStream, aesSecretKey, true);
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                        output = fileName + " has been received by server";
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);

                        //send to database
                        dbhandler.sendFile("server_data/" + fileName, fileName);
                        // DatabHandler dbhandler = new DatabHandler();
                        // dbHandler.uploadFile("server_data/" + fileName, fileName);
                    }
                    else if (inputLine.startsWith("download ")) {
                        String fileName = inputLine.substring(9);
                        FileHandler fileHandler = new FileHandler("server_data/" + fileName);
                        try {
                            fileHandler.sendFile(dataOutputStream, aesSecretKey, true);
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                        output = "File Downloaded";
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }
                    else if (inputLine.startsWith("delete ")) {
                        String fileName = inputLine.substring(7);
                        FileHandler fileHandler = new FileHandler("server_data/" + fileName);
                        boolean deleted = fileHandler.deleteFile();
                        if (deleted) {
                            output = fileName + " has been deleted.";
                        } else {
                            output = fileName + " has not been deleted...either does not exist or something else went wrong.";
                        }
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }
                    else if (inputLine.equals("pwd")) {
                        FileHandler fileHandler = new FileHandler("server_data/");
                        output = fileHandler.pwd();
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }
                    else if (inputLine.startsWith("list")) {
                        String folder = inputLine.substring(4).trim();
                        if (folder.length() == 0) {
                            folder = "server_data/";
                        }
                        FileHandler fileHandler = new FileHandler(folder);
                        output = fileHandler.listFiles();
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }
                    else if (inputLine.equals("exit")) {
                        break;
                    }
                    else {
                        output = "No command like that available";
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }
                }
            } catch(Exception e) {
                System.out.println(e);
            } 
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 
        finally {
            try {
                // Close connections
                in.close();
                out.close();
                dataInputStream.close();
                dataOutputStream.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean authenticateUser(String username, byte[] providedPassword) {
        // Validate username and password using server's logic
        // return Server.verifyPassword(password, Server.getUserPasswords().get(username));

        // Get the stored password hash for the given username
        byte[] storedPasswordHash = Server.getUserPasswords().get(username);

        if (storedPasswordHash == null) {
            return false; // User not found
        }

        // Hash the provided password
        byte[] providedPasswordHash = Server.hashPassword(new String(providedPassword));

        // Compare the hashed passwords
        return Arrays.equals(providedPasswordHash, storedPasswordHash);
    }

    private byte[] encryptSecretKey(byte[] secretKey, byte[] password) {
        // Implement secret key encryption here
        // For demonstration, just return the secret key
        return secretKey;
    }
}