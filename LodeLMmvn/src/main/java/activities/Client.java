package activities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.security.*;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;
import org.apache.commons.lang3.StringUtils;

import utils.*;
import javax.crypto.SecretKey;

public class Client {
    private static final String SERVER_IP = "127.0.0.1";
    // private static final int SERVER_PORT = 12555;
    private static final int SERVER_PORT = 53779;
    private int BUFFER_SIZE = 4096;

    public static void main(String[] args) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException {
        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            System.out.println("Connected to Server");

            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in)); //user input stream
            
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

            FileEncryption fe = new FileEncryption();
            SecretKey aesKey;
            SecretKey macKey;

            // AES KEY Communication
            aesKey = fe.getAESKey();
            byte[] keyData =  aesKey.getEncoded();
            //TODO: Encrypt keydata
            dataOutputStream.write(keyData);
            dataOutputStream.flush();
            System.out.println("Secret Key Shared");

            // macKey = fe.getHmacKey();
            // byte[] macKeyData =  macKey.getEncoded();
            // //TODO: Encrypt keydata
            // dataOutputStream.write(macKeyData);
            // dataOutputStream.flush();
            // System.out.println("MAC Key Shared");

            // LOOP ADDED
            // Loop for login attempts
            // boolean loggedIn = false;
            // while (!loggedIn) {
            // }

            boolean loggedIn = false;
            while (!loggedIn) {
                // Prompt user to choose login method
                System.out.print("Choose an option: 1. Login, 2. Forgot Password, 3. Create Account, 4. Exit\n");
                String login = userInput.readLine();
                if (login.equals("1") || login.equalsIgnoreCase("Login")) {
                    // sending action to server
                    EncryptedCom.sendMessage(login.getBytes(), aesKey, fe, dataOutputStream);
                    // Prompt the user for username
                    System.out.print("Enter your username: ");
                    String username = userInput.readLine();
                    // case for user not existing
                    while (!UserExists(username)) {
                        System.out.print("Username is incorrect or does not exist. Enter another username: ");
                        username = userInput.readLine();
                    }
                    EncryptedCom.sendMessage(username.getBytes(), aesKey, fe, dataOutputStream); // Send username to server

                    // Prompt the user for password
                    System.out.print("Enter your password: ");
                    String password = userInput.readLine();
                    // Send encrypt password
                    EncryptedCom.sendMessage(password.getBytes(), aesKey, fe, dataOutputStream); // Send password to server
                }
                else if (login.equals("2") || login.equalsIgnoreCase("Forgot Password")) {
                    // sending action to server
                    EncryptedCom.sendMessage(login.getBytes(), aesKey, fe, dataOutputStream);

                    String username = "";
                    String email = "";
                    // Get valid email entry
                    while (true) {
                        // Prompt the user for username
                        System.out.print("Enter your username: ");
                        username = userInput.readLine();

                        // case for user not existing
                        while (!UserExists(username)) {
                            System.out.print("Username is incorrect or does not exist. Enter another username: ");
                            username = userInput.readLine();
                        }
                        System.out.print("Enter your email: ");
                        email = userInput.readLine();

                        if (email.isEmpty()) {
                            System.out.println("Email cannot be empty. Please enter valid values.");
                            continue;
                        }

                        // Check if the email is valid
                        if (!SimpleMailSender.isValidEmail(email)) {
                            System.out.println("Invalid email format. Please enter valid values.");
                            continue;
                        }

                        // Check if email and username match
                        if (!UserEmailMatch(username, email)) {
                            System.out.println("Username or Email are incorrect. Please enter a valid username and email.");
                            continue;
                        }

                        // Password re-set email
                        System.out.println("Sending one-time passcode to your email...");
                        String otpVal = SimpleMailSender.generateOTP();
                        String emailSubject = "Password Reset";
                        String emailBody = "Dear " + username + ",\n\n"
                                        + "Your one-time passcode for password reset is: " + otpVal + "\n"
                                        + "Please use this passcode to reset your password.\n\n"
                                        + "Regards,\n"
                                        + "Your LodeLM Team";
                        SimpleMailSender.sendEmail(email, emailSubject, emailBody);

                        System.out.print("Enter your one-time passcode: ");
                        String answer = userInput.readLine();
                        if (answer.equals(otpVal)) {
                            System.out.print("Enter your new password: ");
                            String password = userInput.readLine();
                            // while (!isPasswordStrong(password)) {
                            //     String errorMessage = "Password is not strong enough. Please choose a password with at least 8 characters, containing at least one digit, one uppercase letter, one lowercase letter, and one special character. \n";
                            //     System.out.print(errorMessage);
                            //     System.out.print("Try again please. Enter your password: ");
                            //     password = userInput.readLine();
                            // }
                            System.out.print("Retype your password: ");
                            String password2 = userInput.readLine();
                            while (!password.equals(password2)){
                                System.out.println("Passwords do not match"); 
                                System.out.println("Please try again: "); 
                                password2 = userInput.readLine();
                            }
                            EncryptedCom.sendMessage(username.getBytes(), aesKey, fe, dataOutputStream);
                            EncryptedCom.sendMessage(password.getBytes(), aesKey, fe, dataOutputStream);
                            EncryptedCom.sendMessage(email.getBytes(), aesKey, fe, dataOutputStream);
                            break;
                        } else {
                            // TODO: add reports of inccorect attemps to login AUDIT milestone
                            System.out.println("Incorrect Answer");
                        }
                    }
                }
                else if (login.equals("3") || login.equalsIgnoreCase("Create Account")) {
                    // Send a signal to the server indicating account creation
                    EncryptedCom.sendMessage(login.getBytes(), aesKey, fe, dataOutputStream);
                    // Prompt the user for username
                    System.out.print("Enter your username: ");
                    String username = userInput.readLine();

                    while (UserExists(username)) {
                        System.out.print("Username exists. Enter another username: ");
                        username = userInput.readLine();
                    }
                    if (username.isEmpty()) {
                        System.out.println("Username cannot be empty. Please enter valid values.");
                        continue;
                    }

                    // Prompt the user for password
                    System.out.print("Enter your password: ");
                    String password = userInput.readLine();
                    while (!isPasswordStrong(password)) {
                        String errorMessage = "Password is not strong enough. Please choose a password with at least 8 characters, containing at least one digit, one uppercase letter, one lowercase letter, and one special character.";
                        System.out.print(errorMessage);
                        System.out.print("Try again please. Enter your password: ");
                        password = userInput.readLine();
                    }
                    System.out.print("Enter your password again: ");
                    String password2 = userInput.readLine();
                    while (!password.equals(password2)){
                        System.out.print("Passwords do not match.");
                        System.out.print("Please try again:");
                        password2 = userInput.readLine();
                    }

                    String email = "";
                    // Get valid email entry
                    while (true) {
                        System.out.print("Enter your email: ");
                        email = userInput.readLine();

                        if (email.isEmpty()) {
                            System.out.println("Email cannot be empty. Please enter a valid email.");
                            continue;
                        }

                        // Check if the email is valid
                        if (!SimpleMailSender.isValidEmail(email)) {
                            System.out.println("Invalid email format. Please enter a valid email.");
                            continue;
                        }

                        // Verifying email
                        System.out.println("Sending one-time passcode to your email...");
                        String otpVal = SimpleMailSender.generateOTP();
                        String emailSubject = "Email Verification";
                        String emailBody = "Dear " + username + ",\n\n"
                                        + "Your one-time passcode is: " + otpVal + "\n"
                                        + "Please use this passcode to verify your email.\n\n"
                                        + "Regards,\n"
                                        + "Your LodeLM Team";
                        SimpleMailSender.sendEmail(email, emailSubject, emailBody);

                        System.out.print("Enter your one-time passcode: ");
                        String answer = userInput.readLine();
                        if (answer.equals(otpVal)) {
                            System.out.println("Your email has been verified!");
                            break;
                        } else {
                            // TODO: add reports of inccorect attemps to login AUDIT milestone
                            System.out.println("Your email was invalid. Please enter a valid email.");
                        }
                    }

                    // Encrypt the password
                    EncryptedCom.sendMessage(username.getBytes(), aesKey, fe, dataOutputStream);
                    EncryptedCom.sendMessage(password.getBytes(), aesKey, fe, dataOutputStream);
                    EncryptedCom.sendMessage(email.getBytes(), aesKey, fe, dataOutputStream);

                }
                else if (login.equals("4") || login.equalsIgnoreCase("Exit")) {
                    // Send exit command to the server
                    EncryptedCom.sendMessage("exit".getBytes(), aesKey, fe, dataOutputStream);
                    // Close connections
                    userInput.close();
                    socket.close();
                    dataInputStream.close();
                    dataOutputStream.close();
                    // System.exit(0);
                    return;
                }
                else {
                    System.out.println("Not a valid login method");
                    continue;
                    // Close connections
                    // userInput.close();
                    // socket.close();
                    // dataInputStream.close();
                    // dataOutputStream.close();
                    // return;
                }

                // Receive and print the greeting message from the server
                byte[] greetingByte = EncryptedCom.receiveMessage(aesKey, fe, dataInputStream);
                String greeting = new String(greetingByte, StandardCharsets.UTF_8);
                System.out.println(greeting);
                if (!greeting.equals("Invalid username or password.")) {
                    loggedIn = true;
                    // After successful authentication
                    String loggedInMessage = "logged-in";
                    EncryptedCom.sendMessage(loggedInMessage.getBytes(), aesKey, fe, dataOutputStream);
                }
            }

            String userMessage;
            while ((userMessage = userInput.readLine()) != null) {

                EncryptedCom.sendMessage(userMessage.getBytes(), aesKey, fe, dataOutputStream);

                if (userMessage.startsWith("send ")) {
                    String fileName = userMessage.substring(5);
                    FileHandler fileHandler = new FileHandler("client_data/" + fileName);
                    try {
                        fileHandler.sendFile(dataOutputStream, aesKey, false);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

                else if (userMessage.startsWith("download ")) {
                    String fileName = userMessage.substring(9);
                    FileHandler fileHandler = new FileHandler("client_data/" + fileName);
                    try {
                        fileHandler.receiveFile(dataInputStream, aesKey, false);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

                // Exit loop if user types 'exit'
                else if (userMessage.equalsIgnoreCase("exit")) {
                    break;
                }

                // Print server responses
                String response;
                while ((response = new String(EncryptedCom.receiveMessage(aesKey, fe, dataInputStream), StandardCharsets.UTF_8)) != null) { 
                    System.out.println(response);

                    // Break out of inner loop to return to waiting for user input
                    break;
                }

            }

            // Close connections
            userInput.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean UserExists(String username) {
        Map<String, byte[]> userData = Server.getUserPasswords().get(username);
        if (userData == null) {
            return false;
        }
        else {
            return true;
        }
    }

    private static boolean UserEmailMatch (String username, String providedEmail) {
        Map<String, byte[]> userData = Server.getUserPasswords().get(username);
        if (userData == null) {
            return false;
        }
        else {
            // Get the stored salt and password email
            byte[] storedSalt = userData.get("salt");
            byte[] storedEmailHash = userData.get("emailHash");
            if (storedSalt == null || storedEmailHash == null) {
                return false;
            }
            // Hash the provided email
            byte[] providedEmailHash = Server.hashSalt(new String(providedEmail), storedSalt);
            return Arrays.equals(providedEmailHash, storedEmailHash);
        }
    }

    private static boolean isPasswordStrong(String password) {
        if (StringUtils.isBlank(password) || password.length() < 8) {
            return false;
        }
    
        // Count special characters
        int specialCharCount = 0;
        for (char ch : password.toCharArray()) {
            if (!Character.isLetterOrDigit(ch)) {
                specialCharCount++;
            }
        }
    
        // Check if password contains at least one digit, one uppercase, and one lowercase character
        boolean containsDigit = StringUtils.containsAny(password, "1234567890");
        boolean containsUppercase = StringUtils.containsAny(password, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        boolean containsLowercase = StringUtils.containsAny(password, "abcdefghijklmnopqrstuvwxyz");
    
        return containsDigit && containsUppercase && containsLowercase && specialCharCount >= 1;
    }

}