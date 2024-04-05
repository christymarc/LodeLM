package activities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import utils.FileHandler;
import java.security.*;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;

import utils.*;
import javax.crypto.SecretKey;

public class Client {
    private static final String SERVER_IP = "127.0.0.1";
    // private static final int SERVER_PORT = 12345;
    private static final int SERVER_PORT = 54393;
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

            macKey = fe.getHmacKey();
            byte[] macKeyData =  macKey.getEncoded();
            //TODO: Encrypt keydata
            dataOutputStream.write(macKeyData);
            dataOutputStream.flush();
            System.out.println("MAC Key Shared");

            // Prompt user to choose login method
            System.out.print("Choose an option: 1. Login, 2. Forgot Password, 3. Create Account \n");
            String login = userInput.readLine();
            if (login.equals("1") || login.equalsIgnoreCase("Login")) {
                // sending action to server
                EncryptedCom.sendMessage(login.getBytes(), aesKey, fe, dataOutputStream);
                // Prompt the user for username
                System.out.print("Enter your username: ");
                String username = userInput.readLine();
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
                // Prompt the user for username
                System.out.print("Enter your username: ");
                //String username = userInput.readLine();
                // Prompt the user for email
                System.out.print("Enter your email: ");
                String email = userInput.readLine();
                // Prompt the user for recovery question
                System.out.print("Recovery question: Who is your favorite teacher? ");
                String answer = userInput.readLine();
                
                // TODO: fix this so that recovery question is checked on server
                String username = "alice";
                String password = "password123";
                EncryptedCom.sendMessage(username.getBytes(), aesKey, fe, dataOutputStream);
                EncryptedCom.sendMessage(password.getBytes(), aesKey, fe, dataOutputStream);

                //out.println(username); // Send username to server
                // TODO: check that this is a valid username and email? pairing and give them the option to reset the password
                // if (email & username is valid) {
                //     reset password
                // }
                // else {
                //     System.out.println("Invalid email or username");
                // }
            }
            else if (login.equals("3") || login.equalsIgnoreCase("Create Account")) {
                // Send a signal to the server indicating account creation
                EncryptedCom.sendMessage(login.getBytes(), aesKey, fe, dataOutputStream);
                // Prompt the user for username
                System.out.print("Enter your username: ");
                String username = userInput.readLine();

                // Prompt the user for password
                System.out.print("Enter your password: ");
                String password = userInput.readLine();

                // // Prompt the user for email
                // System.out.print("Enter your email: ");
                // String email = userInput.readLine();

                // Prompt the user for recovery question
                // System.out.print("Recovery Question: Who is your favorite teacher? ");
                // String teacher = userInput.readLine();

                // Encrypt the password
                EncryptedCom.sendMessage(username.getBytes(), aesKey, fe, dataOutputStream);
                EncryptedCom.sendMessage(password.getBytes(), aesKey, fe, dataOutputStream);

            }
            else {
                System.out.println("Not a valid login method");
                // Close connections
                userInput.close();
                socket.close();
                dataInputStream.close();
                dataOutputStream.close();
            }

            // Receive and print the greeting message from the server
            byte[] greetingByte = EncryptedCom.receiveMessage(aesKey, fe, dataInputStream);
            String greeting = new String(greetingByte, StandardCharsets.UTF_8);
            System.out.println(greeting);

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
}