import java.net.URL;
import java.net.HttpURLConnection;
import java.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProxyTest3 {
    public static void main(String[] args) {
        try {
            String proxyHost = System.getProperty("http.proxyHost");
            String proxyPort = System.getProperty("http.proxyPort");
            String proxyUser = System.getProperty("http.proxyUser");
            String proxyPassword = System.getProperty("http.proxyPassword");

            // Display proxy settings
            System.out.println("=== Java Proxy Settings ===");
            System.out.println("http.proxyHost: " + proxyHost);
            System.out.println("http.proxyPort: " + proxyPort);
            System.out.println("http.proxyUser: " + proxyUser);
            System.out.println("https.proxyHost: " + System.getProperty("https.proxyHost"));
            System.out.println();

            // Test connection to services.gradle.org
            System.out.println("=== Testing HTTPS Connection ===");
            URL url = new URL("https://services.gradle.org/");
            System.out.println("Connecting to: " + url);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Manually set Proxy-Authorization header
            if (proxyUser != null && proxyPassword != null) {
                String credentials = proxyUser + ":" + proxyPassword;
                String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes("UTF-8"));
                String authHeader = "Basic " + encodedCredentials;
                System.out.println("Setting Proxy-Authorization header");
                System.out.println("Credentials length: " + credentials.length());
                System.out.println("Encoded length: " + encodedCredentials.length());
                conn.setRequestProperty("Proxy-Authorization", authHeader);
            }

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            System.out.println("Response Message: " + conn.getResponseMessage());

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                int lineCount = 0;

                while ((inputLine = in.readLine()) != null && lineCount < 5) {
                    content.append(inputLine).append("\n");
                    lineCount++;
                }
                in.close();

                System.out.println("\nFirst 5 lines of response:");
                System.out.println(content.toString());
            }

            conn.disconnect();
            System.out.println("\nConnection successful!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
