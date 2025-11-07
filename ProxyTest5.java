import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ProxySelector;
import java.net.InetSocketAddress;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.time.Duration;

public class ProxyTest5 {
    public static void main(String[] args) {
        try {
            String proxyHost = System.getProperty("http.proxyHost");
            String proxyPort = System.getProperty("http.proxyPort");
            String proxyUser = System.getProperty("http.proxyUser");
            String proxyPassword = System.getProperty("http.proxyPassword");

            System.out.println("=== Using Java 11+ HttpClient ===");
            System.out.println("Proxy: " + proxyHost + ":" + proxyPort);
            System.out.println("User: " + proxyUser);
            System.out.println();

            // Create ProxySelector
            ProxySelector proxySelector = ProxySelector.of(
                new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort))
            );

            // Create Authenticator
            Authenticator authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    System.out.println("=== Authenticator Called (HttpClient) ===");
                    System.out.println("Requestor Type: " + getRequestorType());
                    System.out.println("Requesting Host: " + getRequestingHost());
                    System.out.println("Requesting Scheme: " + getRequestingScheme());

                    if (getRequestorType() == RequestorType.PROXY) {
                        System.out.println("Returning proxy credentials");
                        return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                    }
                    return null;
                }
            };

            // Create HttpClient with proxy and authenticator
            HttpClient client = HttpClient.newBuilder()
                .proxy(proxySelector)
                .authenticator(authenticator)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            // Create request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://services.gradle.org/"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            System.out.println("Sending request to: https://services.gradle.org/");

            // Send request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response Code: " + response.statusCode());
            System.out.println("Response Headers: " + response.headers().map());

            String body = response.body();
            String[] lines = body.split("\n");
            System.out.println("\nFirst 5 lines of response body:");
            for (int i = 0; i < Math.min(5, lines.length); i++) {
                System.out.println(lines[i]);
            }

            System.out.println("\nConnection successful!");

        } catch (Exception e) {
            System.err.println("\n=== Error Occurred ===");
            System.err.println("Error: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
