package activities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;

import io.grpc.netty.shaded.io.netty.util.TimerTask;
import utils.EncryptedCom;
import utils.FileEncryption;
import utils.FileHandler;
import utils.IdleTimeoutManager;

public class ClientHandler implements Runnable {
    private int AES_KEY_LENGTH = 32;
    private int MAC_KEY_LENGTH = 32;
    private int BUFFER_SIZE = 4096;
    private int wrongPasswordAttempts = 0;

    private SSLSocket clientSocket;
    private IdleTimeoutManager idleTimeoutManager;

    DataInputStream dataInputStream;
    DataOutputStream dataOutputStream;

    public ClientHandler(SSLSocket socket) throws SocketException {
        this.clientSocket = socket;
        // set 5 min time manager
        socket.setSoTimeout(300000);
        this.idleTimeoutManager = new IdleTimeoutManager(null, new FileHandler(null));
    }
  
    public void run() {
        try {
            Timer timer = new Timer();

            dataInputStream = new DataInputStream(clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

            FileEncryption fe = new FileEncryption();

            // Receive AES Key
            byte[] aesKey = new byte[AES_KEY_LENGTH];
            dataInputStream.read(aesKey, 0, AES_KEY_LENGTH);
            SecretKey aesSecretKey = new SecretKeySpec(aesKey, 0, AES_KEY_LENGTH, "AES");
            System.out.println("AES Key Received");

            String username = "";
            IdleTimeoutManager.updateUserActivity(username);

            // Loop until "logged-in" message is received
            String action;
            while ((action = new String(EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream),
                    StandardCharsets.UTF_8)) != null) {
                IdleTimeoutManager.updateUserActivity(username);
                if (action.equalsIgnoreCase("logged-in")) {
                    // User is logged in, break the loop and proceed to handle commands
                    break;
                }
                else if (action.equals("Create Account") || action.equals("3")) {
                    // Receive username from client
                    byte[] usernameByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
                    username = new String(usernameByte, StandardCharsets.UTF_8);

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

                    // Receive encrypted email from client
                    byte[] emailByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
                    String email = new String(emailByte, StandardCharsets.UTF_8);

                    createAccount(username, password, email);
                    String accountCreation = "Account creation successful. Proceeding with connection...";
                    try {
                        EncryptedCom.sendMessage(accountCreation.getBytes(), aesSecretKey, fe, dataOutputStream);
                    } catch (SocketTimeoutException e) {
                        // Handle timeout: log the event
                        Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
                        System.out.println("Connection timed out due to inactivity.");
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

                else if (action.equals("2") || action.equals("Forgot Password")) {
                    // Receive username and new password from client
                    byte[] usernameByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
                    username = new String(usernameByte, StandardCharsets.UTF_8);
                    byte[] newPasswordByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
                    String newPasswordString = new String(newPasswordByte, StandardCharsets.UTF_8);
                    String sub = Base64.getEncoder().encodeToString(newPasswordString.getBytes());
                    
                    int padding = sub.length() % 4;
                    if (padding > 0) {
                        sub += "====".substring(padding);
                    }
                    byte[] newPassword = Base64.getDecoder().decode(sub);
                    // Receive encrypted email from client
                    byte[] emailByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
                    String email = new String(emailByte, StandardCharsets.UTF_8);

                    resetPassword(username, newPassword, email);
                    String accountCreation = "Password reset successful. Proceeding with connection...";
                    try {
                        EncryptedCom.sendMessage(accountCreation.getBytes(), aesSecretKey, fe, dataOutputStream);
                    } catch (SocketTimeoutException e) {
                        // Handle timeout: log the event
                        Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
                        System.out.println("Connection timed out due to inactivity.");
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

                else if (action.equals("4") || action.equals("exit")) {
                    try {
                        // Close the socket
                        clientSocket.close();
                        // return;
                    } catch (SocketTimeoutException e) {
                        // Handle timeout: log the event
                        Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
                        System.out.println("Connection timed out due to inactivity.");
                    } catch (IOException e) {
                        System.out.println("Error closing socket: " + e.getMessage());
                    }
                    return;
                }

                else {
                    // Receive username from client
                    byte[] usernameByte = EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream);
                    username = new String(usernameByte, StandardCharsets.UTF_8);

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

                        try {
                            String authenticationSuccess = "Authentication successful. Proceeding with connection...";
                            EncryptedCom.sendMessage(authenticationSuccess.getBytes(), aesSecretKey, fe,
                                    dataOutputStream);
                        } catch (SocketTimeoutException e) {
                            // Handle timeout: log the event
                            Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
                            System.out.println("Connection timed out due to inactivity.");
                        } catch (Exception e) {
                            System.out.println(e);
                        }

                    } else {
                        try {
                            String authenticationFailure = "Invalid username or password.";
                            EncryptedCom.sendMessage(authenticationFailure.getBytes(), aesSecretKey, fe,
                                    dataOutputStream);
                            wrongPasswordAttempts++;
                            System.out.println("wrongPasswordAttempts: " + wrongPasswordAttempts);
                            if (wrongPasswordAttempts >= 4) {
                                String action_string = wrongPasswordAttempts + " failed password attempts on login";
                                Client.logAuditAction(username, "Admin", action_string,
                                        "audit_log.txt");
                                String failedAttempts = wrongPasswordAttempts + " Failed login attempts";
                                EncryptedCom.sendMessage(failedAttempts.getBytes(), aesSecretKey, fe, dataOutputStream);
                                clientSocket.close();
                                return;
                            }
                            // else {
                            //     Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
                            //     System.out.println("CONNECTION timed out due to inactivity.");
                            //     return;
                            // }

                        } catch (SocketTimeoutException e) {
                            // Handle timeout: log the event
                            System.out.println("SocketTimeoutException occurred: " + e.getMessage());
                            Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
                            System.out.println("Connection timed out due to inactivity.");
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }
                }
            }

            // Handle client requests
            try {
                String output;
                String inputLine;
                while ((inputLine = new String(EncryptedCom.receiveMessage(aesSecretKey, fe, dataInputStream),
                        StandardCharsets.UTF_8)) != null) {

                    System.out.println("Received from client: " + inputLine);

                    IdleTimeoutManager.updateUserActivity(username);

                    // handling send case
                    if (inputLine.startsWith("send ")) {
                        String fileName = inputLine.substring(5);
                        String userOutput = null;

                        // file sent to client_data
                        File fileToSend = new File("client_data/" + fileName);
                        // check to ensure file exists
                        if (fileToSend.exists() && !fileToSend.isDirectory()) {
                            FileHandler fileHandler = new FileHandler("server_data/" + fileName);
                            try {
                                userOutput = fileHandler.receiveFile(dataInputStream, aesSecretKey, true, username);
                            } catch (Exception e) {
                                System.out.println(e);
                            }
                            if (userOutput != null) {
                                output = userOutput;
                            } else {
                                output = fileName + " has been received by server";
                            }
                            EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                        } else {
                            output = fileName + " cannot be sent for some reason. Does this file exist? Is it a file not a directory?";
                            EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                        }
                    }
                    // download case handler
                    else if (inputLine.startsWith("download ")) {
                        String fileName = inputLine.substring(9);

                        File fileToDownload = new File("server_data/" + fileName);
                        // check to see if file is valid
                        if (fileToDownload.exists() && !fileToDownload.isDirectory()) {
                            FileHandler fileHandler = new FileHandler("server_data/" + fileName);
                            output = "File was not downloaded for some reason...";
                            try {
                                output = fileHandler.sendFile(dataOutputStream, aesSecretKey, true, username);
                                System.out.println(output);
                            } catch (Exception e) {
                                System.out.println(e);
                            }
                            EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                        } else {
                            output = "File was not downloaded for some reason...";
                            EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                        }

                    } else if (inputLine.startsWith("delete ")) {
                        String fileName = inputLine.substring(7);
                        File fileToDelete = new File("server_data/" + fileName);
                        // check if file is valid
                        if (!fileToDelete.exists() || fileToDelete.isDirectory()) {
                        } else {
                            // delete file if valid
                            FileHandler fileHandler = new FileHandler("server_data/" + fileName);
                            output = fileHandler.deleteFile(username);
                            EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                        }
                    } 
                    else if (inputLine.equals("pwd")) {
                        FileHandler fileHandler = new FileHandler("server_data/");
                        output = fileHandler.pwd();
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }
                    // list all files on server 
                    else if (inputLine.startsWith("list")) {
                        String folder = inputLine.substring(4).trim();
                        if (folder.length() == 0) {
                            folder = "server_data/";
                        }
                        FileHandler fileHandler = new FileHandler(folder);
                        output = fileHandler.listFiles();
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }
                    // case to share permissions of a file 
                    else if (inputLine.startsWith("share ")) {
                        String permissionUsernameAndFileName = inputLine.substring(6);
                        String[] arrOfStr = permissionUsernameAndFileName.split("\\s+");
                        String permission = null;
                        String sharedUsername = null;
                        String fileName = null;
                        output = "File not shared for some reason...";
                        if (arrOfStr != null && arrOfStr.length > 2) {
                            permission = arrOfStr[0];
                            // check to ensure correct input format
                            if ((permission.length() == 1 && permission.contains("w"))
                                    || (permission.length() == 1 && permission.contains("r"))
                                    || (permission.length() == 2 && Character.toString(permission.charAt(1)).equals("w")
                                            && Character.toString(permission.charAt(0)).equals("r"))) {
                                sharedUsername = arrOfStr[1];
                                if (!sharedUsername.equals(username)) {
                                    // check if user exists
                                    if (Client.UserExists(sharedUsername, "normal")) {
                                        fileName = inputLine
                                                .substring(6 + permission.length() + 1 + sharedUsername.length() + 1);
                                        File fileToShare = new File("server_data/" + fileName);
                                        // check if file is valid
                                        if (fileToShare.exists()) {
                                            FileHandler fileHandler = new FileHandler("server_data/" + fileName);
                                            try {
                                                output = fileHandler.shareFile(username, sharedUsername, permission);
                                            } catch (Exception e) {
                                                System.out.println(e);
                                            }
                                        } else {
                                            output = fileName + " does not exist";
                                        }
                                    } else {
                                        output = "This username is does not exist!";
                                    }
                                } else {
                                    output = "You can not share files with yourself. Nice try.";
                                }
                            } else {
                                output = "Permission to enable for share user must be either \'r\', \'w\', or \'rw\'";
                            }
                        } else {
                            output = "You have not given the arguments required to share a file. \nThe format required is:\n\n share <permission> <share_username> <file_name>";
                            output += "\n\n<permission>: permission to enable for share_user, either \'r\', \'w\', or \'rw\'\n<share_username>: username of user to share file with\n<file_name>: name of file to share";
                        }
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    } else if (inputLine.equals("exit")) {
                        break;
                    } else {
                        output = "No command like that available";
                        EncryptedCom.sendMessage(output.getBytes(), aesSecretKey, fe, dataOutputStream);
                    }

                    IdleTimeoutManager.updateUserActivity(username);
                }
            } catch (SocketTimeoutException e) {
                // Handle timeout: log the event
                System.out.println("SocketTimeoutException occurred: " + e.getMessage());
                Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
                System.out.println("Connection timed out due to inactivity.");
            } catch (Exception e) {
                System.out.println(e);
            }
            clientSocket.close();
        }
        catch (SocketTimeoutException e) {
            // Handle timeout: log the event
            System.out.println("SocketTimeoutException occurred: " + e.getMessage());
            Client.logAuditAction("Client", "Idle", "Idle for 5 min", "audit_log.txt");
            System.out.println("Connection timed out due to inactivity.");
        }
        catch (EOFException | SocketException e) {
            // Client has closed the connection abruptly
            System.out.println("Client connection terminated.");
        } catch (IOException e) {
            // Other IO exceptions
            e.printStackTrace();
        } finally {
            try {
                // Close connections
                dataInputStream.close();
                dataOutputStream.close();
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /***
     * 
     * authenticates username and password credentials being input
     * 
     * String username: the username being checked
     * byte[] providedPassword: the input password being checked
     * 
     */
    private boolean authenticateUser(String username, byte[] providedPassword) {

        // Get the stored user data for the given username
        Map<String, byte[]> userData = Server.getUserPasswords().get(username);
        if (userData == null) {
            return false;
        }

        while (!Client.UserExists(username, "normal")) {
            return false;
        }

        // Get the stored salt and password hash from user data
        byte[] storedSalt = userData.get("salt");
        byte[] storedPasswordHash = userData.get("passwordHash");

        if (storedSalt == null || storedPasswordHash == null) {
            return false;
        }

        // Hash the provided password with the stored salt
        byte[] providedPasswordHash = Server.hashSalt(new String(providedPassword), storedSalt);

        // Convert salt, provided password hash, and stored password hash to Base64 for comparison
        String encodedSalt = Base64.getEncoder().encodeToString(storedSalt);
        String encodedHashedPasswordP = Base64.getEncoder().encodeToString(providedPasswordHash);
        String encodedHashedPasswordS = Base64.getEncoder().encodeToString(storedPasswordHash);

        return Arrays.equals(providedPasswordHash, storedPasswordHash);
    }


    /***
     * 
     * creates new user account
     * 
     * String username: username being used for new account
     * byte[] password: the password being stored for the new user
     * String email: the associated email
     * 
     */
    public static void createAccount(String username, byte[] password, String email) {

        byte[] salt = generateSalt();
        // Hash the password and email with Salt
        byte[] hashedPassword = Server.hashSalt(new String(password, StandardCharsets.UTF_8), salt);
        byte[] hashedEmail = Server.hashSalt(email, salt);
        // populate the userData
        Map<String, byte[]> userData = new HashMap<>();
        userData.put("salt", salt);
        userData.put("passwordHash", hashedPassword);
        userData.put("emailHash", hashedEmail);
        Server.getUserPasswords().put(username, userData);

        byte[] storedPasswordHash = userData.get("passwordHash");

        // Generate a secret key for the new account
        byte[] secretKey = generateSecretKey();
        // Write the username, secret key, salt, and hashed pwd
        writeToSecretKeysFile(username, secretKey);
        writeToUserFile(username, salt, storedPasswordHash, hashedEmail);
    }


    /***
     * 
     * resets password for account recovery purpose
     * 
     * String username: username being updated
     * byte[] resetPassword: the new password
     * String email: the associated email
     * 
     */
    public static void resetPassword(String username, byte[] resetPassword, String email) {

        // Check if the user exists
        if (Server.getUserPasswords().containsKey(username)) {
            // get new salt and hash new password and associated email with it
            byte[] salt = generateSalt();
            byte[] hashedNewPassword = Server.hashSalt(new String(resetPassword, StandardCharsets.UTF_8), salt);
            byte[] hashedEmail = Server.hashSalt(email, salt);
            Map<String, byte[]> userData = Server.getUserPasswords().get(username);

            // Update the user data with the new salt, hashed password, and hashed email
            userData.put("salt", salt);
            userData.put("passwordHash", hashedNewPassword);
            userData.put("emailHash", hashedEmail);
            Server.getUserPasswords().put(username, userData);

            // Write updated data to the user file
            updateUserDataFile(username, userData);
        } else {
            System.out.println("User does not exist.");
        }
    }

    /***
     * 
     * generates random 32 byte secret key
     * 
     */
    private static byte[] generateSecretKey() {
        // Generate new random 32-byte secret-key
        SecureRandom random = new SecureRandom();
        byte[] secretKey = new byte[32];
        random.nextBytes(secretKey);
        return secretKey;
    }

    /***
     * 
     * generates random 32 byte salt
     * 
     */
    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[32];
        random.nextBytes(salt);
        return salt;
    }

    /***
     * 
     * write secret key to to secret_key file
     * 
     * String username: username in question
     * byte[] secretKey: the secretKey being written
     * 
     */
    private static void writeToSecretKeysFile(String username, byte[] secretKey) {
        File file = new File("src/main/java/activities/secret_keys.txt");
        try (FileReader fr = new FileReader(file);
                BufferedReader br = new BufferedReader(fr)) {
            String line;
            StringBuilder fileContent = new StringBuilder();
            boolean found = false;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length >= 2 && parts[0].equals(username)) {
                    // Update the secret key for the existing user
                    found = true;
                    String message = "User already exists. Please log in.";
                    System.out.println(message);
                    break;
                } else {
                    // Keep the line unchanged
                    fileContent.append(line).append("\n");
                }
            }
            if (!found) {
                // Append a new entry for the user if not found
                fileContent.append(username).append(":").append(Base64.getEncoder().encodeToString(secretKey))
                        .append("\n");
            }

            // Write the updated file content back to the file
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(fileContent.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /***
     * 
     * write user information to users.txt file
     * 
     * String username: username being written
     * byte[] salt: salt used for hashing
     * byte[] hashedPassword: hashed password to store
     * byte[] hashedEmail: hashed email to store
     * 
     */
    private static void writeToUserFile(String username, byte[] salt, byte[] hashedPassword, byte[] hashedEmail) {
        String userPath = System.getProperty("user.dir") + "/server_data/users.txt";
        File file = new File (userPath);
        try (FileWriter fw = new FileWriter(file, true);
                BufferedWriter bw = new BufferedWriter(fw)) {
            // encode salt, hashedPwd and hashed email
            String encodedSalt = Base64.getEncoder().encodeToString(salt);
            String encodedHashedPassword = Base64.getEncoder().encodeToString(hashedPassword);
            String encodedHashedEmail = Base64.getEncoder().encodeToString(hashedEmail);
            if (file.length() != 0) {
                bw.newLine();
            }
            // format and write the information
            bw.write(username + " " + encodedSalt + " " + encodedHashedPassword + " " + encodedHashedEmail);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /***
     * 
     * updated user information being written to users.txt
     * 
     * String username: username being written
     * Map<String, byte[]> userData: userData with updated information
     * 
     */
    private static void updateUserDataFile(String username, Map<String, byte[]> userData) {
        String userPath = System.getProperty("user.dir") + "/server_data/users.txt";
        File inputFile = new File(userPath);
        File tempFile = new File(inputFile.getAbsolutePath() + ".tmp");

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            String line;

            // Read each line from the original file
            while ((line = reader.readLine()) != null) {
                // Check if the line contains the username to be updated
                if (line.startsWith(username + " ")) {
                    // Replace user-specific data with updated data
                    String updatedLine = username + " " +
                            Base64.getEncoder().encodeToString(userData.get("salt")) + " " +
                            Base64.getEncoder().encodeToString(userData.get("passwordHash")) + " " +
                            Base64.getEncoder().encodeToString(userData.get("emailHash"));
                    // Write the updated line to the temporary file
                    writer.write(updatedLine + System.lineSeparator());
                } else {
                    // Write all other lines to the temporary file
                    writer.write(line + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Replace the original file with the temporary file
        if (!inputFile.delete()) {
            System.out.println("Could not delete the original file.");
            return;
        }
        if (!tempFile.renameTo(inputFile)) {
            System.out.println("Could not rename the temporary file.");
        }
    }

}