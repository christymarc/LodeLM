package activities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.Base64;
import utils.FileHandler;
import java.security.*;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;
import java.security.spec.InvalidKeySpecException;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import utils.*;
import javax.crypto.SecretKey;

public class Client {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 12345;
    private int BUFFER_SIZE = 4096;

    public static void main(String[] args) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException, Exception, InvalidAlgorithmParameterException {
        try {
            String clientName = "Client";
            String serverName = "Server";

            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            System.out.println("Connected to Server");

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); //server input stream
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in)); //user input stream

            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

            FileEncryption fe = new FileEncryption();
            SecretKey aesKey;
            SecretKey macKey;

            // ISO/IEC 11770-3 Key Transport Mechanism 2

            // Send name
            byte[] clientNameByte = clientName.getBytes();
            dataOutputStream.writeLong(clientNameByte.length);
            dataOutputStream.write(clientNameByte);

            // Send time
            String time = TimeUtil.getTime();
            byte[] timeByte = time.getBytes();
            dataOutputStream.writeLong(timeByte.length);
            dataOutputStream.write(timeByte);

            // Server public RSA key
            String serverPublicKeyFile = "rsa_keys/server_public.pem";
            RSAPublicKey serverPublicKey= RSAEncryption.readRSAPublicKey(serverPublicKeyFile);

            // AES KEY Communication
            aesKey = fe.getAESKey();
            byte[] keyData =  aesKey.getEncoded();

            // Encrypt clientName and AES Key
            // byte[] clientShareKey = OtherUtils.concatenateByteArrays(clientName.getBytes(), keyData);
            byte[] cipherText = RSAEncryption.RSAEncrypt(keyData, serverPublicKey);
            dataOutputStream.write(cipherText);
            dataOutputStream.flush();
            System.out.println("Secret Key Shared");

            macKey = fe.getHmacKey();
            byte[] macKeyData =  macKey.getEncoded();
            //TODO: Encrypt keydata
            dataOutputStream.write(macKeyData);
            dataOutputStream.flush();
            System.out.println("MAC Key Shared");


            // Prompt the user for username
            System.out.print("Enter your username: ");
            String username = userInput.readLine();
            EncryptedCom.sendMessage(username.getBytes(), aesKey, fe, dataOutputStream); // Send username to server

            // Prompt the user for password
            System.out.print("Enter your password: ");
            String password = userInput.readLine();
            // Send encrypt password
            EncryptedCom.sendMessage(password.getBytes(), aesKey, fe, dataOutputStream);

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
            out.println("Client disconnected");
            userInput.close();
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}