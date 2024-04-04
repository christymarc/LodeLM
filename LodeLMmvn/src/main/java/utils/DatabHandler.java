// package utils;

// import com.google.firebase.database.*;
// import java.util.Base64;
// import java.io.*;

// public class DatabHandler {
//     private DatabaseReference databaseRef;

//     public DatabHandler() {
//         // Initialize Firebase
//         FirebaseInit.initialize();
//         // Get a reference to the Firebase Realtime Database
//         this.databaseRef = FirebaseDatabase.getInstance().getReference();
//     }

//     public void uploadFile(String filePath, String fileName) {
//         try {
//             // Read the contents of the file
//             File file = new File(filePath);
//             FileInputStream fileInputStream = new FileInputStream(file);
//             byte[] fileBytes = new byte[(int) file.length()];
//             fileInputStream.read(fileBytes);
//             fileInputStream.close();
    
//             // Convert file bytes to Base64 string
//             String fileBase64 = Base64.getEncoder().encodeToString(fileBytes);
    
//             // Modify file name to remove invalid characters
//             String sanitizedFileName = sanitizeFileName(fileName);
    
//             // Upload file to Firebase Realtime Database
//             databaseRef.child("files").child(sanitizedFileName).setValue(fileBase64, new DatabaseReference.CompletionListener() {
//                 @Override
//                 public void onComplete(DatabaseError error, DatabaseReference ref) {
//                     if (error == null) {
//                         System.out.println("File uploaded to Firebase with name: " + sanitizedFileName);
//                     } else {
//                         System.err.println("Failed to upload file to Firebase: " + error.getMessage());
//                     }
//                 }
//             });
//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }
    
//     private String sanitizeFileName(String fileName) {
//         // Replace invalid characters with valid ones
//         return fileName.replaceAll("[.#$\\[\\]]", "_");
//     }
    
// }

package utils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

public class DatabHandler {
    private static DatabaseReference databaseRef;

    // Initialize Firebase only once
    static {
        initializeFirebase();
    }

    private static void initializeFirebase() {
        try {
            FileInputStream serviceAccount = new FileInputStream("../serviceAccountKey.json");

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://lodelm-936f0-default-rtdb.firebaseio.com/")
                    .build();

            FirebaseApp.initializeApp(options);
            databaseRef = FirebaseDatabase.getInstance().getReference();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendFile(String filePath, String fileName) {
        String fileContents = readFileContents(filePath);

        // Generate a unique key for the upload
        String uploadKey = databaseRef.push().getKey();

        // Set the file contents in Firebase under the unique key
        databaseRef.child(uploadKey).setValue(fileContents, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError error, DatabaseReference ref) {
                if (error == null) {
                    System.out.println("File sent to Firebase with key: " + uploadKey);
                } else {
                    System.err.println("Failed to send Firebase: " + error.getMessage());
                }
            }
        });
    }

    // Method to read file contents
    private static String readFileContents(String filePath) {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentBuilder.toString();
    }
}
