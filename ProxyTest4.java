import java.net.URL;
import java.net.HttpURLConnection;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProxyTest4 {
    public static void main(String[] args) {
        try {
            String proxyHost = System.getProperty("http.proxyHost");
            String proxyPort = System.getProperty("http.proxyPort");
            String proxyUser = System.getProperty("http.proxyUser");
            String proxyPassword = System.getProperty("http.proxyPassword");

            // Display all relevant settings
            System.out.println("=== All Proxy-Related Settings ===");
            System.out.println("http.proxyHost: " + proxyHost);
            System.out.println("http.proxyPort: " + proxyPort);
            System.out.println("http.proxyUser: " + proxyUser);
            System.out.println("https.proxyHost: " + System.getProperty("https.proxyHost"));
            System.out.println("https.proxyPort: " + System.getProperty("https.proxyPort"));
            System.out.println("https.proxyUser: " + System.getProperty("https.proxyUser"));
            System.out.println();

            // Display auth scheme settings
            System.out.println("=== Auth Scheme Settings ===");
            System.out.println("jdk.http.auth.proxying.disabledSchemes: [" +
                System.getProperty("jdk.http.auth.proxying.disabledSchemes") + "]");
            System.out.println("jdk.http.auth.tunneling.disabledSchemes: [" +
                System.getProperty("jdk.http.auth.tunneling.disabledSchemes") + "]");
            System.out.println();

            // Set up authenticator
            if (proxyUser != null && proxyPassword != null) {
                final String user = proxyUser;
                final String password = proxyPassword;

                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        System.out.println("=== Authenticator Called ===");
                        System.out.println("Requestor Type: " + getRequestorType());
                        System.out.println("Requesting Host: " + getRequestingHost());
                        System.out.println("Requesting Port: " + getRequestingPort());
                        System.out.println("Requesting Prompt: " + getRequestingPrompt());
                        System.out.println("Requesting Protocol: " + getRequestingProtocol());
                        System.out.println("Requesting Scheme: " + getRequestingScheme());

                        if (getRequestorType() == RequestorType.PROXY) {
                            System.out.println("Returning credentials for PROXY authentication");
                            return new PasswordAuthentication(user, password.toCharArray());
                        }
                        System.out.println("Not a PROXY request, returning null");
                        return null;
                    }
                });
                System.out.println("Authenticator configured\n");
            }

            // Test connection to services.gradle.org
            System.out.println("=== Testing HTTPS Connection ===");
            URL url = new URL("https://services.gradle.org/");
            System.out.println("Connecting to: " + url);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

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
            System.err.println("\n=== Error Occurred ===");
            System.err.println("Error: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
