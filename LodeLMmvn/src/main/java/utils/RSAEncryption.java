package utils;

import java.security.*;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.*;
import java.security.spec.X509EncodedKeySpec;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.crypto.spec.OAEPParameterSpec;
import java.security.spec.MGF1ParameterSpec;

import javax.crypto.Cipher;

import java.security.spec.InvalidKeySpecException;
import javax.crypto.BadPaddingException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;

import javax.crypto.spec.PSource;


public class RSAEncryption {
    public static RSAPrivateKey readRSAPrivateKey(String file) throws FileNotFoundException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        FileReader fileReader = new FileReader(file);
        PemReader reader = new PemReader(fileReader);
        PemObject pemObject;
        try {
            pemObject = reader.readPemObject();
        } finally {
            reader.close();
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pemObject.getContent());
        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(keySpec);
        return privateKey;
    }

	public static RSAPublicKey readRSAPublicKey(String file) throws FileNotFoundException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        FileReader fileReader = new FileReader(file);
        PemReader reader = new PemReader(fileReader);
        PemObject pemObject;
        try {
            pemObject = reader.readPemObject();
        } finally {
            reader.close();
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pemObject.getContent());
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(keySpec);
        return publicKey;
    }
    public static byte[] RSAEncrypt(byte[] text, PublicKey key) throws Exception {
		byte[] cipherText = null;

        //get an RSA cipher object and print the provider
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.ENCRYPT_MODE, key, oaepParams);

        //encrypt the plaintext
        cipherText = cipher.doFinal(text);
        return cipherText;
    }

    public static byte[] decryptRSA(byte[] text, RSAPrivateKey privateKey) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        byte[] decryptedText = null;
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
        decryptedText = cipher.doFinal(text);
        return decryptedText;
    }
}